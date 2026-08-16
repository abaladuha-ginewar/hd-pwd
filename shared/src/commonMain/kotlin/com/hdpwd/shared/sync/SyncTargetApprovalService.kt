package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.domain.VaultState

/**
 * 控制从备份或远端同步来的 S3 目标必须经过用户确认。
 */
class SyncTargetApprovalService {
    /**
     * 导入时将所有目标置为未确认和停用。
     */
    fun pendingImportedTargets(vault: VaultState): VaultState =
        vault.copy(
            syncTargets = vault.syncTargets.map {
                it.copy(enabled = false, confirmed = false)
            },
        )

    /**
     * 本机创建用户并主动导入备份时：对已含完整凭据的 S3 目标视为用户确认，可立即同步。
     */
    fun activateLocalBackupTargets(vault: VaultState): VaultState =
        vault.copy(
            syncTargets = vault.syncTargets.map { target ->
                if (hasUsableCredentials(target)) {
                    target.copy(
                        enabled = true,
                        confirmed = true,
                        status = if (target.status == SyncStatus.FAILED) target.status else SyncStatus.PENDING,
                        lastErrorCode = null,
                    )
                } else {
                    target.copy(enabled = false, confirmed = false)
                }
            },
        )

    /**
     * 用户确认单个目标后才允许自动连接。
     */
    fun confirm(vault: VaultState, targetId: EntityId): VaultState =
        vault.copy(
            syncTargets = vault.syncTargets.map {
                if (it.id == targetId) it.copy(confirmed = true, enabled = true) else it
            },
        )

    /**
     * 撤销目标确认并暂停自动连接。
     */
    fun revoke(vault: VaultState, targetId: EntityId): VaultState =
        vault.copy(
            syncTargets = vault.syncTargets.map {
                if (it.id == targetId) it.copy(confirmed = false, enabled = false) else it
            },
        )

    private fun hasUsableCredentials(target: SyncTarget): Boolean =
        target.accessKeyId.isNotBlank() &&
            target.encryptedCredentialsHex.isNotBlank() &&
            target.credentialsSaltHex.isNotBlank() &&
            target.endpoint.isNotBlank() &&
            target.bucket.isNotBlank()
}
