package com.hdpwd.shared.sync

import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.EncryptedContainerCodec
import com.hdpwd.shared.crypto.KdfParameters

/**
 * 使用 Vault 派生用途密钥独立加密 S3 Secret 的服务。
 */
class S3CredentialVault(
    crypto: CryptoProvider,
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
}

private fun ByteArray.indexOf(value: Int): Int =
    indexOfFirst { (it.toInt() and 0xff) == value }
