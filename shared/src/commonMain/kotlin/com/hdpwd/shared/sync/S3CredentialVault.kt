package com.hdpwd.shared.sync

import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.EncryptedContainerCodec
import com.hdpwd.shared.crypto.KdfParameters

/**
 * 使用 Vault 派生用途密钥独立加密 S3 Secret 的服务。
 */
class S3CredentialVault(
    private val crypto: CryptoProvider,
    private val kdfParameters: KdfParameters,
) {
    private val container = EncryptedContainerCodec(crypto)
    private val aad = CryptoDomains.SYNC.encodeToByteArray()

    /**
     * 仅在调用期间读取 Secret 并返回加密凭据载荷。
     */
    suspend fun seal(
        syncKey: ByteArray,
        credentials: S3Credentials,
    ): ByteArray {
        val secret = credentials.useSecret { it.copyOf() }
        val accessKey = credentials.accessKeyId.encodeToByteArray()
        val payload = accessKey.size.toString().encodeToByteArray() + byteArrayOf(0) +
            accessKey + secret
        return try {
            container.seal(syncKey, payload, kdfParameters, aad)
        } finally {
            secret.fill(0)
            payload.fill(0)
        }
    }

    /**
     * 解密凭据并返回临时 S3Credentials，调用方完成请求后必须 clear。
     */
    suspend fun open(syncKey: ByteArray, encrypted: ByteArray): S3Credentials {
        val payload = container.open(syncKey, encrypted)
        return try {
            val separator = payload.indexOf(0)
            require(separator > 0) { "S3 凭据载荷无效" }
            val accessLength = payload.copyOfRange(0, separator).decodeToString().toInt()
            val accessStart = separator + 1
            val accessEnd = accessStart + accessLength
            require(accessEnd <= payload.size) { "S3 access key 长度无效" }
            S3Credentials(
                payload.copyOfRange(accessStart, accessEnd).decodeToString(),
                payload.copyOfRange(accessEnd, payload.size),
            )
        } finally {
            payload.fill(0)
        }
    }

    /**
     * 用恢复密码派生 SyncKey 后封装凭据，返回可写入 SyncTarget 的十六进制字段。
     */
    suspend fun sealWithRecoveryPassword(
        recoveryPassword: CharSequence,
        credentials: S3Credentials,
    ): SealedS3CredentialPayload {
        val salt = crypto.randomBytes(16)
        val syncKey = deriveSyncKey(recoveryPassword, salt)
        return try {
            val sealed = seal(syncKey, credentials)
            SealedS3CredentialPayload(
                accessKeyId = credentials.accessKeyId,
                encryptedCredentialsHex = sealed.toCredentialHex(),
                credentialsSaltHex = salt.toCredentialHex(),
            )
        } finally {
            syncKey.fill(0)
            salt.fill(0)
        }
    }

    /**
     * 用恢复密码解封目标上的凭据。
     */
    suspend fun openWithRecoveryPassword(
        recoveryPassword: CharSequence,
        encryptedCredentialsHex: String,
        credentialsSaltHex: String,
    ): S3Credentials {
        require(encryptedCredentialsHex.isNotBlank() && credentialsSaltHex.isNotBlank()) {
            "尚未配置 S3 访问密钥"
        }
        val salt = credentialsSaltHex.credentialHexToBytes()
        val syncKey = deriveSyncKey(recoveryPassword, salt)
        return try {
            open(syncKey, encryptedCredentialsHex.credentialHexToBytes())
        } finally {
            syncKey.fill(0)
            salt.fill(0)
        }
    }

    private suspend fun deriveSyncKey(recoveryPassword: CharSequence, salt: ByteArray): ByteArray {
        val rootKey = crypto.argon2id(
            password = recoveryPassword.toString().encodeToByteArray(),
            salt = salt,
            parameters = kdfParameters,
        )
        return try {
            crypto.hkdfSha256(
                keyMaterial = rootKey,
                salt = salt,
                info = CryptoDomains.SYNC.encodeToByteArray(),
                length = 32,
            )
        } finally {
            rootKey.fill(0)
        }
    }
}

/**
 * 写入 SyncTarget 的已封装凭据字段。
 */
data class SealedS3CredentialPayload(
    val accessKeyId: String,
    val encryptedCredentialsHex: String,
    val credentialsSaltHex: String,
)

private fun ByteArray.indexOf(value: Int): Int =
    indexOfFirst { (it.toInt() and 0xff) == value }

private fun ByteArray.toCredentialHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun String.credentialHexToBytes(): ByteArray {
    require(length % 2 == 0) { "S3 凭据编码无效" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
