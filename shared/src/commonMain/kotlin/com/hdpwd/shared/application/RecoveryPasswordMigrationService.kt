package com.hdpwd.shared.application

import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.security.LocalEnvelopeKey
import com.hdpwd.shared.security.LocalEnvelopeService
import com.hdpwd.shared.security.UserRecoveryEnvelope
import com.hdpwd.shared.storage.VaultCipher
import com.hdpwd.shared.storage.VaultStore

/**
 * 远端迁移参与者，写入失败时必须允许恢复旧载荷。
 */
interface MigrationTarget {
    /**
     * 提交新恢复密码加密载荷。
     */
    suspend fun writeNew(payload: ByteArray)

    /**
     * 回滚到旧恢复密码加密载荷。
     */
    suspend fun restoreOld(payload: ByteArray)
}

/**
 * 以本地优先、失败回滚方式执行恢复密码全库迁移。
 */
class RecoveryPasswordMigrationService(
    private val vaultCipher: VaultCipher,
    private val localEnvelopeService: LocalEnvelopeService,
    private val localStore: VaultStore,
) {
    /**
     * 重新加密本地及远端数据，并把新恢复密码封装到现有 DeviceLEK。
     */
    suspend fun migrate(
        vault: VaultState,
        oldRecoveryPassword: CharSequence,
        newRecoveryPassword: CharSequence,
        deviceKey: LocalEnvelopeKey,
        deviceGeneration: String,
        targets: List<MigrationTarget>,
    ): RecoveryPasswordMigrationResult {
        val oldPayload = vaultCipher.encrypt(oldRecoveryPassword, vault)
        val newPayload = vaultCipher.encrypt(newRecoveryPassword, vault)
        val newEnvelope = localEnvelopeService.sealRecoveryPassword(
            deviceKey = deviceKey,
            generation = deviceGeneration,
            userId = vault.vaultId.value,
            recoveryPassword = newRecoveryPassword,
        )
        return try {
            localStore.write(vault.vaultId.value, newPayload)
            targets.forEach { it.writeNew(newPayload) }
            RecoveryPasswordMigrationResult(newPayload.copyOf(), newEnvelope)
        } catch (failure: Throwable) {
            localStore.write(vault.vaultId.value, oldPayload)
            targets.forEach { target ->
                runCatching { target.restoreOld(oldPayload) }
            }
            throw failure
        } finally {
            oldPayload.fill(0)
            newPayload.fill(0)
        }
    }
}

/**
 * 恢复密码迁移提交结果。
 */
data class RecoveryPasswordMigrationResult(
    val encryptedPayload: ByteArray,
    val recoveryEnvelope: UserRecoveryEnvelope,
    val historicalBackupsWarning: String =
        "迁移前导出的历史备份仍需使用迁移前的恢复密码",
)
