package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget

/**
 * 只更新单个目标状态、不污染其他目标状态的纯函数。
 */
object SyncStatusReducer {
    /**
     * 标记目标进入待同步状态。
     */
    fun pending(target: SyncTarget): SyncTarget = target.copy(status = SyncStatus.PENDING)

    /**
     * 标记目标进入同步中状态。
     */
    fun syncing(target: SyncTarget): SyncTarget = target.copy(status = SyncStatus.SYNCING)

    /**
     * 标记目标同步成功并清除旧错误。
     */
    fun success(target: SyncTarget): SyncTarget =
        target.copy(status = SyncStatus.SUCCESS, lastErrorCode = null)

    /**
     * 标记目标同步失败，只保存脱敏错误码。
     */
    fun failed(target: SyncTarget, errorCode: String): SyncTarget =
        target.copy(status = SyncStatus.FAILED, lastErrorCode = errorCode.take(64))
}
