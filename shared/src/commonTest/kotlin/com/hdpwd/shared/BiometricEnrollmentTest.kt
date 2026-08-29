package com.hdpwd.shared

import com.hdpwd.shared.security.BiometricAvailability
import com.hdpwd.shared.security.BiometricEnrollmentDecision
import com.hdpwd.shared.security.BiometricEnrollmentService
import com.hdpwd.shared.security.DeviceUnlockPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证设备锁生物识别初始选择与默认验证方式。
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
        assertEquals(
            BiometricEnrollmentDecision.ENABLED,
            BiometricEnrollmentService.decide(BiometricAvailability.AVAILABLE, true, true),
        )
    }

    /**
     * 只有偏好开启、能力可用且仍有封装时才自动拉起生物识别。
     */
    @Test
    fun autoPromptFollowsPreferenceAndCapability() {
        assertTrue(
            DeviceUnlockPreference.shouldAutoPromptBiometric(
                preferBiometric = true,
                availability = BiometricAvailability.AVAILABLE,
                hasSealedBlob = true,
            ),
        )
        assertFalse(
            DeviceUnlockPreference.shouldAutoPromptBiometric(
                preferBiometric = true,
                availability = BiometricAvailability.AVAILABLE,
                hasSealedBlob = false,
            ),
        )
        assertFalse(
            DeviceUnlockPreference.shouldAutoPromptBiometric(
                preferBiometric = false,
                availability = BiometricAvailability.AVAILABLE,
                hasSealedBlob = true,
            ),
        )
        assertFalse(
            DeviceUnlockPreference.shouldAutoPromptBiometric(
                preferBiometric = true,
                availability = BiometricAvailability.NOT_ENROLLED,
                hasSealedBlob = true,
            ),
        )
    }
}
