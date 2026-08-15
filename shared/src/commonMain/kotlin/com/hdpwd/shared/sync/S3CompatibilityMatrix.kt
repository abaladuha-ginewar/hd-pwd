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
     * 返回通用 S3 及国内厂商的最小测试矩阵。
     */
    val profiles: List<S3CompatibilityProfile> = listOf(
        S3CompatibilityProfile(S3ProviderPreset.GENERIC, true, true),
        S3CompatibilityProfile(S3ProviderPreset.ALIYUN, false, true),
        S3CompatibilityProfile(S3ProviderPreset.TENCENT, true, true),
        S3CompatibilityProfile(S3ProviderPreset.QINIU, true, true),
    )
}
