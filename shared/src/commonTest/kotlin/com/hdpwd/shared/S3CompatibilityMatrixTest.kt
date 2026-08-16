package com.hdpwd.shared

import com.hdpwd.shared.sync.S3CompatibilityMatrix
import com.hdpwd.shared.sync.S3ProviderPreset
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
        assertEquals(S3ProviderPreset.entries.size, S3CompatibilityMatrix.profiles.size)
        assertTrue(S3CompatibilityMatrix.profiles.all { it.signatureVersion == "AWS4-HMAC-SHA256" })
        assertTrue(S3CompatibilityMatrix.profiles.all { it.requiresCors })
        assertTrue(
            S3CompatibilityMatrix.profiles.any { it.preset == S3ProviderPreset.ALIYUN && !it.supportsPathStyle },
        )
        assertTrue(
            S3CompatibilityMatrix.profiles.any {
                it.preset == S3ProviderPreset.CSTCLOUD_CAPSULE && it.supportsPathStyle
            },
        )
        assertTrue(S3ProviderPreset.CSTCLOUD_CAPSULE.forcePathStyle)
    }
}
