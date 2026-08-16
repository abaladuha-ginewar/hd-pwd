package com.hdpwd.shared.storage

/**
 * 浏览器 localStorage 的 AtomicByteStore 实现。
 *
 * Kotlin/Wasm 上 IndexedDB 绑定兼容性较差，故用 localStorage + Hex 编码替代；
 * 密码库密文体积通常远小于配额，满足 Web 本地缓存需求。
 */
class WasmLocalStorageByteStore(
    private val prefix: String = "hdpwd:",
) : AtomicByteStore {
    override suspend fun read(key: String): ByteArray? {
        validateKey(key)
        val encoded = localStorageGetItem(storageKey(key)) ?: return null
        return runCatching { decodeHex(encoded) }.getOrNull()
    }

    override suspend fun writeAtomically(key: String, bytes: ByteArray) {
        validateKey(key)
        // localStorage 单次 setItem 对调用方表现为原子写入
        localStorageSetItem(storageKey(key), encodeHex(bytes))
    }

    override suspend fun delete(key: String) {
        validateKey(key)
        localStorageRemoveItem(storageKey(key))
    }

    private fun storageKey(key: String): String = "$prefix$key"

    private fun validateKey(key: String) {
        require(key.matches(Regex("[A-Za-z0-9._-]+"))) { "存储 key 包含非法路径字符" }
    }

    private fun encodeHex(bytes: ByteArray): String =
        bytes.joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            HEX[value ushr 4].toString() + HEX[value and 0x0f]
        }

    private fun decodeHex(text: String): ByteArray {
        require(text.length % 2 == 0) { "Hex 长度无效" }
        return ByteArray(text.length / 2) { index ->
            val hi = hexValue(text[index * 2])
            val lo = hexValue(text[index * 2 + 1])
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

    private companion object {
        const val HEX = "0123456789abcdef"
    }
}

@JsFun("(key) => globalThis.localStorage.getItem(key)")
private external fun localStorageGetItem(key: String): String?

@JsFun("(key, value) => { globalThis.localStorage.setItem(key, value); }")
private external fun localStorageSetItem(key: String, value: String)

@JsFun("(key) => { globalThis.localStorage.removeItem(key); }")
private external fun localStorageRemoveItem(key: String)
