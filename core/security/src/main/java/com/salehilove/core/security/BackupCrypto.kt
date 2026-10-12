package com.salehilove.core.security

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted, portable backup container.
 *
 * The whole database export is serialised, GZIP-compressed and encrypted with
 * AES-256-GCM under a key derived from the user's backup password using
 * PBKDF2-HMAC-SHA256 (210k iterations, 16-byte salt, per OWASP 2023).
 *
 * File layout ("SLBK" = SalehiLove Backup):
 *   magic "SLBK" | version u8 | [kdfSalt:16] | [kdfIterations:4]
 *   | [iv:12] | [ciphertext+tag]
 *
 * All parameters travel inside the file, so a backup taken on one phone can be
 * restored on another with only the password.
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf(0x53, 0x4C, 0x42, 0x4B) // "SLBK"
    private const val VERSION: Byte = 1
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    const val DEFAULT_KDF_ITERATIONS = 210_000

    fun createContainer(payload: ByteArray, password: CharArray, iterations: Int = DEFAULT_KDF_ITERATIONS): ByteArray {
        require(payload.isNotEmpty()) { "empty payload" }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, iterations)
        val compressed = gzip(payload)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(compressed)
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION.toInt())
        out.write(salt)
        out.write(intToBytes(iterations))
        out.write(iv)
        out.write(encrypted)
        return out.toByteArray()
    }

    /** Verifies structure + password and returns the decrypted payload. */
    fun openContainer(container: ByteArray, password: CharArray): ByteArray {
        require(container.size > MAGIC.size + 1 + SALT_LENGTH + 4 + IV_LENGTH) {
            "فایل پشتیبان معتبر نیست"
        }
        require(container.copyOfRange(0, 4).contentEquals(MAGIC)) { "این فایل یک پشتیبان صالحی‌لاو نیست" }
        val version = container[4]
        require(version == VERSION) { "نسخه پشتیبان پشتیبانی نمی‌شود: $version" }
        var offset = 5
        val salt = container.copyOfRange(offset, offset + SALT_LENGTH); offset += SALT_LENGTH
        val iterations = bytesToInt(container, offset); offset += 4
        require(iterations in 10_000..5_000_000) { "پارامترهای پشتیبان نامعتبر است" }
        val iv = container.copyOfRange(offset, offset + IV_LENGTH); offset += IV_LENGTH
        val encrypted = container.copyOfRange(offset, container.size)
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val compressed = cipher.doFinal(encrypted) // throws AEADBadTagException on wrong password
        return gunzip(compressed)
    }

    fun isBackupFile(container: ByteArray): Boolean =
        container.size > 5 && container.copyOfRange(0, 4).contentEquals(MAGIC)

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }

    private fun intToBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
    )

    private fun bytesToInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)
}
