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
        require(target.endpoint.startsWith("https://") || target.endpoint.startsWith("http://localhost")) {
            "生产 S3 端点必须使用 TLS"
        }
        require(target.bucket.matches(Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))) {
            "S3 bucket 名称无效"
        }
        require(current.none { it.id == target.id }) { "S3 配置标识已存在" }
        return current + target.copy(enabled = false, confirmed = false, status = SyncStatus.IDLE)
    }

    /**
     * 修改目标的非敏感连接配置并撤销原有确认。
     */
    fun update(
        current: List<SyncTarget>,
        target: SyncTarget,
    ): List<SyncTarget> {
        require(current.any { it.id == target.id }) { "S3 配置不存在" }
        return current.map {
            if (it.id == target.id) target.copy(enabled = false, confirmed = false) else it
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
            store.list("")
            target.copy(status = SyncStatus.SUCCESS, lastErrorCode = null)
        } catch (failure: Throwable) {
            target.copy(
                status = SyncStatus.FAILED,
                lastErrorCode = failure::class.simpleName?.take(64) ?: "connection-failed",
            )
        }
}
