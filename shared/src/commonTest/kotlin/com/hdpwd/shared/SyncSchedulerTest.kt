package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.sync.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证同步静默期、代次取消和本地保存前置条件。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncSchedulerTest {
    private fun sampleTarget() = SyncTarget(
        id = EntityId("target"),
        provider = "s3",
        endpoint = "https://example.test",
        bucket = "bucket",
        region = "us-east-1",
        enabled = true,
        confirmed = true,
    )

    /**
     * 只有最后一次调度且本地保存成功时才启动同步。
     */
    @Test
    fun schedulesAfterLocalSave() = runTest {
        val completed = CompletableDeferred<EntityId>()
        val target = sampleTarget()
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

    /**
     * 静默期内再次 schedule 会重新计时，不会提前触发同步。
     */
    @Test
    fun rescheduleResetsQuietPeriod() = runTest {
        var syncCount = 0
        val target = sampleTarget()
        val scheduler = SyncScheduler(
            scope = this,
            sync = { syncCount++ },
            localSaveCompleted = { true },
            quietPeriodMillis = 5_000L,
        )
        scheduler.schedule(listOf(target))
        assertTrue(scheduler.hasPendingJobs())
        advanceTimeBy(4_000)
        assertEquals(0, syncCount)
        scheduler.schedule(listOf(target))
        advanceTimeBy(4_000)
        assertEquals(0, syncCount)
        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertEquals(1, syncCount)
        assertFalse(scheduler.hasPendingJobs())
        scheduler.cancel()
    }

    /**
     * 静默期结束后会等待本地保存完成再同步。
     */
    @Test
    fun waitsForLocalSaveBeforeSync() = runTest {
        var ready = false
        var syncCount = 0
        val target = sampleTarget()
        val scheduler = SyncScheduler(
            scope = this,
            sync = { syncCount++ },
            localSaveCompleted = { ready },
            quietPeriodMillis = 100,
        )
        scheduler.schedule(listOf(target))
        advanceTimeBy(100)
        assertEquals(0, syncCount)
        ready = true
        advanceUntilIdle()
        assertEquals(1, syncCount)
        scheduler.cancel()
    }
}
