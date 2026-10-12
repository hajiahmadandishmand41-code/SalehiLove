package com.salehilove.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN hashing for the private diary lock. PINs are never stored — only a
 * salted PBKDF2-HMAC-SHA256 hash with a per-install random salt.
 *
 * Wire format: [iterations:4][salt:16][hash:32]
 */
object PinManager {

    private const val SALT_LENGTH = 16
    private const val HASH_LENGTH = 32
    const val DEFAULT_ITERATIONS = 120_000

    fun hashPin(pin: String, iterations: Int = DEFAULT_ITERATIONS, salt: ByteArray? = null): ByteArray {
        require(pin.length in 4..8) { "PIN must be 4 to 8 digits" }
        require(pin.all { it in '0'..'9' }) { "PIN must contain digits only" }
        val realSalt = salt ?: ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, realSalt, iterations)
        return ByteArray(4 + SALT_LENGTH + HASH_LENGTH).also {
            writeInt(it, 0, iterations)
            realSalt.copyInto(it, 4)
            hash.copyInto(it, 4 + SALT_LENGTH)
        }
    }

    fun verifyPin(pin: String, stored: ByteArray): Boolean {
        if (stored.size != 4 + SALT_LENGTH + HASH_LENGTH) return false
        val iterations = readInt(stored, 0)
        val salt = stored.copyOfRange(4, 4 + SALT_LENGTH)
        val expected = stored.copyOfRange(4 + SALT_LENGTH, stored.size)
        val actual = try {
            pbkdf2(pin, salt, iterations)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_LENGTH * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)
}
