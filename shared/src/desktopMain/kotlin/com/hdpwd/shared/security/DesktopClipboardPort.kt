package com.hdpwd.shared.security

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Windows/Desktop 系统剪贴板适配。
 */
class DesktopClipboardPort : ClipboardPort {
    /**
     * 读取当前剪贴板文本。
     */
    override suspend fun readText(): String? = withContext(Dispatchers.IO) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard
                .getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()
    }

    /**
     * 写入剪贴板文本。
     */
    override suspend fun writeText(text: String) {
        withContext(Dispatchers.IO) {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(text), null)
        }
    }

    /**
     * 清空剪贴板。
     */
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(""), null)
        }
    }
}
