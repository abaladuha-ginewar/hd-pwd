package com.hdpwd.shared

import com.hdpwd.shared.sync.S3CompatibilityMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证首版 S3 厂商兼容性矩阵覆盖所有预设。
 */
class S3CompatibilityMatrixTest {
    /**
     * 每个厂商都必须使用 Signature V4 并声明 CORS 测试要求。
     */
    @Test
    fun matrixCoversAllPresets() {
        assertEquals(4, S3CompatibilityMatrix.profiles.size)
        assertTrue(S3CompatibilityMatrix.profiles.all { it.signatureVersion == "AWS4-HMAC-SHA256" })
        assertTrue(S3CompatibilityMatrix.profiles.all { it.requiresCors })
    }
}
