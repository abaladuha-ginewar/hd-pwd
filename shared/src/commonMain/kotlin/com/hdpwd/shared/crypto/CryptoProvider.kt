package com.hdpwd.shared.crypto

import kotlinx.serialization.Serializable

/**
 * 三端共享的密码学门面，具体原语由经过依赖验证的平台实现提供。
 */
interface CryptoProvider {
    /**
     * 生成密码学安全随机字节。
     */
    fun randomBytes(size: Int): ByteArray

    /**
     * 使用固定参数 Argon2id 派生密钥。
     */
    suspend fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray

    /**
     * 使用 Argon2d 派生密钥。
     *
     * 仅用于解开 diglol Android 0.2.0 把 `Argon2.Type.ID` 错映射成 Argon2d
     * 而写出的历史密文；新密文必须走 [argon2id]。测试替身默认同 [argon2id]。
     */
    suspend fun argon2d(
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray = argon2id(password, salt, parameters)

    /**
     * 使用 HKDF-SHA-256 做用途隔离派生。
     */
    fun hkdfSha256(
        keyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray

    /**
     * 使用 XChaCha20-Poly1305 认证加密。
     */
    suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray

    /**
     * 使用 XChaCha20-Poly1305 验证并解密。
     */
    suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
}

/**
 * 可写入格式头部的 KDF 参数。
 */
@Serializable
data class KdfParameters(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val outputLength: Int = 32,
)

/**
 * 为不同业务用途生成独立 HKDF 上下文。
 */
object CryptoDomains {
    const val DATA = "hdpwd/data/v1"
    const val SYNC = "hdpwd/sync/v1"
    const val BACKUP = "hdpwd/backup/v1"
    const val GENERATOR = "hdpwd/generator/v1"
    const val LOCAL_ENVELOPE = "hdpwd/local-envelope/v1"
    /** 设备锁 DeviceLEK 包装的附加认证数据域。 */
    const val DEVICE_LOCK = "hdpwd/device-lock/v1"
    /** 按用户封装本机恢复密码的附加认证数据域。 */
    const val USER_RECOVERY = "hdpwd/user-recovery/v1"
}

/**
 * 依据 RFC 5869 实现 HKDF-SHA-256，供平台门面复用。
 */
internal fun hkdfSha256Portable(
    keyMaterial: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray {
    require(length >= 0 && length <= 255 * 32) { "HKDF 输出长度无效" }
    val extractSalt = if (salt.isEmpty()) ByteArray(32) else salt
    val prk = PortableHmacSha256.mac(extractSalt, keyMaterial)
    val output = ByteArray(length)
    var previous = byteArrayOf()
    var offset = 0
    var counter = 1
    while (offset < length) {
        previous = PortableHmacSha256.mac(
            prk,
            previous + info + byteArrayOf(counter.toByte()),
        )
        val copyLength = minOf(previous.size, length - offset)
        previous.copyInto(output, offset, 0, copyLength)
        offset += copyLength
        counter++
    }
    return output
}
