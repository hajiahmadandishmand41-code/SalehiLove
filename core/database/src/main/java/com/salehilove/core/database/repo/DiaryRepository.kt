package com.salehilove.core.database.repo

import com.salehilove.core.database.DiaryEntry
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.dao.DiaryDao
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Diary facade: bodies are encrypted by the caller-supplied vault (Android
 * Keystore AES-GCM on device) before they ever reach the DAO. The repository
 * itself never sees plaintext persistence — only ciphertext in/out.
 */
class DiaryRepository(
    db: RawDatabase,
    private val encrypt: (String) -> ByteArray,
    private val decrypt: (ByteArray) -> String,
) {
    private val dao = DiaryDao(db)

    fun saveNew(
        title: String,
        content: String,
        mood: String,
        entryDate: LocalDate,
        photoPaths: List<String> = emptyList(),
    ): Long {
        require(content.isNotBlank()) { "متن خاطره نمی‌تواند خالی باشد" }
        val now = LocalDateTime.now()
        return dao.insert(
            DiaryEntry(
                title = title.trim(),
                contentEnc = encrypt(content),
                mood = mood,
                entryDate = entryDate,
                photoPaths = photoPaths,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    fun saveUpdate(
        id: Long,
        title: String,
        content: String,
        mood: String,
        entryDate: LocalDate,
        photoPaths: List<String>,
    ): Boolean {
        require(id != 0L) { "شناسه خاطره نامعتبر است" }
        require(content.isNotBlank()) { "متن خاطره نمی‌تواند خالی باشد" }
        val existing = dao.byId(id) ?: return false
        return dao.update(
            existing.copy(
                title = title.trim(),
                contentEnc = encrypt(content),
                mood = mood,
                entryDate = entryDate,
                photoPaths = photoPaths,
                updatedAt = LocalDateTime.now(),
            ),
        ) > 0
    }

    fun delete(id: Long) = dao.delete(id)

    /** Decrypted entry: body is decrypted in memory for the UI only. */
    data class DecryptedEntry(
        val id: Long,
        val title: String,
        val content: String,
        val mood: String,
        val entryDate: LocalDate,
        val photoPaths: List<String>,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    )

    fun all(): List<DiaryEntry> = dao.all()

    fun count(): Int = dao.count()

    fun latest(limit: Int): List<DiaryEntry> = dao.latest(limit)

    fun decrypt(entry: DiaryEntry): DecryptedEntry = DecryptedEntry(
        id = entry.id,
        title = entry.title,
        content = decrypt(entry.contentEnc),
        mood = entry.mood,
        entryDate = entry.entryDate,
        photoPaths = entry.photoPaths,
        createdAt = entry.createdAt,
        updatedAt = entry.updatedAt,
    )

    fun byIdDecrypted(id: Long): DecryptedEntry? = dao.byId(id)?.let(::decrypt)

    fun searchTitles(query: String): List<DiaryEntry> = dao.searchTitles(query.trim())

    /** Vault access for the backup container (portable, re-encrypted on import). */
    fun encryptText(plainText: String): ByteArray = encrypt(plainText)

    fun decryptText(blob: ByteArray): String = decrypt(blob)
}
