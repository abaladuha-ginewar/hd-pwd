package com.hdpwd.shared.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android Strong Biometric + Keystore 的 LEK 封装实现。
 */
class AndroidBiometricProvider(
    private val activity: FragmentActivity,
    private val context: Context = activity,
) : BiometricProvider {
    /**
     * 查询 Android Strong Biometric 是否可用。
     */
    override fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> BiometricAvailability.UNAVAILABLE
            else -> BiometricAvailability.PERMISSION_DENIED
        }

    /**
     * 生成或复用 Keystore AES 密钥并通过生物识别认证后封装 LEK。
     */
    override suspend fun seal(label: String, envelopeKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(label))
        val authenticatedCipher = authenticate(label, cipher)
        return authenticatedCipher.iv + authenticatedCipher.doFinal(envelopeKey)
    }

    /**
     * 通过生物识别认证后解封装 LEK。
     */
    override suspend fun open(label: String, sealedKey: ByteArray): ByteArray {
        require(sealedKey.size > GCM_IV_BYTES) { "生物识别封装数据长度无效" }
        val iv = sealedKey.copyOfRange(0, GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(label),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return authenticate(label, cipher).doFinal(sealedKey, GCM_IV_BYTES, sealedKey.size - GCM_IV_BYTES)
    }

    /**
     * 删除 Android Keystore 中的本机生物识别密钥。
     */
    override suspend fun delete(label: String) {
        loadKeyStore().deleteEntry(alias(label))
    }

    private suspend fun authenticate(label: String, cipher: Cipher): Cipher =
        suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authenticatedCipher = result.cryptoObject?.cipher
                        if (authenticatedCipher == null) {
                            continuation.resumeWithException(IllegalStateException("生物识别未返回密码学对象"))
                        } else {
                            continuation.resume(authenticatedCipher)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        continuation.resumeWithException(
                            IllegalStateException("生物识别验证失败: $errorCode"),
                        )
                    }
                },
            )
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("验证身份")
                    .setSubtitle("使用生物识别解锁密码库")
                    .setNegativeButtonText("使用本机主密码")
                    .build(),
                BiometricPrompt.CryptoObject(cipher),
            )
        }

    private fun getOrCreateKey(label: String): SecretKey {
        val store = loadKeyStore()
        val existing = store.getKey(alias(label), null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias(label),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(300)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }

    private fun alias(label: String): String = "hdpwd.biometric.${label.hashCode().toUInt().toString(16)}"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
