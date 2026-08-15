package com.hdpwd.shared.storage

import com.hdpwd.shared.domain.VaultState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 版本化的加密 Blob 外层头部。
 */
@Serializable
data class EncryptedBlobHeader(
    val magic: String = "HDPW",
    val formatVersion: Int = 1,
    val kdf: String = "argon2id",
    val aead: String = "xchacha20-poly1305",
    val saltBase64Url: String,
    val nonceBase64Url: String,
)

/**
 * 认证加密存储的完整容器。
 */
@Serializable
data class EncryptedBlob(
    val header: EncryptedBlobHeader,
    val ciphertextBase64Url: String,
)

/**
 * 加密 Vault 的平台无关持久化接口。
 */
interface VaultStore {
    /**
     * 读取当前用户的加密快照，不存在时返回 null。
     */
    suspend fun read(userId: String): ByteArray?

    /**
     * 以原子语义写入当前用户的加密快照。
     */
    suspend fun write(userId: String, encryptedSnapshot: ByteArray)

    /**
     * 删除本地用户数据，不触碰远端同步目标。
     */
    suspend fun delete(userId: String)
}

/**
 * 加密服务的稳定门面，具体实现由各平台安全依赖提供。
 */
interface VaultCipher {
    /**
     * 使用恢复密码加密 VaultState。
     */
    suspend fun encrypt(recoveryPassword: CharSequence, vault: VaultState): ByteArray

    /**
     * 使用恢复密码认证并解密 VaultState。
     */
    suspend fun decrypt(recoveryPassword: CharSequence, encrypted: ByteArray): VaultState
}

/**
 * 共享序列化器配置，禁止未知字段静默丢失。
 */
val vaultJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = true
}
