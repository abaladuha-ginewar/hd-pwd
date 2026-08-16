package com.hdpwd.shared.crypto

import com.hdpwd.shared.domain.PasswordPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/**
 * V1 密码生成与恢复配方服务。
 *
 * V1 的输出协议已固定；更强的密码学 KDF 接入前不得修改此流程，
 * 否则同一恢复密码和配方会在不同版本产生不同结果。
 */
@OptIn(ExperimentalSerializationApi::class)
object PasswordGenerator {
    private const val VERSION = 1
    private const val DOMAIN = "hdpwd-generator-v1"
    private const val FIXED_SALT = "hdpwd-v1-fixed-salt"

    /**
     * 根据恢复密码、key 和完整规则临时生成子密码。
     */
    fun generate(recoveryPassword: CharSequence, key: String, policy: PasswordPolicy): String {
        require(policy.validationError() == null) { policy.validationError() ?: "密码规则无效" }
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) { "key 格式无效" }
        val stream = ByteStream(
            deriveGeneratorKey(recoveryPassword.toString(), key, policy),
            key.protocolBytes() + policy.canonical().protocolBytes(),
        )
        val pools = buildPools(policy)
        val result = ArrayList<Char>(policy.length)
        pools.required.forEach { pool ->
            repeat(pool.second) { result += stream.nextFrom(pool.first) }
        }
        repeat(policy.length - result.size) {
            result += stream.nextFrom(pools.all)
        }
        for (index in result.lastIndex downTo 1) {
            val swapIndex = stream.nextInt(index + 1)
            val old = result[index]
            result[index] = result[swapIndex]
            result[swapIndex] = old
        }
        return result.joinToString("")
    }

    /**
     * 创建不含恢复密码和生成结果的可复制恢复配方。
     */
    fun recipe(key: String, policy: PasswordPolicy): RecoveryRecipe {
        require(policy.validationError() == null) { policy.validationError() ?: "密码规则无效" }
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) { "key 格式无效" }
        return RecoveryRecipe(VERSION, key, policy, checksum(key, policy))
    }

    /**
     * 解析并校验恢复配方文本。
     */
    fun parseRecipe(text: String): RecoveryRecipe {
        val parts = text.split(':', limit = 4)
        require(parts.size == 4 && parts[0] == "hdpwd-recipe" && parts[1] == "v1") {
            "恢复配方格式无效"
        }
        val payload = Cbor.decodeFromByteArray<RecoveryRecipePayload>(Base64Url.decode(parts[2]))
        require(payload.version == 1) { "恢复配方版本无效" }
        require(payload.checksum == parts[3]) { "恢复配方外层校验失败" }
        val recipe = RecoveryRecipe(payload.version, payload.key, payload.policy, payload.checksum)
        require(recipe.checksum == checksum(recipe.key, recipe.policy)) { "恢复配方校验失败" }
        return recipe
    }

    /**
     * 将恢复配方编码为可复制文本。
     */
    fun encodeRecipe(recipe: RecoveryRecipe): String {
        val payload = RecoveryRecipePayload(recipe.version, recipe.key, recipe.policy, recipe.checksum)
        val encoded = Cbor { encodeDefaults = true }.encodeToByteArray(payload)
        return "hdpwd-recipe:v1:${Base64Url.encode(encoded)}:${recipe.checksum}"
    }

    private fun deriveGeneratorKey(password: String, key: String, policy: PasswordPolicy): ByteArray {
        val material = buildString {
            append(DOMAIN).append('\u0000')
            append(FIXED_SALT).append('\u0000')
            append(password).append('\u0000')
            append(key).append('\u0000')
            append(policy.canonical())
        }.protocolBytes()
        return PortableSha256.digest(material)
    }

    private fun checksum(key: String, policy: PasswordPolicy): String =
        PortableSha256.digest((key + "\u0000" + policy.canonical()).protocolBytes())
            .take(4)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun buildPools(policy: PasswordPolicy): Pools {
        val upper = ('A'..'Z').joinToString("")
        val lower = ('a'..'z').joinToString("")
        val digits = ('0'..'9').joinToString("")
        val symbols = policy.symbols
            .filterNot { it in policy.excluded }
            .toList()
            .distinct()
            .joinToString("")
        val all = (upper + lower + digits + symbols).filterNot { it in policy.excluded }
        require(all.isNotEmpty()) { "可用字符集合不能为空" }
        val required = mutableListOf<Pair<String, Int>>()
        if (policy.requireUppercase) {
            required += upper.filterNot { it in policy.excluded } to policy.minimumUppercase
        }
        if (policy.requireLowercase) {
            required += lower.filterNot { it in policy.excluded } to policy.minimumLowercase
        }
        if (policy.requireDigits) {
            required += digits.filterNot { it in policy.excluded } to policy.minimumDigits
        }
        if (policy.requireSymbols) {
            required += symbols to policy.minimumSymbols
        }
        require(required.all { it.first.isNotEmpty() }) { "必选字符集合不能为空" }
        return Pools(all, required)
    }

    private data class Pools(
        val all: String,
        val required: List<Pair<String, Int>>,
    )

    private class ByteStream(
        private val key: ByteArray,
        private val context: ByteArray,
    ) {
        private var counter = 0
        private var buffer = ByteArray(0)
        private var position = 0

        /**
         * 使用拒绝采样从字符集合取值，避免非二次幂集合的模偏差。
         */
        fun nextFrom(pool: String): Char {
            require(pool.isNotEmpty()) { "字符集合不能为空" }
            val bound = 256 - (256 % pool.length)
            while (true) {
                val candidate = nextByte()
                val unsigned = candidate.toInt() and 0xff
                if (unsigned < bound) return pool[unsigned % pool.length]
            }
        }

        /**
         * 产生 [bound] 范围内的确定性整数。
         */
        fun nextInt(bound: Int): Int {
            require(bound > 0)
            val pool = (0 until bound).joinToString("") { ('\u0000'.code + it).toChar().toString() }
            return nextFrom(pool)
                .code
                .coerceAtMost(bound - 1)
        }

        private fun nextByte(): Byte {
            if (position >= buffer.size) {
                buffer = PortableHmacSha256.mac(
                    key,
                    context + byteArrayOf(
                        (counter ushr 24).toByte(),
                        (counter ushr 16).toByte(),
                        (counter ushr 8).toByte(),
                        counter.toByte(),
                    ),
                )
                counter++
                position = 0
            }
            return buffer[position++]
        }
    }
}

