package com.hdpwd.shared.security

/**
 * 设备锁初始设置或设置页中的生物识别选择结果。
 */
enum class BiometricEnrollmentDecision {
    ENABLED,
    PASSWORD_ONLY,
}

/**
 * 处理初始验证取消和设备能力降级；失败不得启用生物识别偏好。
 */
object BiometricEnrollmentService {
    /**
     * 只有用户主动选择且初始验证成功才启用。
     */
    fun decide(
        availability: BiometricAvailability,
        userRequested: Boolean,
        initialVerificationSucceeded: Boolean,
    ): BiometricEnrollmentDecision =
        if (
            userRequested &&
            availability == BiometricAvailability.AVAILABLE &&
            initialVerificationSucceeded
        ) {
            BiometricEnrollmentDecision.ENABLED
        } else {
            BiometricEnrollmentDecision.PASSWORD_ONLY
        }
}
