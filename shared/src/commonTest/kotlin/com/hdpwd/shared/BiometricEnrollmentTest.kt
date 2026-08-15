package com.hdpwd.shared

import com.hdpwd.shared.security.BiometricAvailability
import com.hdpwd.shared.security.BiometricEnrollmentDecision
import com.hdpwd.shared.security.BiometricEnrollmentService
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证创建用户阶段生物识别取消后的主密码回退。
 */
class BiometricEnrollmentTest {
    /**
     * 用户取消或初始验证失败时必须使用本机主密码。
     */
    @Test
    fun cancellationFallsBackToPassword() {
        assertEquals(
            BiometricEnrollmentDecision.PASSWORD_ONLY,
            BiometricEnrollmentService.decide(BiometricAvailability.AVAILABLE, true, false),
        )
        assertEquals(
            BiometricEnrollmentDecision.PASSWORD_ONLY,
            BiometricEnrollmentService.decide(BiometricAvailability.UNAVAILABLE, true, true),
        )
    }
}
