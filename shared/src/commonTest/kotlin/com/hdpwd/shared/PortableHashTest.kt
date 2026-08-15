package com.hdpwd.shared

import com.hdpwd.shared.crypto.PortableSha256
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 固定 SHA-256 向量，防止跨平台字节序实现漂移。
 */
class PortableHashTest {
    /**
     * 验证空输入的标准 SHA-256 向量。
     */
    @Test
    fun emptySha256Vector() {
        val actual = PortableSha256.digest(byteArrayOf())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            actual,
        )
    }
}
