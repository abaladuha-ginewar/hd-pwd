package com.hdpwd.shared.crypto

import diglol.crypto.XChaCha20Poly1305
import diglol.crypto.random.nextBytes
import org.signal.argon2.Argon2
import org.signal.argon2.Type
import org.signal.argon2.Version

/**
 * Android 使用 Diglol AEAD 与 Signal Argon2 的生产密码学提供者。
 *
 * 不使用 diglol-crypto 0.2.0 的 Argon2 封装：其 Android 实现把 `Type.ID` 错映射成 Argon2d，
 * 导致安卓导出的备份无法在 Windows 上用真正的 Argon2id 解开。
 */
actual fun platformCryptoProvider(): CryptoProvider = AndroidCryptoProvider()

/**
 * 通过 RFC 9106 Argon2id 和 XChaCha20-Poly1305 实现 Android 密码学门面。
 */
private class AndroidCryptoProvider : CryptoProvider {
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
    ): ByteArray = argon2(Type.Argon2id, password, salt, parameters)

    /**
     * 使用 Argon2d 派生，仅兼容旧版 diglol Android 错映射写出的密文。
     */
    override suspend fun argon2d(
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray = argon2(Type.Argon2d, password, salt, parameters)

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

    private fun argon2(
        type: Type,
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray = Argon2.Builder(Version.V13)
        .type(type)
        .iterations(parameters.iterations)
        .memoryCostKiB(parameters.memoryKiB)
        .parallelism(parameters.parallelism)
        .hashLength(parameters.outputLength)
        .build()
        .hash(password, salt)
        .hash
}
