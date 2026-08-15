package com.hdpwd.shared

import com.hdpwd.shared.application.ShutdownCoordinator
import com.hdpwd.shared.application.ShutdownDecision
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 Windows 等待同步和移动端平台限制。
 */
class ShutdownCoordinatorTest {
    /**
     * Windows dirty 状态在等待同步成功后允许关闭。
     */
    @Test
    fun windowsWaitsForSync() = runTest {
        val result = ShutdownCoordinator(true, { true }, { true }).requestClose(waitForSync = true)
        assertEquals(ShutdownDecision.CLOSE_NOW, result)
    }

    /**
     * 非 Windows 平台不能承诺拦截关闭。
     */
    @Test
    fun mobileDoesNotClaimCloseGuarantee() = runTest {
        val result = ShutdownCoordinator(false, { true }, { true }).requestClose(waitForSync = true)
        assertEquals(ShutdownDecision.CANCEL_CLOSE, result)
    }
}
