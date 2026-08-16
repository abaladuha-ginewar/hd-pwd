package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import kotlinx.serialization.Serializable

/**
 * 常用兼容 S3 云厂商的快速配置预设。
 *
 * 选择预设后可自动填充 Endpoint / 默认区域；[CUSTOM] 与勾选“手动编辑全部参数”时保留完整自定义。
 */
enum class S3ProviderPreset(
    val displayName: String,
    val providerCode: String,
    val defaultRegion: String,
    val regionHint: String,
) {
    CUSTOM(
        displayName = "自定义（全部手填）",
        providerCode = "custom",
        defaultRegion = "us-east-1",
        regionHint = "按厂商文档填写区域代码",
    ),
    AWS(
        displayName = "AWS S3",
        providerCode = "aws-s3",
        defaultRegion = "ap-northeast-1",
        regionHint = "如 ap-northeast-1、us-east-1",
    ),
    ALIYUN(
        displayName = "阿里云 OSS",
        providerCode = "aliyun-oss",
        defaultRegion = "cn-hangzhou",
        regionHint = "如 cn-hangzhou、cn-beijing、cn-shanghai",
    ),
    TENCENT(
        displayName = "腾讯云 COS",
        providerCode = "tencent-cos",
        defaultRegion = "ap-guangzhou",
        regionHint = "如 ap-guangzhou、ap-shanghai、ap-beijing",
    ),
    HUAWEI(
        displayName = "华为云 OBS",
        providerCode = "huawei-obs",
        defaultRegion = "cn-north-4",
        regionHint = "如 cn-north-4、cn-east-3、cn-south-1",
    ),
    QINIU(
        displayName = "七牛云 Kodo",
        providerCode = "qiniu-kodo",
        defaultRegion = "cn-east-1",
        regionHint = "如 cn-east-1、cn-north-1、cn-south-1、us-north-1",
    ),
    VOLCENGINE(
        displayName = "火山引擎 TOS",
        providerCode = "volcengine-tos",
        defaultRegion = "cn-beijing",
        regionHint = "如 cn-beijing、cn-shanghai、cn-guangzhou",
    ),
    BAIDU(
        displayName = "百度智能云 BOS",
        providerCode = "baidu-bos",
        defaultRegion = "bj",
        regionHint = "如 bj、gz、su、hkg",
    ),
    JDCLOUD(
        displayName = "京东云 OSS",
        providerCode = "jdcloud-oss",
        defaultRegion = "cn-north-1",
        regionHint = "如 cn-north-1、cn-east-1、cn-south-1",
    ),
    UCLOUD(
        displayName = "UCloud US3",
        providerCode = "ucloud-us3",
        defaultRegion = "cn-bj",
        regionHint = "如 cn-bj、cn-gd、cn-sh2",
    ),
    CLOUDFLARE(
        displayName = "Cloudflare R2",
        providerCode = "cloudflare-r2",
        defaultRegion = "auto",
        regionHint = "固定填写 auto",
    ),
    BACKBLAZE(
        displayName = "Backblaze B2",
        providerCode = "backblaze-b2",
        defaultRegion = "us-west-004",
        regionHint = "如 us-west-004、eu-central-003",
    ),
    WASABI(
        displayName = "Wasabi",
        providerCode = "wasabi",
        defaultRegion = "us-east-1",
        regionHint = "如 us-east-1、us-west-1、ap-northeast-1",
    ),
    DIGITALOCEAN(
        displayName = "DigitalOcean Spaces",
        providerCode = "do-spaces",
        defaultRegion = "nyc3",
        regionHint = "如 nyc3、sfo3、sgp1、fra1",
    ),
    MINIO(
        displayName = "MinIO / 自建",
        providerCode = "minio",
        defaultRegion = "us-east-1",
        regionHint = "自建服务常用 us-east-1",
    ),
    ;

    /**
     * 是否需要用户完整手填 Endpoint。
     */
    val requiresManualEndpoint: Boolean
        get() = this == CUSTOM || this == MINIO

    /**
     * 是否需要额外填写 Account ID（如 Cloudflare R2）。
     */
    val requiresAccountId: Boolean
        get() = this == CLOUDFLARE

    /**
     * 根据区域（及可选 Account ID）生成推荐 Endpoint；无法生成时返回空串。
     */
    fun suggestEndpoint(region: String, accountId: String = ""): String {
        val r = region.trim().ifBlank { defaultRegion }
        return when (this) {
            CUSTOM, MINIO -> ""
            AWS -> "https://s3.$r.amazonaws.com"
            ALIYUN -> "https://oss-$r.aliyuncs.com"
            TENCENT -> "https://cos.$r.myqcloud.com"
            HUAWEI -> "https://obs.$r.myhuaweicloud.com"
            QINIU -> "https://s3-$r.qiniucs.com"
            VOLCENGINE -> "https://tos-s3-$r.volces.com"
            BAIDU -> "https://s3.$r.bcebos.com"
            JDCLOUD -> "https://s3.$r.jdcloud-oss.com"
            UCLOUD -> "https://s3-$r.ufileos.com"
            CLOUDFLARE -> {
                val id = accountId.trim()
                if (id.isEmpty()) "" else "https://$id.r2.cloudflarestorage.com"
            }
            BACKBLAZE -> "https://s3.$r.backblazeb2.com"
            WASABI -> "https://s3.$r.wasabisys.com"
            DIGITALOCEAN -> "https://$r.digitaloceanspaces.com"
        }
    }

    companion object {
        /**
         * 按 provider 代码解析预设；未知时回落为自定义。
         */
        fun fromProviderCode(code: String): S3ProviderPreset =
            entries.firstOrNull { it.providerCode.equals(code, ignoreCase = true) } ?: CUSTOM
    }
}

/**
 * 规范化对象目录前缀：去掉首尾斜杠与空白，禁止 `..` 路径穿越。
 */
fun normalizeObjectPrefix(raw: String): String {
    val trimmed = raw.trim().trim('/')
    require(!trimmed.contains("..")) { "对象目录不能包含 .." }
    require(trimmed.matches(Regex("[A-Za-z0-9._\\-/]*"))) {
        "对象目录仅允许字母、数字、点、下划线、短横线和斜杠"
    }
    return trimmed
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
    objectPrefix: String = "",
) = SyncTarget(
    id = id,
    provider = providerCode,
    endpoint = endpoint,
    bucket = bucket,
    region = defaultRegion,
    objectPrefix = normalizeObjectPrefix(objectPrefix),
    enabled = false,
    confirmed = false,
    status = SyncStatus.IDLE,
)
