package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.sync.syncUiStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证同步失败和冲突的红点状态。
 */
class SyncUiStatusTest {
    /**
     * 任意失败目标都应触发设置红点。
     */
    @Test
    fun failedTargetNeedsAttention() {
        val target = SyncTarget(
            EntityId("target"),
            "s3",
            "https://example.test",
            "bucket",
            "region",
            status = SyncStatus.FAILED,
        )
        val status = syncUiStatus(listOf(target), emptyList())
        assertTrue(status.hasAttention)
        assertEquals(1, status.failedTargetCount)
    }
}
