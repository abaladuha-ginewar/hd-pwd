package com.hdpwd.shared

import com.hdpwd.shared.security.BiometricAvailability
import com.hdpwd.shared.security.DesktopWindowsDpapiProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Windows DPAPI 封装往返；非 Windows 仅断言能力不可用。
 */
class DesktopWindowsDpapiProviderTest {
    @Test
    fun availabilityMatchesOperatingSystem() {
        val provider = DesktopWindowsDpapiProvider()
        val windows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")
        assertEquals(
            if (windows) BiometricAvailability.AVAILABLE else BiometricAvailability.UNAVAILABLE,
            provider.availability(),
        )
    }

    @Test
    fun sealOpenRoundTripOnWindows() = runTest {
        val provider = DesktopWindowsDpapiProvider()
        if (provider.availability() != BiometricAvailability.AVAILABLE) return@runTest
        val lek = ByteArray(32) { (it + 3).toByte() }
        val sealed = provider.seal("user-1", lek)
        assertTrue(sealed.isNotEmpty())
        val opened = provider.open("user-1", sealed)
        assertContentEquals(lek, opened)
    }
}
