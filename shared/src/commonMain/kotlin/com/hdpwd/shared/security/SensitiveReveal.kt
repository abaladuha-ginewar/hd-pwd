package com.hdpwd.shared.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 按需显示子密码并在一分钟后自动隐藏的状态控制器。
 */
class SensitiveRevealController(
    private val scope: CoroutineScope,
    private val hideAfterMillis: Long = 60_000L,
) {
    private var hideJob: Job? = null
    private var current: String? = null

    /**
     * 临时显示生成后的子密码。
     */
    fun reveal(value: String) {
        hideJob?.cancel()
        current = value
        hideJob = scope.launch {
            delay(hideAfterMillis)
            hide()
        }
    }

    /**
     * 返回当前明文；未显示时返回 null。
     */
    fun current(): String? = current

    /**
     * 立即隐藏并清理当前引用。
     */
    fun hide() {
        current = null
        hideJob?.cancel()
        hideJob = null
    }

    /**
     * 平台进入后台时立即隐藏全部明文。
     */
    fun onBackground() = hide()
}
