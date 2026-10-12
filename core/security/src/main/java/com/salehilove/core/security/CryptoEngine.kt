package com.salehilove.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Pure-JVM AES-256-GCM engine. Kept free of any Android dependency so that it
 * is fully unit-testable on the JVM. Keys are supplied by a [KeyProvider]
 * (Android Keystore on device, raw keys in tests).
 *
 * Wire format: [1 byte version][12 bytes IV][ciphertext+tag]
 */
class CryptoEngine(private val keyProvider: KeyProvider) {

    companion object {
        const val VERSION: Byte = 1
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val key: SecretKey = keyProvider.getOrCreateKey()
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain)
        return ByteArray(1 + IV_LENGTH + encrypted.size).apply {
            this[0] = VERSION
            iv.copyInto(this, 1)
            encrypted.copyInto(this, 1 + IV_LENGTH)
        }
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 1 + IV_LENGTH) { "ciphertext too short" }
        val version = blob[0]
        require(version == VERSION) { "unsupported crypto container version $version" }
        val iv = blob.copyOfRange(1, 1 + IV_LENGTH)
        val encrypted = blob.copyOfRange(1 + IV_LENGTH, blob.size)
        val key: SecretKey = keyProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }
}

interface KeyProvider {
    /** Returns the symmetric key, creating it on first use when possible. */
    fun getOrCreateKey(): SecretKey
}
