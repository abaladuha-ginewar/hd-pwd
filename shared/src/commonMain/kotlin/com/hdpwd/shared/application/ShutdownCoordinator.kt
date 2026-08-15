package com.hdpwd.shared.application

/**
 * 应用退出前的同步决策。
 */
enum class ShutdownDecision {
    CLOSE_NOW,
    WAIT_FOR_SYNC,
    CANCEL_CLOSE,
}

/**
 * Windows 可等待同步完成、移动端尽力同步的共享生命周期协调器。
 */
class ShutdownCoordinator(
    private val isWindows: Boolean,
    private val isDirty: () -> Boolean,
    private val awaitSync: suspend () -> Boolean,
) {
    /**
     * 根据平台能力执行关闭流程。
     */
    suspend fun requestClose(waitForSync: Boolean): ShutdownDecision {
        if (!isDirty()) return ShutdownDecision.CLOSE_NOW
        if (!isWindows || !waitForSync) return ShutdownDecision.CANCEL_CLOSE
        return if (awaitSync()) ShutdownDecision.CLOSE_NOW else ShutdownDecision.CANCEL_CLOSE
    }
}
