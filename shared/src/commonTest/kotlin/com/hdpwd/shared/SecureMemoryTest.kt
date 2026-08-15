package com.hdpwd.shared

import com.hdpwd.shared.security.SensitiveBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails

/**
 * 验证敏感缓冲区清理后不可再次访问。
 */
class SecureMemoryTest {
    /**
     * clear 必须覆盖内容并使后续读取失败。
     */
    @Test
    fun sensitiveBytesAreCleared() {
        val bytes = SensitiveBytes(byteArrayOf(1, 2, 3))
        bytes.use { assertContentEquals(byteArrayOf(1, 2, 3), it) }
        bytes.clear()
        assertFails { bytes.use { error("不应访问已清理数据") } }
    }
}
