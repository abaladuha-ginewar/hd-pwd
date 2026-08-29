package com.hdpwd.shared

import com.hdpwd.shared.security.BiometricAvailability
import com.hdpwd.shared.security.Crypt32DpapiProtector
import com.hdpwd.shared.security.DesktopWindowsHelloDpapiProvider
import com.hdpwd.shared.security.DpapiProtector
import com.hdpwd.shared.security.WindowsHelloConsent
import com.hdpwd.shared.security.WinRtWindowsHelloConsent
import com.hdpwd.shared.security.isWindowsOs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Hello 闸门必须先于 DPAPI；未验证不得解封装。
 */
class DesktopWindowsHelloDpapiProviderTest {
    /**
     * 非 Windows 不得加载 Hello 为可用。
     */
    @Test
    fun helloUnavailableOffWindows() {
        if (isWindowsOs()) return
        assertEquals(
            BiometricAvailability.UNAVAILABLE,
            WinRtWindowsHelloConsent().availability(),
        )
    }

    /**
     * 未录入 Hello 时不得报告 AVAILABLE，设置页应隐藏开关。
     */
    @Test
    fun notEnrolledIsNotAvailable() {
        val provider = DesktopWindowsHelloDpapiProvider(
            consent = FakeWindowsHelloConsent(BiometricAvailability.NOT_ENROLLED),
            protector = RecordingDpapiProtector(),
        )
        assertEquals(BiometricAvailability.NOT_ENROLLED, provider.availability())
    }

    /**
     * Hello 取消或失败时不得调用 DPAPI 解封装。
     */
    @Test
    fun helloFailureDoesNotUnprotect() = runTest {
        val protector = RecordingDpapiProtector()
        val consent = FakeWindowsHelloConsent(
            availabilityValue = BiometricAvailability.AVAILABLE,
            failVerification = true,
        )
        val provider = DesktopWindowsHelloDpapiProvider(consent, protector)
        assertFails { provider.open("hdpwd.device-lock", byteArrayOf(1, 2, 3)) }
        assertEquals(0, protector.unprotectCalls)
        assertEquals(1, consent.verificationCalls)
    }

    /**
     * Hello 成功后才能封装并解封装同一把密钥。
     */
    @Test
    fun helloSuccessRoundTrip() = runTest {
        val protector = RecordingDpapiProtector()
        val consent = FakeWindowsHelloConsent(BiometricAvailability.AVAILABLE)
        val provider = DesktopWindowsHelloDpapiProvider(consent, protector)
        val lek = ByteArray(32) { (it + 3).toByte() }
        val sealed = provider.seal("hdpwd.device-lock", lek)
        assertTrue(sealed.isNotEmpty())
        assertEquals(1, protector.protectCalls)
        val opened = provider.open("hdpwd.device-lock", sealed)
        assertContentEquals(lek, opened)
        assertEquals(2, consent.verificationCalls)
    }

    /**
     * 真实 Crypt32 密文在 Hello 闸门通过后仍可往返（仅 Windows）。
     */
    @Test
    fun crypt32RoundTripAfterHelloOnWindows() = runTest {
        if (!isWindowsOs()) return@runTest
        val provider = DesktopWindowsHelloDpapiProvider(
            consent = FakeWindowsHelloConsent(BiometricAvailability.AVAILABLE),
            protector = Crypt32DpapiProtector(),
        )
        val lek = ByteArray(32) { (it + 9).toByte() }
        val sealed = provider.seal("hdpwd.device-lock", lek)
        val opened = provider.open("hdpwd.device-lock", sealed)
        assertContentEquals(lek, opened)
    }
}

/**
 * 可记录是否真正触碰 DPAPI 的测试替身。
 */
private class RecordingDpapiProtector : DpapiProtector {
    var protectCalls = 0
    var unprotectCalls = 0

    /**
     * 用简单前缀模拟封装，便于断言调用次数。
     */
    override fun protect(label: String, envelopeKey: ByteArray): ByteArray {
        protectCalls++
        return byteArrayOf(label.length.toByte()) + envelopeKey
    }

    /**
     * 去掉测试前缀还原 DeviceLEK。
     */
    override fun unprotect(label: String, sealedKey: ByteArray): ByteArray {
        unprotectCalls++
        return sealedKey.copyOfRange(1, sealedKey.size)
    }
}

/**
 * 不弹出系统 UI 的 Hello 替身。
 */
private class FakeWindowsHelloConsent(
    private val availabilityValue: BiometricAvailability,
    private val failVerification: Boolean = false,
) : WindowsHelloConsent {
    var verificationCalls = 0
        private set

    override fun availability(): BiometricAvailability = availabilityValue

    override suspend fun requestVerification(message: String) {
        verificationCalls++
        if (failVerification) {
            error("已取消 Windows Hello 验证")
        }
        require(message.isNotBlank())
    }
}
