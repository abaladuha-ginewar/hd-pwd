package com.hdpwd.shared.application

/**
 * 平台生命周期可持久化的 dirty 标记接口。
 */
interface DirtyStateStore {
    /**
     * 写入当前用户待同步状态。
     */
    suspend fun setDirty(userId: String, dirty: Boolean)

    /**
     * 读取上次进程退出时的待同步状态。
     */
    suspend fun isDirty(userId: String): Boolean
}

/**
 * Android/Web 生命周期使用的尽力同步协调器。
 */
class LifecycleSyncCoordinator(
    private val dirtyStore: DirtyStateStore,
    private val userId: String,
    private val hasPendingChanges: () -> Boolean,
    private val continueSync: suspend () -> Unit,
) {
    /**
     * 进入后台时立即保存 dirty 状态并启动允许的同步。
     */
    suspend fun onBackground() {
        val dirty = hasPendingChanges()
        dirtyStore.setDirty(userId, dirty)
        if (dirty) runCatching { continueSync() }
    }

    /**
     * 启动或恢复前检查上次未完成同步。
     */
    suspend fun onForeground() {
        if (dirtyStore.isDirty(userId)) {
            runCatching { continueSync() }
        }
    }
}
