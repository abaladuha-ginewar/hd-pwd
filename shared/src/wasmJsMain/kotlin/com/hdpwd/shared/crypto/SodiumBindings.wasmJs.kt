@file:Suppress("UNUSED")

package com.hdpwd.shared.crypto

import kotlin.js.JsAny
import kotlin.js.JsName
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * libsodium-wrappers-sumo 的最小外部 API。
 *
 * 二进制不要走 Kotlin/Wasm 的 Uint8Array operator get/set：该路径在 Wasm
 * 互操作里不可靠，会导致本端加解密自洽、却解不开 PC/安卓密文。
 */
external interface SodiumApi : JsAny {
    /**
     * libsodium 初始化 Promise。
     */
    val ready: Promise<JsAny?>
}

/**
 * 导入 npm 包的默认 libsodium 对象。
 */
@JsModule("libsodium-wrappers-sumo")
@JsName("default")
external val sodium: SodiumApi

/**
 * Kotlin ByteArray 转小写十六进制，供 @JsFun 跨 FFI 传递。
 */
internal fun ByteArray.toWasmHex(): String =
    joinToString("") { byte ->
        val value = byte.toInt() and 0xff
        HEX[value ushr 4].toString() + HEX[value and 0x0f]
    }

/**
 * 小写或大写十六进制转 ByteArray。
 */
internal fun String.wasmHexToBytes(): ByteArray {
    require(length % 2 == 0) { "十六进制长度无效" }
    return ByteArray(length / 2) { index ->
        val hi = hexValue(this[index * 2])
        val lo = hexValue(this[index * 2 + 1])
        ((hi shl 4) or lo).toByte()
    }
}

private fun hexValue(char: Char): Int =
    when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> error("非法 Hex 字符")
    }

private const val HEX = "0123456789abcdef"

/**
 * 浏览器 CSPRNG，在 JS 侧填充并转 hex，避免 Uint8Array 互操作丢字节。
 */
@JsFun(
    """
    (size) => {
      function u8ToHex(u8) {
        var hex = '';
        for (var i = 0; i < u8.length; i++) {
          hex += ('0' + u8[i].toString(16)).slice(-2);
        }
        return hex;
      }
      var u8 = new Uint8Array(size);
      crypto.getRandomValues(u8);
      return u8ToHex(u8);
    }
    """,
)
internal external fun browserRandomHex(size: Int): String

/**
 * Argon2id：在 JS 里把 hex 解码成真正的 Uint8Array 再调用 libsodium。
 */
@JsFun(
    """
    (sodium, passwordHex, saltHex, opsLimit, memoryKiB, outputLength) => {
      function hexToU8(hex) {
        if (!hex) return new Uint8Array(0);
        var out = new Uint8Array(hex.length / 2);
        for (var i = 0; i < out.length; i++) {
          out[i] = parseInt(hex.substr(i * 2, 2), 16);
        }
        return out;
      }
      function u8ToHex(u8) {
        var hex = '';
        for (var i = 0; i < u8.length; i++) {
          hex += ('0' + u8[i].toString(16)).slice(-2);
        }
        return hex;
      }
      var out = sodium.crypto_pwhash(
        outputLength,
        hexToU8(passwordHex),
        hexToU8(saltHex),
        opsLimit,
        memoryKiB * 1024,
        sodium.crypto_pwhash_ALG_ARGON2ID13,
        'uint8array'
      );
      return u8ToHex(out);
    }
    """,
)
internal external fun sodiumPwhashArgon2idHex(
    sodium: JsAny,
    passwordHex: String,
    saltHex: String,
    opsLimit: Int,
    memoryKiB: Int,
    outputLength: Int,
): String

/**
 * hash-wasm 命名导出命名空间。不要用 @JsName("argon2d") 绑顶层函数：
 * Kotlin/Wasm 会错生成 `module.default(...)`，运行时直接 TypeError。
 */
@JsModule("hash-wasm")
external object HashWasm : JsAny

/**
 * 在 JS 里取 `hash-wasm.argon2d` 再派生，返回 hex。
 */
@JsFun(
    """
    (hashWasm, passwordHex, saltHex, iterations, memoryKiB, parallelism, outputLength) => {
      var argon2d = hashWasm && (hashWasm.argon2d || (hashWasm.default && hashWasm.default.argon2d));
      if (typeof argon2d !== 'function') {
        throw new Error('hash-wasm.argon2d unavailable');
      }
      function hexToU8(hex) {
        if (!hex) return new Uint8Array(0);
        var out = new Uint8Array(hex.length / 2);
        for (var i = 0; i < out.length; i++) {
          out[i] = parseInt(hex.substr(i * 2, 2), 16);
        }
        return out;
      }
      return argon2d({
        password: hexToU8(passwordHex),
        salt: hexToU8(saltHex),
        iterations: iterations,
        memorySize: memoryKiB,
        parallelism: parallelism,
        hashLength: outputLength,
        outputType: 'hex'
      });
    }
    """,
)
internal external fun hashWasmArgon2dHex(
    hashWasm: JsAny,
    passwordHex: String,
    saltHex: String,
    iterations: Int,
    memoryKiB: Int,
    parallelism: Int,
    outputLength: Int,
): Promise<JsString>

/**
 * XChaCha20-Poly1305 IETF 加密。secret_nonce 必须是 JS null。
 */
@JsFun(
    """
    (sodium, messageHex, adHex, nonceHex, keyHex) => {
      function hexToU8(hex) {
        if (!hex) return new Uint8Array(0);
        var out = new Uint8Array(hex.length / 2);
        for (var i = 0; i < out.length; i++) {
          out[i] = parseInt(hex.substr(i * 2, 2), 16);
        }
        return out;
      }
      function u8ToHex(u8) {
        var hex = '';
        for (var i = 0; i < u8.length; i++) {
          hex += ('0' + u8[i].toString(16)).slice(-2);
        }
        return hex;
      }
      var out = sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
        hexToU8(messageHex),
        hexToU8(adHex),
        null,
        hexToU8(nonceHex),
        hexToU8(keyHex),
        'uint8array'
      );
      return u8ToHex(out);
    }
    """,
)
internal external fun sodiumXchachaEncryptHex(
    sodium: JsAny,
    messageHex: String,
    adHex: String,
    nonceHex: String,
    keyHex: String,
): String

/**
 * XChaCha20-Poly1305 IETF 解密。secret_nonce 必须是 JS null。
 */
@JsFun(
    """
    (sodium, ciphertextHex, adHex, nonceHex, keyHex) => {
      function hexToU8(hex) {
        if (!hex) return new Uint8Array(0);
        var out = new Uint8Array(hex.length / 2);
        for (var i = 0; i < out.length; i++) {
          out[i] = parseInt(hex.substr(i * 2, 2), 16);
        }
        return out;
      }
      function u8ToHex(u8) {
        var hex = '';
        for (var i = 0; i < u8.length; i++) {
          hex += ('0' + u8[i].toString(16)).slice(-2);
        }
        return hex;
      }
      var out = sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
        null,
        hexToU8(ciphertextHex),
        hexToU8(adHex),
        hexToU8(nonceHex),
        hexToU8(keyHex),
        'uint8array'
      );
      return u8ToHex(out);
    }
    """,
)
internal external fun sodiumXchachaDecryptHex(
    sodium: JsAny,
    ciphertextHex: String,
    adHex: String,
    nonceHex: String,
    keyHex: String,
): String
