package com.hdpwd.shared.security

import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.EncryptedContainerCodec
import com.hdpwd.shared.crypto.KdfParameters
import kotlinx.serialization.Serializable

/**
 * 仅保存本机的恢复密码封装数据，不属于 Vault 或备份载荷。
 */
@Serializable
data class LocalEnvelopeRecord(
    val formatVersion: Int = 1,
    val wrappedLocalEnvelopeKey: ByteArray,
    val encryptedRecoveryPassword: ByteArray,
)

/**
 * 通过本机主密码保护 LEK，并在需要时临时解密恢复密码。
 */
class LocalEnvelopeService(
    private val crypto: CryptoProvider,
    private val kdfParameters: KdfParameters,
) {
    private val container = EncryptedContainerCodec(crypto)
    private val associatedData = CryptoDomains.LOCAL_ENVELOPE.encodeToByteArray()

    /**
     * 创建随机 LEK、本机主密码封装和恢复密码密文。
     */
    suspend fun create(
        recoveryPassword: CharSequence,
        localPassword: CharSequence,
    ): LocalEnvelopeRecord {
        val envelopeKey = crypto.randomBytes(32)
        val localSalt = crypto.randomBytes(16)
        val localKey = deriveLocalKey(localPassword, localSalt)
        return try {
            val wrappedKey = container.seal(
                key = localKey,
                plaintext = envelopeKey,
                kdfParameters = kdfParameters,
                associatedData = associatedData,
                salt = localSalt,
            )
            val encryptedRecovery = container.seal(
                key = envelopeKey,
                plaintext = recoveryPassword.toString().encodeToByteArray(),
                kdfParameters = kdfParameters,
                associatedData = associatedData,
            )
            LocalEnvelopeRecord(
                wrappedLocalEnvelopeKey = wrappedKey,
                encryptedRecoveryPassword = encryptedRecovery,
            )
        } finally {
            envelopeKey.fill(0)
            localKey.fill(0)
        }
    }

    /**
     * 使用本机主密码解封装 LEK；错误密码由 AEAD 完整性校验拒绝。
     */
    suspend fun unlockLocalKey(
        record: LocalEnvelopeRecord,
        localPassword: CharSequence,
    ): LocalEnvelopeKey {
        val header = container.readHeader(record.wrappedLocalEnvelopeKey)
        val localKey = deriveLocalKey(localPassword, header.saltHex.hexToByteArray())
        return try {
            LocalEnvelopeKey(container.open(localKey, record.wrappedLocalEnvelopeKey))
        } finally {
            localKey.fill(0)
        }
    }

    /**
     * 在恢复密码的最短必要生命周期内执行回调并清理明文字节。
     */
    suspend fun <T> withRecoveryPassword(
        record: LocalEnvelopeRecord,
        envelopeKey: LocalEnvelopeKey,
        block: suspend (CharSequence) -> T,
    ): T {
        val key = envelopeKey.use { it.copyOf() }
        val recoveryBytes = try {
            container.open(key, record.encryptedRecoveryPassword)
        } finally {
            key.fill(0)
        }
        return try {
            block(recoveryBytes.decodeToString())
        } finally {
            recoveryBytes.fill(0)
        }
    }

    /**
     * 使用已验证的恢复密码重新封装本机主密码，不改变 Vault 数据。
     */
    suspend fun resetLocalPassword(
        record: LocalEnvelopeRecord,
        oldLocalPassword: CharSequence,
        newLocalPassword: CharSequence,
    ): LocalEnvelopeRecord {
        val oldKey = unlockLocalKey(record, oldLocalPassword)
        return try {
            withRecoveryPassword(record, oldKey) { recoveryPassword ->
                create(recoveryPassword, newLocalPassword)
            }
        } finally {
            oldKey.clear()
        }
    }

    private suspend fun deriveLocalKey(password: CharSequence, salt: ByteArray): ByteArray =
        crypto.argon2id(password.toString().encodeToByteArray(), salt, kdfParameters)
}

/**
 * 解码本机封装头中的十六进制 salt。
 */
private fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "本机封装 salt 无效" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
