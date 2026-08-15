package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import kotlinx.serialization.Serializable

/**
 * 常用兼容 S3 云厂商的快速配置预设。
 */
enum class S3ProviderPreset(
    val displayName: String,
    val providerCode: String,
    val defaultRegion: String,
) {
    GENERIC("通用 S3", "s3", "us-east-1"),
    ALIYUN("阿里云 OSS", "aliyun-oss", "cn-hangzhou"),
    TENCENT("腾讯云 COS", "tencent-cos", "ap-guangzhou"),
    QINIU("七牛云 Kodo", "qiniu-kodo", "z0"),
}

/**
 * S3 凭据的引用元数据，Secret 内容只允许在加密 Vault 载荷中出现。
 */
@Serializable
data class EncryptedS3CredentialRef(
    val credentialId: EntityId,
    val accessKeyId: String,
    val secretVersion: Int = 1,
)

/**
 * 创建待确认的 S3 目标配置。
 */
fun S3ProviderPreset.toTarget(
    id: EntityId,
    endpoint: String,
    bucket: String,
) = SyncTarget(
    id = id,
    provider = providerCode,
    endpoint = endpoint,
    bucket = bucket,
    region = defaultRegion,
    enabled = false,
    confirmed = false,
    status = SyncStatus.IDLE,
)
