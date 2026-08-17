package com.hdpwd.shared.security

import android.os.Build
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android Strong Biometric + Keystore 的 DeviceLEK 封装实现。
 */
class AndroidBiometricProvider(
    private val activity: FragmentActivity,
) : BiometricProvider {
    /**
     * 查询 Android Strong Biometric 是否可用。
     */
    override fun availability(): BiometricAvailability =
        when (
            BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        ) {
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
    override suspend fun seal(label: String, envelopeKey: ByteArray): ByteArray =
        withContext(Dispatchers.Main.immediate) {
            ensureStrongBiometricAvailable()
            deleteKeyIfExists(label)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, createKey(label))
            val authenticatedCipher = authenticate(cipher)
            authenticatedCipher.iv + authenticatedCipher.doFinal(envelopeKey)
        }

    /**
     * 通过生物识别认证后解封装 LEK。
     */
    override suspend fun open(label: String, sealedKey: ByteArray): ByteArray =
        withContext(Dispatchers.Main.immediate) {
            ensureStrongBiometricAvailable()
            require(sealedKey.size > GCM_IV_BYTES) { "生物识别封装数据长度无效" }
            val iv = sealedKey.copyOfRange(0, GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                requireKey(label),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            authenticate(cipher).doFinal(sealedKey, GCM_IV_BYTES, sealedKey.size - GCM_IV_BYTES)
        }

    /**
     * 删除 Android Keystore 中的本机生物识别密钥。
     */
    override suspend fun delete(label: String) {
        withContext(Dispatchers.IO) {
            deleteKeyIfExists(label)
        }
    }

    private fun ensureStrongBiometricAvailable() {
        val status = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        require(status == BiometricManager.BIOMETRIC_SUCCESS) {
            when (status) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    "设备尚未录入指纹/面容，请先在系统设置中添加"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                -> "当前设备不支持强生物识别"
                else -> "生物识别权限或能力不可用（status=$status）"
            }
        }
    }

    private suspend fun authenticate(cipher: Cipher): Cipher =
        suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authenticatedCipher = result.cryptoObject?.cipher
                        if (authenticatedCipher == null) {
                            continuation.resumeWithException(
                                IllegalStateException("生物识别未返回密码学对象"),
                            )
                        } else {
                            continuation.resume(authenticatedCipher)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        continuation.resumeWithException(
                            IllegalStateException("生物识别验证失败: $errString"),
                        )
                    }

                    override fun onAuthenticationFailed() {
                        // 单次失败仍可重试，不结束协程。
                    }
                },
            )
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("验证身份")
                .setSubtitle("使用生物识别保护本机密钥")
                .setNegativeButtonText("取消")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        }

    private fun createKey(label: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias(label),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(0)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun requireKey(label: String): SecretKey {
        val existing = loadKeyStore().getKey(alias(label), null)
        require(existing is SecretKey) { "生物识别密钥不存在，请重新启用" }
        return existing
    }

    private fun deleteKeyIfExists(label: String) {
        val store = loadKeyStore()
        val name = alias(label)
        if (store.containsAlias(name)) {
            store.deleteEntry(name)
        }
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }

    private fun alias(label: String): String =
        "hdpwd.biometric.${label.hashCode().toUInt().toString(16)}"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
