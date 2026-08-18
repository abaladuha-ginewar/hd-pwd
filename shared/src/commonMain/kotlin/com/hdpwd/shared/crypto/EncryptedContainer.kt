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
     *
     * 对从安卓拷到 Windows 时常见的 BOM、UTF-16 误转码、前置垃圾字节和扇区尾部填充做兼容。
     */
    suspend fun open(key: ByteArray, encoded: ByteArray): ByteArray {
        val prepared = prepareContainerBytes(encoded)
        val parsed = parsePrepared(prepared)
        val header = parsed.first
        val nonce = header.nonceHex.hexToByteArray()
        val associatedData = header.associatedDataHex.hexToByteArray()
        val aad = parsed.third + associatedData
        return openCiphertext(
            key = key,
            nonce = nonce,
            ciphertext = parsed.second,
            aad = aad,
            originalSize = encoded.size,
            preparedSize = prepared.size,
        )
    }

    /**
     * 读取并校验非敏感容器头部。
     */
    fun readHeader(encoded: ByteArray): ContainerHeader = parse(encoded).first

    private fun parse(encoded: ByteArray): Triple<ContainerHeader, ByteArray, ByteArray> =
        parsePrepared(prepareContainerBytes(encoded))

    private fun parsePrepared(encoded: ByteArray): Triple<ContainerHeader, ByteArray, ByteArray> {
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

    private suspend fun openCiphertext(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
        originalSize: Int,
        preparedSize: Int,
    ): ByteArray {
        try {
            return crypto.open(key, nonce, ciphertext, aad)
        } catch (first: Throwable) {
            val maxTrim = minOf(MAX_TRAILING_PAD, ciphertext.size - 16)
            if (maxTrim <= 0) throw first
            val trims = LinkedHashSet<Int>()
            val trailingZeros = countTrailingZeros(ciphertext, maxTrim)
            for (keptTagZeros in 0..16) {
                val trim = trailingZeros - keptTagZeros
                if (trim in 1..maxTrim) trims += trim
            }
            addAlignedPaddingTrims(originalSize, maxTrim, trims)
            addAlignedPaddingTrims(preparedSize, maxTrim, trims)
            var last = first
            for (trim in trims) {
                try {
                    return crypto.open(key, nonce, ciphertext.copyOf(ciphertext.size - trim), aad)
                } catch (failure: Throwable) {
                    last = failure
                }
            }
            throw last
        }
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

private const val MAGIC = "HDPW"
private const val MAGIC_SEARCH_LIMIT = 256
private const val MAX_TRAILING_PAD = 511

/**
 * 去掉 BOM / UTF-16 误转码，并切到 HDPW 魔数。
 */
internal fun prepareContainerBytes(encoded: ByteArray): ByteArray {
    val collapsed = collapseUtf16IfNeeded(encoded)
    val withoutBom = stripUtf8Bom(collapsed)
    val magicAt = indexOfMagic(withoutBom)
    require(magicAt >= 0) { "加密容器 magic 无效" }
    return if (magicAt == 0) withoutBom else withoutBom.copyOfRange(magicAt, withoutBom.size)
}

private fun stripUtf8Bom(bytes: ByteArray): ByteArray =
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
        bytes.copyOfRange(3, bytes.size)
    } else {
        bytes
    }

private fun collapseUtf16IfNeeded(bytes: ByteArray): ByteArray {
    val leBom = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
    val beBom = bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
    val offset = if (leBom || beBom) 2 else 0
    val remaining = bytes.size - offset
    if (remaining < MAGIC.length * 2 || remaining % 2 != 0) return bytes
    val littleEndian = when {
        leBom -> true
        beBom -> false
        else -> looksLikeUtf16Le(bytes, offset)
    }
    if (!littleEndian && !beBom && !looksLikeUtf16Be(bytes, offset)) return bytes
    val collapsed = ByteArray(remaining / 2)
    for (index in collapsed.indices) {
        val first = bytes[offset + index * 2]
        val second = bytes[offset + index * 2 + 1]
        val (lo, hi) = if (littleEndian) first to second else second to first
        if (hi != 0.toByte()) return bytes
        collapsed[index] = lo
    }
    return collapsed
}

private fun looksLikeUtf16Le(bytes: ByteArray, offset: Int): Boolean =
    MAGIC.indices.all { index ->
        bytes[offset + index * 2] == MAGIC[index].code.toByte() &&
            bytes[offset + index * 2 + 1] == 0.toByte()
    }

private fun looksLikeUtf16Be(bytes: ByteArray, offset: Int): Boolean =
    MAGIC.indices.all { index ->
        bytes[offset + index * 2] == 0.toByte() &&
            bytes[offset + index * 2 + 1] == MAGIC[index].code.toByte()
    }

private fun indexOfMagic(bytes: ByteArray): Int {
    val limit = minOf(bytes.size - MAGIC.length, MAGIC_SEARCH_LIMIT - 1)
    for (index in 0..limit) {
        if (bytes[index] == MAGIC[0].code.toByte() &&
            bytes[index + 1] == MAGIC[1].code.toByte() &&
            bytes[index + 2] == MAGIC[2].code.toByte() &&
            bytes[index + 3] == MAGIC[3].code.toByte()
        ) {
            return index
        }
    }
    return -1
}

private fun countTrailingZeros(bytes: ByteArray, max: Int): Int {
    var count = 0
    while (count < max && bytes[bytes.size - 1 - count] == 0.toByte()) {
        count++
    }
    return count
}

private fun addAlignedPaddingTrims(size: Int, maxTrim: Int, trims: MutableSet<Int>) {
    if (size % 512 != 0) return
    for (trim in 1..minOf(511, maxTrim)) {
        trims += trim
    }
}
