package com.hdpwd.shared

import com.hdpwd.shared.security.BiometricAvailability
import com.hdpwd.shared.security.BiometricCapabilityService
import com.hdpwd.shared.security.UnavailableBiometricProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证生物识别不可用时隐藏入口并允许主密码回退。
 */
class BiometricCapabilityTest {
    /**
     * 不可用平台不应显示启用选项。
     */
    @Test
    fun unavailableProviderIsHidden() {
        assertFalse(BiometricCapabilityService.shouldOfferEnable(UnavailableBiometricProvider))
        assertTrue(BiometricCapabilityService.shouldFallbackToLocalPassword(Exception()))
        assertTrue(BiometricAvailability.UNAVAILABLE != BiometricAvailability.AVAILABLE)
    }
}
