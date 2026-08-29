package com.hdpwd.shared.crypto

import kotlin.js.JsString
import kotlinx.coroutines.await

/**
 * Web 使用 libsodium-wrappers-sumo 的生产密码学提供者。
 */
actual fun platformCryptoProvider(): CryptoProvider = LibsodiumWasmCryptoProvider()

/**
 * 通过浏览器 CSPRNG、Argon2id 和 XChaCha20-Poly1305 实现 Wasm 密码学门面。
 *
 * 所有二进制均以 hex 经 @JsFun 进入真实 JS Uint8Array，避免 Kotlin/Wasm
 * 对 TypedArray 的 get/set 与 Kotlin null→JS null 映射问题。
 */
private class LibsodiumWasmCryptoProvider : CryptoProvider {
    /**
     * 使用浏览器 Web Crypto 生成同步随机数。
     */
    override fun randomBytes(size: Int): ByteArray = browserRandomHex(size).wasmHexToBytes()

    /**
     * 等待 libsodium 初始化后执行 Argon2id。
     */
    override suspend fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray = withSodium("密钥派生失败") {
        sodiumPwhashArgon2idHex(
            sodium,
            password.toWasmHex(),
            salt.toWasmHex(),
            parameters.iterations,
            parameters.memoryKiB,
            parameters.outputLength,
        ).wasmHexToBytes()
    }

    /**
     * 用 hash-wasm 做 Argon2d v1.3，对齐 Diglol Type.D / Signal Type.Argon2d。
     */
    override suspend fun argon2d(
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray {
        try {
            val hex = hashWasmArgon2dHex(
                HashWasm,
                password.toWasmHex(),
                salt.toWasmHex(),
                parameters.iterations,
                parameters.memoryKiB,
                parameters.parallelism,
                parameters.outputLength,
            ).await<JsString>()
            return hex.toString().wasmHexToBytes()
        } catch (ex: Throwable) {
            throw IllegalStateException("密钥派生失败", ex)
        }
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
     *
     * Diglol 的 encrypt 会把 24 字节 nonce 前缀写进密文；Web 必须输出相同格式，
     * 否则 PC/安卓备份在浏览器里会 AEAD 失败。
     */
    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray =
        withSodium("加密失败") {
            val body = sodiumXchachaEncryptHex(
                sodium,
                plaintext.toWasmHex(),
                aad.toWasmHex(),
                nonce.toWasmHex(),
                key.toWasmHex(),
            ).wasmHexToBytes()
            nonce + body
        }

    /**
     * 等待 libsodium 初始化后验证并解密 XChaCha20-Poly1305 密文。
     */
    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray =
        withSodium("解密失败") {
            val body = stripLeadingNonce(nonce, ciphertext)
            sodiumXchachaDecryptHex(
                sodium,
                body.toWasmHex(),
                aad.toWasmHex(),
                nonce.toWasmHex(),
                key.toWasmHex(),
            ).wasmHexToBytes()
        }

    /**
     * 若密文以给定 nonce 开头（Diglol 格式），则剥掉前缀再交给 libsodium。
     */
    private fun stripLeadingNonce(nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        if (ciphertext.size > nonce.size &&
            ciphertext.copyOf(nonce.size).contentEquals(nonce)
        ) {
            return ciphertext.copyOfRange(nonce.size, ciphertext.size)
        }
        return ciphertext
    }

    private suspend fun <T> withSodium(failure: String, block: () -> T): T {
        try {
            sodium.ready.await<kotlin.js.JsAny?>()
            return block()
        } catch (ex: Throwable) {
            throw IllegalStateException(failure, ex)
        }
    }
}
