package com.hdpwd.shared.application

import com.hdpwd.shared.storage.BackupService
import com.hdpwd.shared.storage.VaultStore
import com.hdpwd.shared.security.LocalEnvelopeRecord
import com.hdpwd.shared.security.LocalEnvelopeService

/**
 * 在临时验证完成后原子创建本地用户。
 */
class UserImportService(
    private val backupService: BackupService,
    private val localEnvelopeService: LocalEnvelopeService,
    private val vaultStore: VaultStore,
    private val userAccessService: UserAccessService,
) {
    /**
     * 解密备份、建立本机封装、写入 Vault 并最后提交用户索引。
     */
    suspend fun import(
        currentUsers: List<LocalUserRecord>,
        username: String,
        recoveryPassword: CharSequence,
        localPassword: CharSequence,
        backup: ByteArray,
    ): ImportedUser {
        require(currentUsers.none { it.username == username }) { "用户名已存在" }
        val vault = backupService.import(recoveryPassword, backup)
        val localEnvelope = localEnvelopeService.create(recoveryPassword, localPassword)
        val userId = vault.vaultId.value
        val location = "vault/$userId.dat"
        return try {
            vaultStore.write(userId, backup)
            val users = userAccessService.addUser(currentUsers, vault.vaultId, username, location)
            ImportedUser(users, LocalUserRecord(vault.vaultId, username, location), localEnvelope, vault)
        } catch (failure: Throwable) {
            vaultStore.delete(userId)
            throw failure
        }
    }
}

/**
 * 导入成功后的本地用户临时提交结果。
 */
data class ImportedUser(
    val users: List<LocalUserRecord>,
    val user: LocalUserRecord,
    val localEnvelope: LocalEnvelopeRecord,
    val vault: com.hdpwd.shared.domain.VaultState,
)
