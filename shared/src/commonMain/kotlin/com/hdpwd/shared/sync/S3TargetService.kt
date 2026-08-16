package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget

/**
 * 当前用户的 S3 目标配置编辑和连接测试服务。
 */
class S3TargetService {
    /**
     * 添加一个默认待确认的同步目标。
     */
    fun add(
        current: List<SyncTarget>,
        target: SyncTarget,
    ): List<SyncTarget> {
        val normalized = normalize(target)
        require(
            normalized.endpoint.startsWith("https://") ||
                normalized.endpoint.startsWith("http://localhost") ||
                normalized.endpoint.startsWith("http://127.0.0.1"),
        ) {
            "端点地址必须以 https:// 开头（本机调试可用 http://localhost）"
        }
        require(normalized.bucket.matches(Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))) {
            "Bucket 名称无效：需为 3–63 位小写字母、数字、点或短横线"
        }
        require(normalized.region.isNotBlank()) { "区域不能为空" }
        require(normalized.accessKeyId.isNotBlank()) { "请填写 Access Key" }
        require(normalized.encryptedCredentialsHex.isNotBlank() && normalized.credentialsSaltHex.isNotBlank()) {
            "请填写 Secret Access Key"
        }
        require(current.none { it.id == normalized.id }) { "该同步目标已存在" }
        // 本机手动添加视为用户已确认；从备份导入的目标仍走待确认流程
        return current + normalized.copy(
            enabled = true,
            confirmed = true,
            status = SyncStatus.PENDING,
            lastErrorCode = null,
        )
    }

    /**
     * 修改目标的非敏感连接配置并撤销原有确认。
     */
    fun update(
        current: List<SyncTarget>,
        target: SyncTarget,
    ): List<SyncTarget> {
        val normalized = normalize(target)
        require(current.any { it.id == normalized.id }) { "同步目标不存在" }
        return current.map {
            if (it.id == normalized.id) {
                normalized.copy(enabled = false, confirmed = false, status = SyncStatus.IDLE)
            } else {
                it
            }
        }
    }

    /**
     * 删除本地 S3 配置，不删除远端 bucket 或对象。
     */
    fun remove(current: List<SyncTarget>, targetId: EntityId): List<SyncTarget> =
        current.filterNot { it.id == targetId }

    /**
     * 使用最小 list 请求测试目标连接。
     */
    suspend fun testConnection(target: SyncTarget, store: S3ObjectStore): SyncTarget =
        try {
            val prefix = target.objectPrefix.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }
            store.list(prefix)
            target.copy(status = SyncStatus.SUCCESS, lastErrorCode = null)
        } catch (failure: Throwable) {
            target.copy(
                status = SyncStatus.FAILED,
                lastErrorCode = failure.message?.take(64)
                    ?: failure::class.simpleName?.take(64)
                    ?: "connection-failed",
            )
        }

    private fun normalize(target: SyncTarget): SyncTarget =
        target.copy(
            endpoint = target.endpoint.trim().trimEnd('/'),
            bucket = target.bucket.trim(),
            region = target.region.trim(),
            objectPrefix = normalizeObjectPrefix(target.objectPrefix),
            provider = target.provider.trim().ifBlank { S3ProviderPreset.CUSTOM.providerCode },
            accessKeyId = target.accessKeyId.trim(),
            encryptedCredentialsHex = target.encryptedCredentialsHex.trim().lowercase(),
            credentialsSaltHex = target.credentialsSaltHex.trim().lowercase(),
        )
}
