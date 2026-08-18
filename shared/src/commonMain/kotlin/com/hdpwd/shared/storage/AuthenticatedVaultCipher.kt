package com.hdpwd.shared.storage

import com.hdpwd.shared.crypto.ContainerHeader
import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.EncryptedContainerCodec
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.crypto.openWithArgon2Compat
import com.hdpwd.shared.domain.VaultState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 使用恢复密码派生 DataKey 并认证加密 VaultState。
 */
class AuthenticatedVaultCipher(
    private val crypto: CryptoProvider,
    private val kdfParameters: KdfParameters = DefaultKdfParameters,
    private val keyDomain: String = CryptoDomains.DATA,
) : VaultCipher {
    private val container = EncryptedContainerCodec(crypto)

    /**
     * 派生 DataKey 后加密完整 Vault 状态。
     */
    override suspend fun encrypt(recoveryPassword: CharSequence, vault: VaultState): ByteArray {
        val salt = crypto.randomBytes(16)
        val dataKey = deriveDataKey(recoveryPassword, salt)
        val associatedData = vault.vaultId.value.encodeToByteArray()
        return try {
            container.seal(
                key = dataKey,
                plaintext = vaultJson.encodeToString(vault).encodeToByteArray(),
                kdfParameters = kdfParameters,
                associatedData = associatedData,
                salt = salt,
            )
        } finally {
            dataKey.fill(0)
        }
    }

    /**
     * 读取头部参数、重新派生 DataKey 并验证解密 Vault 状态。
     */
    override suspend fun decrypt(recoveryPassword: CharSequence, encrypted: ByteArray): VaultState {
        val header: ContainerHeader = container.readHeader(encrypted)
        val salt = header.saltHex.hexToByteArray()
        val password = recoveryPassword.toString().encodeToByteArray()
        val payload = try {
            openWithArgon2Compat(
                crypto = crypto,
                password = password,
                salt = salt,
                parameters = header.kdfParameters,
                deriveKey = { rootKey -> isolateKey(rootKey, salt) },
                open = { dataKey ->
                    try {
                        container.open(dataKey, encrypted)
                    } catch (failure: Throwable) {
                        throw IllegalArgumentException("恢复密码错误，或备份/密码库数据已损坏", failure)
                    }
                },
            )
        } finally {
            password.fill(0)
            salt.fill(0)
        }
        return vaultJson.decodeFromString(payload.decodeToString())
    }

    private suspend fun deriveDataKey(
        recoveryPassword: CharSequence,
        salt: ByteArray,
        parameters: KdfParameters = kdfParameters,
    ): ByteArray {
        val rootKey = crypto.argon2id(
            password = recoveryPassword.toString().encodeToByteArray(),
            salt = salt,
            parameters = parameters,
        )
        return try {
            isolateKey(rootKey, salt)
        } finally {
            rootKey.fill(0)
        }
    }

    private fun isolateKey(rootKey: ByteArray, salt: ByteArray): ByteArray =
        crypto.hkdfSha256(
            keyMaterial = rootKey,
            salt = salt,
            info = keyDomain.encodeToByteArray(),
            length = 32,
        )
}

/**
 * 首版数据 KDF 参数，写入容器头部并允许未来格式版本升级。
 */
val DefaultKdfParameters = KdfParameters(
    memoryKiB = 64 * 1024,
    iterations = 3,
    parallelism = 1,
    outputLength = 32,
)

/**
 * 解码十六进制 salt。
 */
private fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "salt 编码无效" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
