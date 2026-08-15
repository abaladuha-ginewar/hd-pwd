package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.Conflict
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget

/**
 * 设置入口可直接消费的同步状态摘要。
 */
data class SyncUiStatus(
    val targets: List<SyncTarget>,
    val conflictCount: Int,
) {
    /**
     * 任意目标失败或存在冲突时显示红点。
     */
    val hasAttention: Boolean
        get() = conflictCount > 0 || targets.any { it.status == SyncStatus.FAILED }

    /**
     * 顶部提示所需的失败目标数量。
     */
    val failedTargetCount: Int
        get() = targets.count { it.status == SyncStatus.FAILED }
}

/**
 * 从当前 Vault 状态计算设置入口提示状态。
 */
fun syncUiStatus(targets: List<SyncTarget>, conflicts: List<Conflict>): SyncUiStatus =
    SyncUiStatus(targets, conflicts.size)
