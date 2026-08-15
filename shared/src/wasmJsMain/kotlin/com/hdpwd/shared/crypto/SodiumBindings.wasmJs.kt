@file:Suppress("UNUSED")

package com.hdpwd.shared.crypto

import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.Promise

/**
 * JavaScript Uint8Array 的 Kotlin/Wasm 外部声明。
 */
@JsName("Uint8Array")
external class SodiumUint8Array(length: Int) : JsAny {
    /**
     * 数组长度。
     */
    val length: Int

    /**
     * 读取一个无符号字节。
     */
    operator fun get(index: Int): Int

    /**
     * 写入一个无符号字节。
     */
    operator fun set(index: Int, value: Int)
}

/**
 * libsodium-wrappers-sumo 的最小外部 API。
 */
external interface SodiumApi : JsAny {
    /**
     * libsodium 初始化 Promise。
     */
    val ready: Promise<JsAny?>

    /**
     * 生成随机字节。
     */
    fun randombytes_buf(size: Int): SodiumUint8Array

    /**
     * Argon2id 密码派生。
     */
    fun crypto_pwhash(
        outputLength: Int,
        password: SodiumUint8Array,
        salt: SodiumUint8Array,
        opsLimit: Int,
        memoryLimit: Int,
        algorithm: Int,
    ): SodiumUint8Array

    /**
     * Argon2id 算法标识。
     */
    val crypto_pwhash_ALG_ARGON2ID13: Int

    /**
     * XChaCha20-Poly1305 认证加密。
     */
    fun crypto_aead_xchacha20poly1305_ietf_encrypt(
        message: SodiumUint8Array,
        additionalData: SodiumUint8Array?,
        nonce: SodiumUint8Array,
        key: SodiumUint8Array,
    ): SodiumUint8Array

    /**
     * XChaCha20-Poly1305 验证并解密。
     */
    fun crypto_aead_xchacha20poly1305_ietf_decrypt(
        secretNonce: SodiumUint8Array?,
        ciphertext: SodiumUint8Array,
        additionalData: SodiumUint8Array?,
        nonce: SodiumUint8Array,
        key: SodiumUint8Array,
    ): SodiumUint8Array
}

/**
 * 导入 npm 包的默认 libsodium 对象。
 */
@JsModule("libsodium-wrappers-sumo")
@JsName("default")
external val sodium: SodiumApi

/**
 * 浏览器 Web Crypto 随机数接口。
 */
external interface BrowserCryptoApi : JsAny {
    /**
     * 使用浏览器 CSPRNG 填充数组。
     */
    fun getRandomValues(array: SodiumUint8Array): SodiumUint8Array
}

/**
 * 导入浏览器全局 crypto 对象。
 */
@JsName("crypto")
external val browserCrypto: BrowserCryptoApi

/**
 * Kotlin ByteArray 转 JavaScript Uint8Array。
 */
fun ByteArray.toSodiumArray(): SodiumUint8Array =
    SodiumUint8Array(size).also { target ->
        forEachIndexed { index, byte -> target[index] = byte.toInt() and 0xff }
    }

/**
 * JavaScript Uint8Array 转 Kotlin ByteArray。
 */
fun SodiumUint8Array.toKotlinByteArray(): ByteArray =
    ByteArray(length) { index -> get(index).toByte() }
