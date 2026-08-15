package com.hdpwd.shared.storage

import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.domain.VaultValidator
import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.security.AuthorizationSession
import com.hdpwd.shared.security.LocalEnvelopeRecord
import com.hdpwd.shared.security.LocalEnvelopeService
import com.hdpwd.shared.security.OperationPurpose

/**
 * 自包含加密 `.dat` 备份服务。
 */
class BackupService(
    private val vaultCipher: VaultCipher,
    private val backupCipher: VaultCipher = vaultCipher,
) {
    /**
     * 导出不含用户名和本机封装材料的加密备份正文。
     */
    suspend fun export(
        recoveryPassword: CharSequence,
        vault: VaultState,
    ): ByteArray = backupCipher.encrypt(recoveryPassword, vault)

    /**
     * 在五分钟会话内取得导出许可，临时解密恢复密码并在完成后清理。
     */
    suspend fun exportWithAuthorization(
        session: AuthorizationSession,
        localEnvelopeService: LocalEnvelopeService,
        localEnvelope: LocalEnvelopeRecord,
        vault: VaultState,
    ): ByteArray {
        val permit = session.acquire(OperationPurpose.EXPORT_BACKUP)
            ?: error("授权会话已失效")
        return try {
            session.withEnvelopeKeySuspending(permit) { envelopeKey ->
                localEnvelopeService.withRecoveryPassword(localEnvelope, envelopeKey) { recoveryPassword ->
                    export(recoveryPassword, vault)
                }
            }
        } finally {
            permit.close()
        }
    }

    /**
     * 使用恢复密码认证并导入备份正文。
     */
    suspend fun import(
        recoveryPassword: CharSequence,
        backup: ByteArray,
    ): VaultState = backupCipher.decrypt(recoveryPassword, backup).also(VaultValidator::requireValid)

    /**
     * 创建明确区分 DataKey 与 BackupKey 域的生产备份服务。
     */
    companion object {
        /**
         * 使用同一 CryptoProvider 创建 Vault 与备份用途隔离的服务。
         */
        fun production(crypto: CryptoProvider): BackupService = BackupService(
            vaultCipher = AuthenticatedVaultCipher(crypto, keyDomain = CryptoDomains.DATA),
            backupCipher = AuthenticatedVaultCipher(crypto, keyDomain = CryptoDomains.BACKUP),
        )
    }
}
