package com.hdpwd.shared

import com.hdpwd.shared.application.DirtyStateStore
import com.hdpwd.shared.application.LifecycleSyncCoordinator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证后台 dirty 标记和下次启动继续同步。
 */
class LifecycleSyncCoordinatorTest {
    /**
     * dirty 状态应被持久化并触发同步回调。
     */
    @Test
    fun persistsDirtyAndResumesSync() = runTest {
        val store = MemoryDirtyStore()
        var syncCount = 0
        val coordinator = LifecycleSyncCoordinator(
            dirtyStore = store,
            userId = "user",
            hasPendingChanges = { true },
            continueSync = { syncCount++ },
        )
        coordinator.onBackground()
        coordinator.onForeground()
        assertTrue(store.dirty)
        assertTrue(syncCount >= 2)
    }
}

/**
 * 生命周期测试用 dirty 存储。
 */
private class MemoryDirtyStore : DirtyStateStore {
    var dirty = false
    override suspend fun setDirty(userId: String, dirty: Boolean) {
        this.dirty = dirty
    }
    override suspend fun isDirty(userId: String): Boolean = dirty
}
