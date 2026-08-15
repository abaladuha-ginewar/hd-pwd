package com.hdpwd.shared.crypto

import com.hdpwd.shared.storage.vaultJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 加密容器的非敏感认证头部。
 */
@Serializable
data class ContainerHeader(
    val magic: String = "HDPW",
    val formatVersion: Int = 1,
    val kdf: String = "argon2id",
    val aead: String = "xchacha20-poly1305",
    val saltHex: String,
    val nonceHex: String,
    val associatedDataHex: String,
    val kdfParameters: KdfParameters,
)

/**
 * 认证加密容器编解码器。
 */
class EncryptedContainerCodec(
    private val crypto: CryptoProvider,
) {
    /**
     * 使用随机盐和 nonce 封装密文，头部作为附加认证数据。
     */
    suspend fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        kdfParameters: KdfParameters,
        associatedData: ByteArray = byteArrayOf(),
        salt: ByteArray = crypto.randomBytes(16),
        nonce: ByteArray = crypto.randomBytes(24),
    ): ByteArray {
        val header = ContainerHeader(
            saltHex = salt.toHex(),
            nonceHex = nonce.toHex(),
            associatedDataHex = associatedData.toHex(),
            kdfParameters = kdfParameters,
        )
        val headerBytes = vaultJson.encodeToString(header).encodeToByteArray()
        val ciphertext = crypto.seal(key, nonce, plaintext, headerBytes + associatedData)
        return buildByteArray {
            appendBytes("HDPW".encodeToByteArray())
            appendByte(header.formatVersion.toByte())
            appendInt(headerBytes.size)
            appendBytes(headerBytes)
            appendBytes(ciphertext)
        }
    }

    /**
     * 验证格式头部、附加认证数据并解密载荷。
     */
    suspend fun open(key: ByteArray, encoded: ByteArray): ByteArray {
        val parsed = parse(encoded)
        val header = parsed.first
        val nonce = header.nonceHex.hexToByteArray()
        val associatedData = header.associatedDataHex.hexToByteArray()
        return crypto.open(
            key,
            nonce,
            parsed.second,
            parsed.third + associatedData,
        )
    }

    /**
     * 读取并校验非敏感容器头部。
     */
    fun readHeader(encoded: ByteArray): ContainerHeader = parse(encoded).first

    private fun parse(encoded: ByteArray): Triple<ContainerHeader, ByteArray, ByteArray> {
        require(encoded.size >= 9) { "加密容器长度不足" }
        require(encoded.copyOfRange(0, 4).decodeToString() == "HDPW") { "加密容器 magic 无效" }
        val version = encoded[4].toInt() and 0xff
        require(version == 1) { "不支持的加密容器版本" }
        val headerLength = readInt(encoded, 5)
        require(headerLength in 1..encoded.size - 9) { "加密容器头部长度无效" }
        val headerStart = 9
        val headerEnd = headerStart + headerLength
        val headerBytes = encoded.copyOfRange(headerStart, headerEnd)
        val header = vaultJson.decodeFromString<ContainerHeader>(headerBytes.decodeToString())
        require(header.magic == "HDPW" && header.formatVersion == version) {
            "加密容器头部不匹配"
        }
        return Triple(header, encoded.copyOfRange(headerEnd, encoded.size), headerBytes)
    }
}

/**
 * 以大端序写入整数的容器构建器。
 */
private class ByteArrayBuilder {
    private val parts = mutableListOf<ByteArray>()

    /**
     * 追加字节数组。
     */
    fun appendBytes(bytes: ByteArray) {
        parts += bytes
    }

    /**
     * 追加单个字节。
     */
    fun appendByte(byte: Byte) {
        parts += byteArrayOf(byte)
    }

    /**
     * 合并全部容器片段。
     */
    fun build(): ByteArray = parts.fold(byteArrayOf()) { result, part -> result + part }

    /**
     * 追加四字节大端整数。
     */
    fun appendInt(value: Int) {
        appendBytes(
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            ),
        )
    }
}

/**
 * 创建容器字节数组的便捷函数。
 */
private fun buildByteArray(block: ByteArrayBuilder.() -> Unit): ByteArray =
    ByteArrayBuilder().apply(block).build()

/**
 * 读取四字节大端整数。
 */
private fun readInt(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 24) or
        ((bytes[offset + 1].toInt() and 0xff) shl 16) or
        ((bytes[offset + 2].toInt() and 0xff) shl 8) or
        (bytes[offset + 3].toInt() and 0xff)

/**
 * 将字节数组编码为小写十六进制。
 */
private fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/**
 * 将小写或大写十六进制解码为字节数组。
 */
private fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "十六进制长度无效" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
