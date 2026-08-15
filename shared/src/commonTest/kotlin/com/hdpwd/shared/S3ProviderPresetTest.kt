package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.sync.S3ProviderPreset
import com.hdpwd.shared.sync.toTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证通用 S3、阿里云、腾讯云和七牛云快速配置预设。
 */
class S3ProviderPresetTest {
    /**
     * 每个预设都能生成待确认目标并保留供应商区域。
     */
    @Test
    fun allProviderPresetsCreatePendingTargets() {
        S3ProviderPreset.entries.forEach { preset ->
            val target = preset.toTarget(EntityId(preset.providerCode), "https://example.test", "bucket")
            assertEquals(preset.providerCode, target.provider)
            assertTrue(target.region.isNotBlank())
            assertTrue(!target.confirmed)
        }
    }
}
