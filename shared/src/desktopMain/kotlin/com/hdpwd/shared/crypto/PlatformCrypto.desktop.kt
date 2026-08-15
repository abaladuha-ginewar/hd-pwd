package com.hdpwd.shared.crypto

import diglol.crypto.Argon2
import diglol.crypto.XChaCha20Poly1305
import diglol.crypto.random.nextBytes

/**
 * Desktop/JVM 使用 Diglol Crypto 的生产密码学提供者。
 */
actual fun platformCryptoProvider(): CryptoProvider = DiglolCryptoProvider()

/**
 * 通过 Argon2id 和 XChaCha20-Poly1305 实现 JVM 密码学门面。
 */
private class DiglolCryptoProvider : CryptoProvider {
    /**
     * 生成密码学安全随机字节。
     */
    override fun randomBytes(size: Int): ByteArray = nextBytes(size)

    /**
     * 使用 RFC 9106 Argon2id 派生密钥。
     */
    override suspend fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray = Argon2(
        version = Argon2.Version.V13,
        type = Argon2.Type.ID,
        iterations = parameters.iterations,
        memory = parameters.memoryKiB,
        parallelism = parameters.parallelism,
        hashSize = parameters.outputLength,
    ).deriveKey(password, salt)

    /**
     * 使用标准 HKDF-SHA-256 扩展用途密钥。
     */
    override fun hkdfSha256(
        keyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray = hkdfSha256Portable(keyMaterial, salt, info, length)

    /**
     * 使用 XChaCha20-Poly1305 认证加密。
     */
    override suspend fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray = XChaCha20Poly1305(key, nonce).encrypt(plaintext, aad)

    /**
     * 验证并解密 XChaCha20-Poly1305 密文。
     */
    override suspend fun open(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): ByteArray = XChaCha20Poly1305(key, nonce).decrypt(ciphertext, aad)
}
