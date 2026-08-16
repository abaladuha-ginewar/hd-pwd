package com.hdpwd.shared.storage

import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.security.LocalEnvelopeRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * 本地用户索引中的非敏感摘要。
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
 * 本地用户、信封、Vault 与生物识别封装的统一仓储。
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
     * 读取本机信封。
     */
    suspend fun readEnvelope(userId: String): LocalEnvelopeRecord? {
        val raw = bytes.read(envelopeKey(userId)) ?: return null
        return vaultJson.decodeFromString(raw.decodeToString())
    }

    /**
     * 写入本机信封。
     */
    suspend fun writeEnvelope(userId: String, envelope: LocalEnvelopeRecord) {
        val payload = vaultJson.encodeToString(envelope).encodeToByteArray()
        bytes.writeAtomically(envelopeKey(userId), payload)
    }

    /**
     * 读取生物识别封装的 LEK；不存在时返回 null。
     */
    suspend fun readBiometricSealed(userId: String): ByteArray? =
        bytes.read(biometricKey(userId))

    /**
     * 写入或清除生物识别封装。
     */
    suspend fun writeBiometricSealed(userId: String, sealed: ByteArray?) {
        if (sealed == null) {
            bytes.delete(biometricKey(userId))
        } else {
            bytes.writeAtomically(biometricKey(userId), sealed)
        }
    }

    /**
     * 解密读取 Vault。
     */
    suspend fun readVault(userId: String, recoveryPassword: CharSequence): VaultState {
        val encrypted = vaultStore.read(userId) ?: return VaultState(EntityId(userId))
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
     * 删除某用户的全部本地数据。
     */
    suspend fun deleteUser(userId: String) {
        vaultStore.delete(userId)
        bytes.delete(envelopeKey(userId))
        bytes.delete(biometricKey(userId))
    }

    private fun envelopeKey(userId: String) = "$userId-envelope"

    private fun biometricKey(userId: String) = "$userId-biometric"

    companion object {
        private const val INDEX_KEY = "users-index"
    }
}
