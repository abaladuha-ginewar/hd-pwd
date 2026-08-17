package com.hdpwd.shared.storage

import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.security.DeviceLockRecord
import com.hdpwd.shared.security.UserRecoveryEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * 本地用户索引中的非敏感摘要，不得包含密钥材料。
 */
@Serializable
data class PersistedUserMeta(
    val id: String,
    val username: String,
    val hasBiometric: Boolean = false,
)

@Serializable
private data class PersistedUserIndex(
    val users: List<PersistedUserMeta> = emptyList(),
)

/**
 * 本地用户、设备锁、每用户恢复密码封装与 Vault 的统一仓储。
 */
class LocalAppRepository(
    private val bytes: AtomicByteStore,
    crypto: CryptoProvider,
) {
    private val vaultStore = AtomicVaultStore(bytes)
    private val vaultCipher = AuthenticatedVaultCipher(crypto)

    /**
     * 读取用户索引。
     */
    suspend fun listUsers(): List<PersistedUserMeta> {
        val raw = bytes.read(INDEX_KEY) ?: return emptyList()
        return vaultJson.decodeFromString<PersistedUserIndex>(raw.decodeToString()).users
    }

    /**
     * 覆盖写入用户索引。
     */
    suspend fun saveUsers(users: List<PersistedUserMeta>) {
        val payload = vaultJson.encodeToString(PersistedUserIndex(users)).encodeToByteArray()
        bytes.writeAtomically(INDEX_KEY, payload)
    }

    /**
     * 读取设备锁；尚未设置时返回 null。
     */
    suspend fun readDeviceLock(): DeviceLockRecord? {
        val raw = bytes.read(DEVICE_LOCK_KEY) ?: return null
        return vaultJson.decodeFromString(raw.decodeToString())
    }

    /**
     * 写入设备锁记录，与用户索引分离。
     */
    suspend fun writeDeviceLock(record: DeviceLockRecord) {
        val payload = vaultJson.encodeToString(record).encodeToByteArray()
        bytes.writeAtomically(DEVICE_LOCK_KEY, payload)
    }

    /**
     * 读取设备级生物识别封装；不存在时返回 null。
     */
    suspend fun readDeviceBiometricSealed(): ByteArray? = bytes.read(DEVICE_BIOMETRIC_KEY)

    /**
     * 写入或清除设备级生物识别封装。
     */
    suspend fun writeDeviceBiometricSealed(sealed: ByteArray?) {
        if (sealed == null) {
            bytes.delete(DEVICE_BIOMETRIC_KEY)
        } else {
            bytes.writeAtomically(DEVICE_BIOMETRIC_KEY, sealed)
        }
    }

    /**
     * 读取某用户的本机恢复密码封装。
     */
    suspend fun readEnvelope(userId: String): UserRecoveryEnvelope? {
        val raw = bytes.read(envelopeKey(userId)) ?: return null
        return vaultJson.decodeFromString(raw.decodeToString())
    }

    /**
     * 写入某用户的本机恢复密码封装。
     */
    suspend fun writeEnvelope(userId: String, envelope: UserRecoveryEnvelope) {
        val payload = vaultJson.encodeToString(envelope).encodeToByteArray()
        bytes.writeAtomically(envelopeKey(userId), payload)
    }

    /**
     * 读取已废弃的每用户生物识别封装，仅用于清理。
     */
    suspend fun readLegacyUserBiometricSealed(userId: String): ByteArray? =
        bytes.read(legacyUserBiometricKey(userId))

    /**
     * 解密读取 Vault。
     */
    suspend fun readVault(userId: String, recoveryPassword: CharSequence): VaultState {
        val encrypted = vaultStore.read(userId) ?: return VaultState(EntityId(userId))
        return vaultCipher.decrypt(recoveryPassword, encrypted)
    }

    /**
     * 用恢复密码认证已有密文；没有本地密码库或密码错误时失败。
     */
    suspend fun authenticateVault(userId: String, recoveryPassword: CharSequence): VaultState {
        val encrypted = vaultStore.read(userId) ?: error("本地密码库不存在")
        return vaultCipher.decrypt(recoveryPassword, encrypted)
    }

    /**
     * 加密写入 Vault。
     */
    suspend fun writeVault(userId: String, recoveryPassword: CharSequence, vault: VaultState) {
        val encrypted = vaultCipher.encrypt(recoveryPassword, vault)
        vaultStore.write(userId, encrypted)
    }

    /**
     * 删除某用户的密码库与恢复密码封装，保留设备锁。
     */
    suspend fun deleteUser(userId: String) {
        vaultStore.delete(userId)
        bytes.delete(envelopeKey(userId))
        bytes.delete(legacyUserBiometricKey(userId))
    }

    private fun envelopeKey(userId: String) = "$userId-envelope"

    private fun legacyUserBiometricKey(userId: String) = "$userId-biometric"

    companion object {
        private const val INDEX_KEY = "users-index"
        private const val DEVICE_LOCK_KEY = "device-lock"
        private const val DEVICE_BIOMETRIC_KEY = "device-lock-biometric"
    }
}
