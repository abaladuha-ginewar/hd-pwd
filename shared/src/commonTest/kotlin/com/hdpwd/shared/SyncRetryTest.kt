package com.hdpwd.shared

import com.hdpwd.shared.sync.RetryPolicy
import com.hdpwd.shared.sync.SyncRetryExecutor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证同步重试次数和成功收敛。
 */
class SyncRetryTest {
    /**
     * 临时失败应按尝试次数重试后成功。
     */
    @Test
    fun retriesTransientFailure() = runTest {
        var attempts = 0
        val result = SyncRetryExecutor { 0 }.execute(
            RetryPolicy(maxAttempts = 3, initialDelayMillis = 1, maxDelayMillis = 2),
        ) {
            attempts++
            if (attempts < 3) error("temporary")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }
}
