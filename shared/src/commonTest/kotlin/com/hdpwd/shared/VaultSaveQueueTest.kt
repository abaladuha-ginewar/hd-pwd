package com.hdpwd.shared

import com.hdpwd.shared.storage.VaultSaveQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证本地保存队列的 dirty 状态和失败保留语义。
 */
class VaultSaveQueueTest {
    /**
     * 保存成功后应清除 dirty 状态。
     */
    @Test
    fun successfulSaveClearsDirty() = runTest {
        val completed = CompletableDeferred<Unit>()
        val queue = VaultSaveQueue(CoroutineScope(Dispatchers.Default)) {
            completed.complete(Unit)
        }
        queue.schedule(byteArrayOf(1, 2))
        withTimeout(2_000) { completed.await() }
        withTimeout(2_000) {
            while (queue.isDirty()) kotlinx.coroutines.yield()
        }
        assertFalse(queue.isDirty())
    }

    /**
     * 保存失败后必须保留 dirty 状态供下次启动恢复。
     */
    @Test
    fun failedSaveKeepsDirty() = runTest {
        val queue = VaultSaveQueue(CoroutineScope(Dispatchers.Default)) {
            error("test failure")
        }
        queue.schedule(byteArrayOf(1))
        withTimeout(2_000) {
            while (!queue.isDirty()) kotlinx.coroutines.yield()
        }
        assertTrue(queue.isDirty())
    }
}
