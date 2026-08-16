package com.hdpwd.shared.application

/**
 * Web Page Visibility / beforeunload 的尽力生命周期钩子。
 *
 * 通过轮询 `document.visibilityState` 与脏标记桥接，避免 Kotlin/Wasm 复杂回调导出。
 */
class WebLifecycleSyncAdapter(
    private val coordinator: LifecycleSyncCoordinator,
    private val hasPendingChanges: () -> Boolean,
) {
    private var wasHidden = false

    /**
     * 安装 beforeunload，并按可见性变化触发同步协调。
     */
    suspend fun tick() {
        ensureBeforeUnloadInstalled()
        setJsDirtyFlag(hasPendingChanges())
        val hidden = documentVisibilityState() == "hidden"
        if (hidden && !wasHidden) {
            wasHidden = true
            coordinator.onBackground()
        } else if (!hidden && wasHidden) {
            wasHidden = false
            coordinator.onForeground()
        }
    }
}

@JsFun("(value) => { globalThis.__hdPwdDirtyFlag = !!value; }")
internal external fun setJsDirtyFlag(value: Boolean)

@JsFun(
    """
    () => {
      if (globalThis.__hdPwdLifecycleInstalled) return;
      globalThis.__hdPwdLifecycleInstalled = true;
      globalThis.addEventListener('beforeunload', (event) => {
        if (globalThis.__hdPwdDirtyFlag) {
          event.preventDefault();
          event.returnValue = '';
        }
      });
    }
    """,
)
internal external fun ensureBeforeUnloadInstalled()

@JsFun("() => (globalThis.document && globalThis.document.visibilityState) || 'visible'")
internal external fun documentVisibilityState(): String
