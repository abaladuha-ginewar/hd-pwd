package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.sync.S3ProviderPreset
import com.hdpwd.shared.sync.normalizeObjectPrefix
import com.hdpwd.shared.sync.toTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * 验证国内外主流云厂商快速配置预设。
 */
class S3ProviderPresetTest {
    /**
     * 每个预设都能生成待确认目标并保留供应商区域。
     */
    @Test
    fun allProviderPresetsCreatePendingTargets() {
        S3ProviderPreset.entries.forEach { preset ->
            val endpoint = preset.suggestEndpoint(preset.defaultRegion, "account-id")
                .ifBlank { "https://example.test" }
            val target = preset.toTarget(
                id = EntityId(preset.providerCode),
                endpoint = endpoint,
                bucket = "bucket",
                objectPrefix = "hd-pwd",
            )
            assertEquals(preset.providerCode, target.provider)
            assertTrue(target.region.isNotBlank())
            assertEquals("hd-pwd", target.objectPrefix)
            assertTrue(!target.confirmed)
        }
    }

    /**
     * 模板厂商应根据区域自动生成 https 端点。
     */
    @Test
    fun templatedProvidersSuggestHttpsEndpoints() {
        assertEquals(
            "https://oss-cn-hangzhou.aliyuncs.com",
            S3ProviderPreset.ALIYUN.suggestEndpoint("cn-hangzhou"),
        )
        assertEquals(
            "https://cos.ap-guangzhou.myqcloud.com",
            S3ProviderPreset.TENCENT.suggestEndpoint("ap-guangzhou"),
        )
        assertEquals(
            "https://s3.ap-northeast-1.amazonaws.com",
            S3ProviderPreset.AWS.suggestEndpoint("ap-northeast-1"),
        )
        assertEquals(
            "https://abc123.r2.cloudflarestorage.com",
            S3ProviderPreset.CLOUDFLARE.suggestEndpoint("auto", "abc123"),
        )
        assertEquals(
            "https://s3.cstcloud.cn",
            S3ProviderPreset.CSTCLOUD_CAPSULE.suggestEndpoint("us-east-1"),
        )
        assertEquals("", S3ProviderPreset.CUSTOM.suggestEndpoint("us-east-1"))
    }

    /**
     * 对象目录前缀需要规范化并拒绝路径穿越。
     */
    @Test
    fun objectPrefixIsNormalized() {
        assertEquals("a/b", normalizeObjectPrefix(" /a/b/ "))
        assertFails { normalizeObjectPrefix("../secret") }
    }
}
