package com.salehilove.core.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/**
 * Gate for the private diary: PIN (PBKDF2-verified) and, when the device has
 * enrolled biometrics, fingerprint / face unlock.
 *
 * On API 28+ the platform [BiometricPrompt] (Builder API) is used directly —
 * no extra dependencies; on API 26/27 the legacy [FingerprintManager] is the
 * fallback. On neither path does any biometric data touch our code.
 */
class DiaryVault(
    private val cryptoEngine: CryptoEngine,
) {
    fun encryptEntry(plainText: String): ByteArray = cryptoEngine.encrypt(plainText.toByteArray(Charsets.UTF_8))

    fun decryptEntry(blob: ByteArray): String = cryptoEngine.decrypt(blob).toString(Charsets.UTF_8)
}

class BiometricHelper(private val context: Context) {

    fun isBiometricAvailable(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> {
            val bm = context.getSystemService(BiometricManager::class.java)
            bm != null && bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }
        Build.VERSION.SDK_INT >= 29 -> {
            val bm = context.getSystemService(BiometricManager::class.java)
            bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
        else -> {
            @Suppress("DEPRECATION")
            val fm = context.getSystemService(FingerprintManager::class.java)
            fm?.isHardwareDetected == true && fm.hasEnrolledFingerprints()
        }
    }

    /**
     * Launches the system biometric prompt. Calls exactly one of the callbacks.
     * [activityContext] must be the currently visible Activity.
     */
    fun authenticate(
        activityContext: Context,
        title: String,
        subtitle: String,
        negativeLabel: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val executor = Executor { runnable -> mainHandler.post(runnable) }
        val signal = CancellationSignal()

        if (Build.VERSION.SDK_INT >= 28) {
            val prompt = BiometricPrompt.Builder(activityContext)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton(negativeLabel, executor) { _, _ -> onError(negativeLabel) }
                .build()
            prompt.authenticate(signal, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            })
        } else {
            @Suppress("DEPRECATION")
            val fm = context.getSystemService(FingerprintManager::class.java)
            if (fm == null || !fm.isHardwareDetected || !fm.hasEnrolledFingerprints()) {
                onError("اثر انگشت در دسترس نیست")
                return
            }
            @Suppress("DEPRECATION")
            fm.authenticate(null, signal, 0, object : FingerprintManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onError(errString?.toString() ?: "خطای احراز هویت")
                }
            }, null)
        }
    }
}
