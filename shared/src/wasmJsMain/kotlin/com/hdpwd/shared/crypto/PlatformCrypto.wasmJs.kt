package com.hdpwd.shared.crypto

import kotlinx.coroutines.await

/**
 * Web 使用 libsodium-wrappers-sumo 的生产密码学提供者。
 */
actual fun platformCryptoProvider(): CryptoProvider = LibsodiumWasmCryptoProvider()

/**
 * 通过浏览器 CSPRNG、Argon2id 和 XChaCha20-Poly1305 实现 Wasm 密码学门面。
 */
private class LibsodiumWasmCryptoProvider : CryptoProvider {
    /**
     * 使用浏览器 Web Crypto 生成同步随机数。
     */
    override fun randomBytes(size: Int): ByteArray =
        browserCrypto.getRandomValues(SodiumUint8Array(size)).toKotlinByteArray()

    /**
     * 等待 libsodium 初始化后执行 Argon2id。
     */
    override suspend fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray {
        sodium.ready.await<kotlin.js.JsAny?>()
        return sodium.crypto_pwhash(
            outputLength = parameters.outputLength,
            password = password.toSodiumArray(),
            salt = salt.toSodiumArray(),
            opsLimit = parameters.iterations,
            memoryLimit = parameters.memoryKiB * 1024,
            algorithm = sodium.crypto_pwhash_ALG_ARGON2ID13,
        ).toKotlinByteArray()
    }

    /**
     * 使用共享 RFC 5869 HKDF-SHA-256 实现。
     */
    override fun hkdfSha256(
        keyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray = hkdfSha256Portable(keyMaterial, salt, info, length)

    /**
     * 等待 libsodium 初始化后执行 XChaCha20-Poly1305 加密。
     */
    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray =
        sodium.run {
            ready.await<kotlin.js.JsAny?>()
            crypto_aead_xchacha20poly1305_ietf_encrypt(
                message = plaintext.toSodiumArray(),
                additionalData = aad.toSodiumArray(),
                nonce = nonce.toSodiumArray(),
                key = key.toSodiumArray(),
            ).toKotlinByteArray()
        }

    /**
     * 等待 libsodium 初始化后验证并解密 XChaCha20-Poly1305 密文。
     */
    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray =
        sodium.run {
            ready.await<kotlin.js.JsAny?>()
            crypto_aead_xchacha20poly1305_ietf_decrypt(
                secretNonce = null,
                ciphertext = ciphertext.toSodiumArray(),
                additionalData = aad.toSodiumArray(),
                nonce = nonce.toSodiumArray(),
                key = key.toSodiumArray(),
            ).toKotlinByteArray()
        }
}
