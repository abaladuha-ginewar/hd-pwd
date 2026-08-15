package com.hdpwd.shared.security

/**
 * 创建用户阶段的生物识别初始选择结果。
 */
enum class BiometricEnrollmentDecision {
    ENABLED,
    PASSWORD_ONLY,
}

/**
 * 处理初始验证取消和设备能力降级。
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
