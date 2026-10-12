package com.salehilove.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Provides an AES-256 key that never leaves the Android Keystore.
 * The key is generated on first use with STRONG_INTEGRITY purposes bound to
 * encryption/decryption only. Deleting the app's data destroys the key (and
 * therefore the diary remains unreadable), which is exactly what a private
 * diary needs.
 */
class KeystoreKeyProvider(
    private val alias: String = DEFAULT_ALIAS,
) : KeyProvider {

    companion object {
        const val DEFAULT_ALIAS = "salehilove_diary_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            spec.setUnlockedDeviceRequired(false)
        }
        generator.init(spec.build())
        return generator.generateKey()
    }
}
