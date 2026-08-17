package com.hdpwd.shared.security

/**
 * 将平台生物识别能力转换为 UI 是否显示启用选项。
 */
object BiometricCapabilityService {
    /**
     * 只有可稳定封装 DeviceLEK 时才显示启用选项。
     */
    fun shouldOfferEnable(provider: BiometricProvider): Boolean =
        provider.availability() == BiometricAvailability.AVAILABLE

    /**
     * 生物识别失败时统一回退到本机主密码。
     */
    fun shouldFallbackToLocalPassword(error: Throwable): Boolean = true
}
