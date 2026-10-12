package com.salehilove.feature.backup

import android.content.Context
import android.net.Uri
import com.salehilove.core.common.AppResult
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.backup.BackupCodec
import com.salehilove.core.database.repo.DiaryRepository
import com.salehilove.core.security.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * End-to-end encrypted backup / restore:
 *  1. Full DB → JSON ([BackupCodec]); diary bodies are decrypted by the
 *     Keystore vault for the container only.
 *  2. JSON → AES-GCM container sealed with a password-derived key
 *     ([BackupCrypto] — PBKDF2-SHA256 + AES-256-GCM, salt + IV in header).
 *  3. Container → user-chosen file via SAF. Restore is the inverse; the
 *     whole import runs in one DB transaction, so a bad file never
 *     corrupts existing data.
 *
 * The container is portable: it never depends on this device's Keystore.
 */
class BackupManager(
    private val context: Context,
    private val db: RawDatabase,
    private val diaryRepo: DiaryRepository,
    private val photoStore: PhotoStoreAccess,
) {

    interface PhotoStoreAccess {
        fun read(path: String): ByteArray?
        fun write(path: String, bytes: ByteArray)
        fun photosDir(): File
    }

    private val codec = BackupCodec(object : BackupCodec.PhotoFileAccess {
        override fun read(path: String): ByteArray? = photoStore.read(path)
        override fun write(path: String, bytes: ByteArray) = photoStore.write(path, bytes)
    })

    suspend fun exportTo(uri: Uri, password: CharArray): AppResult<Long> =
        withContext(Dispatchers.IO) {
            try {
                val json = codec.export(db) { entry -> diaryRepo.decryptText(entry.contentEnc) }
                val container = BackupCrypto.createContainer(json.toByteArray(Charsets.UTF_8), password)
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(container)
                } ?: return@withContext AppResult.Failure(IllegalStateException("نمی‌توان فایل را نوشت"))
                AppResult.Success(container.size.toLong())
            } catch (t: Throwable) {
                AppResult.Failure(t)
            }
        }

    suspend fun restoreFrom(uri: Uri, password: CharArray): AppResult<BackupCodec.ImportResult> =
        withContext(Dispatchers.IO) {
            try {
                val container = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext AppResult.Failure(IllegalStateException("نمی‌توان فایل را خواند"))
                if (!BackupCrypto.isBackupFile(container)) {
                    return@withContext AppResult.Failure(IllegalArgumentException("این فایل پشتیبان صالحی‌لاو نیست"))
                }
                val json = BackupCrypto.openContainer(container, password).toString(Charsets.UTF_8)
                val result = codec.import(db, json) { plain -> diaryRepo.encryptText(plain) }
                AppResult.Success(result)
            } catch (t: Throwable) {
                AppResult.Failure(t)
            }
        }
}
