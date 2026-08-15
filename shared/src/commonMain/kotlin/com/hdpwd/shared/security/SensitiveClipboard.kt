package com.hdpwd.shared.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 平台剪贴板适配接口。
 */
interface ClipboardPort {
    /**
     * 读取当前剪贴板文本，平台不允许读取时返回 null。
     */
    suspend fun readText(): String?

    /**
     * 写入临时敏感文本。
     */
    suspend fun writeText(text: String)

    /**
     * 清除剪贴板内容。
     */
    suspend fun clear()
}

/**
 * 只在剪贴板内容未被替换时清除敏感文本的控制器。
 */
class SensitiveClipboardController(
    private val scope: CoroutineScope,
    private val clipboard: ClipboardPort,
    private val clearAfterMillis: Long = 15_000L,
) {
    private var clearJob: Job? = null

    /**
     * 写入密码或恢复配方并安排条件清除。
     */
    fun copySensitive(text: String) {
        clearJob?.cancel()
        clearJob = scope.launch {
            clipboard.writeText(text)
            delay(clearAfterMillis)
            if (clipboard.readText() == text) clipboard.clear()
        }
    }

    /**
     * 取消当前自动清理任务，不主动改写剪贴板。
     */
    fun cancel() {
        clearJob?.cancel()
        clearJob = null
    }
}
