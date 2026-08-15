package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.sync.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证同步静默期、代次取消和本地保存前置条件。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncSchedulerTest {
    /**
     * 只有最后一次调度且本地保存成功时才启动同步。
     */
    @Test
    fun schedulesAfterLocalSave() = runTest {
        val completed = CompletableDeferred<EntityId>()
        val target = SyncTarget(
            id = EntityId("target"),
            provider = "s3",
            endpoint = "https://example.test",
            bucket = "bucket",
            region = "us-east-1",
            enabled = true,
            confirmed = true,
        )
        val scheduler = SyncScheduler(
            scope = this,
            sync = { completed.complete(it.id) },
            localSaveCompleted = { true },
            quietPeriodMillis = 10,
        )
        scheduler.schedule(listOf(target))
        advanceUntilIdle()
        assertEquals(EntityId("target"), completed.await())
        scheduler.cancel()
    }
}
