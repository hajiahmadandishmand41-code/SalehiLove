package com.salehilove.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private class FixedKeyProvider : KeyProvider {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun getOrCreateKey(): SecretKey = key
}

class CryptoEngineTest {

    private val engine = CryptoEngine(FixedKeyProvider())

    @Test
    fun `encrypt-decrypt round trip`() {
        val plain = "سلام دنیا — private diary entry ❤".toByteArray(Charsets.UTF_8)
        val blob = engine.encrypt(plain)
        assertArrayEquals(plain, engine.decrypt(blob))
    }

    @Test
    fun `ciphertext differs from plaintext and is non-deterministic`() {
        val plain = "same message".toByteArray()
        val a = engine.encrypt(plain)
        val b = engine.encrypt(plain)
        assertFalse(a.contentEquals(plain))
        // Fresh IV each time -> different ciphertexts
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val blob = engine.encrypt("secret".toByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        try {
            engine.decrypt(blob)
            error("expected AEADBadTagException")
        } catch (expected: Exception) {
            assertTrue(expected is javax.crypto.AEADBadTagException || expected.cause is javax.crypto.AEADBadTagException)
        }
    }

    @Test
    fun `truncated blob is rejected`() {
        val blob = engine.encrypt("secret".toByteArray())
        try {
            engine.decrypt(blob.copyOf(5))
            error("expected failure")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}

class PinManagerTest {

    @Test
    fun `hash and verify`() {
        val stored = PinManager.hashPin("1375")
        assertTrue(PinManager.verifyPin("1375", stored))
    }

    @Test
    fun `wrong pin rejected`() {
        val stored = PinManager.hashPin("1375")
        assertFalse(PinManager.verifyPin("1376", stored))
        assertFalse(PinManager.verifyPin("", stored))
    }

    @Test
    fun `hashes are salted - two hashes of same pin differ`() {
        val a = PinManager.hashPin("1375")
        val b = PinManager.hashPin("1375")
        assertNotEquals(a.contentHashCode().toLong(), b.contentHashCode().toLong())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `invalid pins rejected`() {
        listOf("", "12", "123", "abcd", "12 34", "123456789").forEach { pin ->
            try {
                PinManager.hashPin(pin)
                error("expected rejection for $pin")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }
}

class BackupCryptoTest {

    private val payload = ByteArray(50_000) { SecureRandom().nextInt(255).toByte() }

    @Test
    fun `container round trip`() {
        val container = BackupCrypto.createContainer(payload, "pass1234".toCharArray())
        assertTrue(BackupCrypto.isBackupFile(container))
        val restored = BackupCrypto.openContainer(container, "pass1234".toCharArray())
        assertArrayEquals(payload, restored)
    }

    @Test
    fun `compression actually shrinks the result`() {
        val repeatable = ByteArray(100_000) { 'A'.toByte() }
        val container = BackupCrypto.createContainer(repeatable, "pw".toCharArray())
        assertTrue("container should be much smaller than payload", container.size < 20_000)
    }

    @Test
    fun `wrong password rejected without corrupting`() {
        val container = BackupCrypto.createContainer(payload, "correct".toCharArray())
        try {
            BackupCrypto.openContainer(container, "wrong".toCharArray())
            error("expected AEADBadTagException")
        } catch (expected: Exception) {
            // GCM tag mismatch - good
        }
    }

    @Test
    fun `non-backup file rejected by magic check`() {
        assertFalse(BackupCrypto.isBackupFile("hello world not a backup!!".toByteArray()))
        try {
            BackupCrypto.openContainer("junk".toByteArray(), "x".toCharArray())
            error("expected failure")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `tampered body fails authentication`() {
        val container = BackupCrypto.createContainer(payload, "pw".toCharArray())
        container[container.size - 2] = (container[container.size - 2].toInt() xor 0x7F).toByte()
        try {
            BackupCrypto.openContainer(container, "pw".toCharArray())
            error("expected failure")
        } catch (expected: Exception) {
            // expected
        }
    }
}
