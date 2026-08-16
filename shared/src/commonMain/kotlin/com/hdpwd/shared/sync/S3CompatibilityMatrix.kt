package com.hdpwd.shared.sync

/**
 * S3 厂商连接兼容性测试参数。
 */
data class S3CompatibilityProfile(
    val preset: S3ProviderPreset,
    val supportsPathStyle: Boolean,
    val requiresCors: Boolean,
    val signatureVersion: String = "AWS4-HMAC-SHA256",
)

/**
 * 首版需要覆盖的对象存储连接矩阵。
 */
object S3CompatibilityMatrix {
    /**
     * 返回通用/自定义及国内外主流厂商的测试矩阵。
     */
    val profiles: List<S3CompatibilityProfile> = S3ProviderPreset.entries.map { preset ->
        S3CompatibilityProfile(
            preset = preset,
            supportsPathStyle = when (preset) {
                S3ProviderPreset.ALIYUN -> false
                else -> true
            },
            requiresCors = true,
        )
    }
}
