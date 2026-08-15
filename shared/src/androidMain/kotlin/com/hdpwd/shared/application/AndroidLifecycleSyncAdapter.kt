package com.hdpwd.shared.application

/**
 * Android Activity/Process 生命周期回调的轻量适配器。
 */
class AndroidLifecycleSyncAdapter(
    private val coordinator: LifecycleSyncCoordinator,
) {
    /**
     * Activity 进入后台时持久化 dirty 状态并尽力同步。
     */
    suspend fun onStop() = coordinator.onBackground()

    /**
     * Activity 回到前台时恢复上次未完成同步。
     */
    suspend fun onStart() = coordinator.onForeground()
}
