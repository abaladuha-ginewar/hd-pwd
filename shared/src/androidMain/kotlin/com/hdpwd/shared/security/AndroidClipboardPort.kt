package com.hdpwd.shared.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Android 系统剪贴板适配。
 */
class AndroidClipboardPort(
    private val context: Context,
) : ClipboardPort {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    /**
     * 读取当前剪贴板文本。
     */
    override suspend fun readText(): String? =
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()

    /**
     * 写入剪贴板文本。
     */
    override suspend fun writeText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("哈密", text))
    }

    /**
     * 清空剪贴板。
     */
    override suspend fun clear() {
        clipboard.clearPrimaryClip()
    }
}