/**
 * 恢复配方的稳定 CBOR 载荷。
 */
@Serializable
private data class RecoveryRecipePayload(
    val version: Int,
    val key: String,
    val policy: PasswordPolicy,
    val checksum: String,
)

/**
 * 恢复配方的公开字段。
 */
data class RecoveryRecipe(
    val version: Int,
    val key: String,
    val policy: PasswordPolicy,
    val checksum: String,
) {
    /**
     * 返回可复制的恢复配方字符串。
     */
    fun encode(): String = PasswordGenerator.encodeRecipe(this)
}

/**
 * 无平台依赖的 Base64URL 编码器。
 */
private object Base64Url {
    private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /**
     * 编码为无填充 Base64URL。
     */
    fun encode(bytes: ByteArray): String {
        val output = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index < bytes.size) {
            val remaining = bytes.size - index
            val first = bytes[index++].toInt() and 0xff
            val hasSecond = remaining > 1
            val hasThird = remaining > 2
            val second = if (hasSecond) bytes[index++].toInt() and 0xff else 0
            val third = if (hasThird) bytes[index++].toInt() and 0xff else 0
            output.append(alphabet[first ushr 2])
            output.append(alphabet[(first and 3) shl 4 or (second ushr 4)])
            if (hasSecond) output.append(alphabet[(second and 15) shl 2 or (third ushr 6)])
            if (hasThird) output.append(alphabet[third and 63])
        }
        return output.toString()
    }

    /**
     * 解码无填充 Base64URL。
     */
    fun decode(text: String): ByteArray {
        val clean = text.trim()
        val output = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        clean.forEach { char ->
            val value = alphabet.indexOf(char)
            require(value >= 0) { "Base64URL 字符无效" }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output += (buffer ushr bits and 0xff).toByte()
            }
        }
        return output.toByteArray()
    }
}
