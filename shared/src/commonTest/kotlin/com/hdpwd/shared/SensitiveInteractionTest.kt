package com.hdpwd.shared

import com.hdpwd.shared.security.ClipboardPort
import com.hdpwd.shared.security.SensitiveClipboardController
import com.hdpwd.shared.security.SensitiveRevealController
import com.hdpwd.shared.ui.ResponsiveLayoutPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证敏感显示和剪贴板自动清理的可测试计时逻辑。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensitiveInteractionTest {
    /**
     * 布局策略保证手机单列、宽屏多列。
     */
    @Test
    fun responsiveLayoutUsesAvailableWidth() {
        kotlin.test.assertEquals(1, ResponsiveLayoutPolicy.columnsFor(360))
        kotlin.test.assertEquals(4, ResponsiveLayoutPolicy.columnsFor(1120))
    }

    /**
     * 显示计时到期后必须隐藏。
     */
    @Test
    fun revealExpires() = runTest {
        val controller = SensitiveRevealController(this, hideAfterMillis = 100)
        controller.reveal("temporary")
        assertEquals("temporary", controller.current())
        advanceTimeBy(100)
        runCurrent()
        assertNull(controller.current())
    }

    /**
     * 剪贴板内容未变更时到期清理。
     */
    @Test
    fun clipboardClearsOnlySameContent() = runTest {
        val clipboard = TestClipboard()
        val controller = SensitiveClipboardController(this, clipboard, clearAfterMillis = 100)
        controller.copySensitive("temporary")
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertNull(clipboard.value)
    }
}

/**
 * 敏感剪贴板控制器测试用适配器。
 */
private class TestClipboard : ClipboardPort {
    var value: String? = null
    override suspend fun readText(): String? = value
    override suspend fun writeText(text: String) {
        value = text
    }
    override suspend fun clear() {
        value = null
    }
}
