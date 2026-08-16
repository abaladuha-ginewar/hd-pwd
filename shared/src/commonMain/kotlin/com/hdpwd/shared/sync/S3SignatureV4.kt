package com.hdpwd.shared.sync

import com.hdpwd.shared.crypto.PortableHmacSha256
import com.hdpwd.shared.crypto.PortableSha256
import com.hdpwd.shared.crypto.protocolBytes

/**
 * AWS S3 Signature V4 支持的 HTTP 方法。
 */
enum class S3HttpMethod {
    GET,
    PUT,
    HEAD,
    DELETE,
}

/**
 * 一个待签名的查询参数；使用列表以保留重复参数的协议语义。
 */
data class S3QueryParameter(
    val name: String,
    val value: String = "",
)

/**
 * 仅在签名操作期间使用的 S3 访问凭据。
 *
 * Secret 只保存在私有字节缓冲区中，类型和签名结果均不会通过
 * `toString` 暴露其内容；调用方应在操作结束后调用 [clear]。
 */
class S3Credentials(
    val accessKeyId: String,
    secretAccessKey: ByteArray,
) {
    private var secret: ByteArray? = secretAccessKey.copyOf()

    init {
        require(accessKeyId.isNotBlank()) { "S3 access key id 不能为空" }
        require(secretAccessKey.isNotEmpty()) { "S3 secret access key 不能为空" }
    }

    /**
     * 在不复制 Secret 的情况下执行一次签名所需的字节访问。
     */
    internal fun <T> useSecret(block: (ByteArray) -> T): T =
        block(secret ?: error("S3 凭据已清理"))

    /**
     * 覆盖并释放内存中的 Secret。
     */
    fun clear() {
        secret?.fill(0)
        secret = null
    }

    /**
     * 防止调试输出意外包含 Secret。
     */
    override fun toString(): String = "S3Credentials(accessKeyId=<redacted>, secret=<redacted>)"
}

/**
 * S3 Signature V4 的输入请求；对象路径由调用方显式提供，不从业务字段推导。
 */
data class S3SigningRequest(
    val method: S3HttpMethod,
    val host: String,
    val path: String,
    val query: List<S3QueryParameter> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val payloadHash: String,
    val amzDate: String,
)

/**
 * S3 Signature V4 生成的认证请求头。
 */
class S3SignatureV4Result internal constructor(
    val authorization: String,
    val amzDate: String,
    val contentSha256: String,
    val signedHeaders: String,
) {
    /**
     * 返回可直接加入 Ktor 请求的认证头集合。
     */
    fun asHeaders(): Map<String, String> = mapOf(
        "Authorization" to authorization,
        "x-amz-date" to amzDate,
        "x-amz-content-sha256" to contentSha256,
    )

    /**
     * 防止完整认证头被默认日志输出。
     */
    override fun toString(): String = "S3SignatureV4Result(<redacted>)"
}

/**
 * 共享的 AWS S3 Signature V4 请求签名器。
 *
 * 该基础模块只负责认证头计算，不执行网络请求、TLS 配置或对象路径拼接。
 */
class S3SignatureV4Signer(
    private val region: String,
) {
    private val service = "s3"

    init {
        require(region.isNotBlank()) { "S3 region 不能为空" }
    }

    /**
     * 按 Signature V4 规则编码查询串，供实际请求 URL 与签名使用同一字符串。
     */
    fun encodeQueryString(query: List<S3QueryParameter>): String = canonicalQuery(query)

    /**
     * 为 GET、PUT 或 HEAD 请求生成 Authorization、日期和 payload hash 认证头。
     */
    fun sign(
        request: S3SigningRequest,
        credentials: S3Credentials,
    ): S3SignatureV4Result {
        require(request.host.isNotBlank()) { "S3 host 不能为空" }
        require(request.path.isNotEmpty()) { "S3 path 不能为空" }
        require(request.amzDate.matches(AMZ_DATE_PATTERN)) { "x-amz-date 格式无效" }

        val payloadHash = normalizePayloadHash(request.payloadHash)
        val date = request.amzDate.substring(0, 8)
        val canonicalHeaders = canonicalHeaders(request, payloadHash)
        val signedHeaders = canonicalHeaders.keys.sorted().joinToString(";")
        val canonicalRequest = listOf(
            request.method.name,
            canonicalUri(request.path),
            canonicalQuery(request.query),
            canonicalHeaders.entries.joinToString("") { (name, value) -> "$name:$value\n" },
            signedHeaders,
            payloadHash,
        ).joinToString("\n")
        val scope = "$date/$region/$service/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            request.amzDate,
            scope,
            hex(PortableSha256.digest(canonicalRequest.protocolBytes())),
        ).joinToString("\n")
        val signature = credentials.useSecret { secret ->
            signature(secret, date, stringToSign)
        }

        return S3SignatureV4Result(
            authorization = "AWS4-HMAC-SHA256 Credential=${credentials.accessKeyId}/$scope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature",
            amzDate = request.amzDate,
            contentSha256 = payloadHash,
            signedHeaders = signedHeaders,
        )
    }

    /**
     * 构造规范化的签名头映射，保证 host、日期和 payload hash 由签名输入统一控制。
     */
    private fun canonicalHeaders(
        request: S3SigningRequest,
        payloadHash: String,
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        request.headers.forEach { (name, value) ->
            val normalizedName = name.trim().lowercase()
            require(normalizedName.isNotEmpty()) { "S3 header 名称不能为空" }
            require(normalizedName != "authorization") { "Authorization 必须由签名器生成" }
            if (normalizedName != "host" &&
                normalizedName != "x-amz-date" &&
                normalizedName != "x-amz-content-sha256"
            ) {
                result[normalizedName] = normalizeHeaderValue(value)
            }
        }
        result["host"] = normalizeHeaderValue(request.host).lowercase()
        result["x-amz-content-sha256"] = payloadHash
        result["x-amz-date"] = request.amzDate
        return result.entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    /**
     * 生成 S3 不做路径归一化的规范 URI。
     */
    private fun canonicalUri(path: String): String {
        val withLeadingSlash = if (path.startsWith('/')) path else "/$path"
        return awsEncode(withLeadingSlash, encodeSlash = false)
    }

    /**
     * 生成按编码后名称和值排序的规范查询字符串。
     */
    private fun canonicalQuery(query: List<S3QueryParameter>): String =
        query
            .map { awsEncode(it.name, true) to awsEncode(it.value, true) }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { (name, value) -> "$name=$value" }

    /**
     * 按 AWS 规则编码 UTF-8 字节，只保留未保留字符。
     */
    private fun awsEncode(value: String, encodeSlash: Boolean): String {
        val output = StringBuilder()
        value.protocolBytes().forEach { byte ->
            val code = byte.toInt() and 0xff
            val isUnreserved = code in 'A'.code..'Z'.code ||
                code in 'a'.code..'z'.code ||
                code in '0'.code..'9'.code ||
                code == '-'.code ||
                code == '.'.code ||
                code == '_'.code ||
                code == '~'.code
            if (isUnreserved || (!encodeSlash && code == '/'.code)) {
                output.append(code.toChar())
            } else {
                output.append('%')
                output.append(HEX[(code ushr 4) and 0x0f])
                output.append(HEX[code and 0x0f])
            }
        }
        return output.toString()
    }

    /**
     * 合并连续空白并去除首尾空白，符合 AWS 规范头值要求。
     */
    private fun normalizeHeaderValue(value: String): String =
        value.trim().replace(WHITESPACE_PATTERN, " ")

    /**
     * 校验并规范化 SHA-256 payload hash。
     */
    private fun normalizePayloadHash(value: String): String {
        if (value == "UNSIGNED-PAYLOAD") return value
        require(value.matches(SHA256_PATTERN)) {
            "payload hash 必须是 64 位十六进制 SHA-256 值"
        }
        return value.lowercase()
    }

    /**
     * 派生日期、区域、服务和终结范围限定的 AWS 签名，并及时覆盖中间密钥。
     */
    private fun signature(
        secret: ByteArray,
        date: String,
        stringToSign: String,
    ): String {
        val dateKey = PortableHmacSha256.mac(("AWS4".protocolBytes() + secret), date.protocolBytes())
        try {
            val regionKey = PortableHmacSha256.mac(dateKey, region.protocolBytes())
            try {
                val serviceKey = PortableHmacSha256.mac(regionKey, service.protocolBytes())
                try {
                    val signingKey = PortableHmacSha256.mac(serviceKey, "aws4_request".protocolBytes())
                    try {
                        val signature = PortableHmacSha256.mac(signingKey, stringToSign.protocolBytes())
                        try {
                            return hex(signature)
                        } finally {
                            signature.fill(0)
                        }
                    } finally {
                        signingKey.fill(0)
                    }
                } finally {
                    serviceKey.fill(0)
                }
            } finally {
                regionKey.fill(0)
            }
        } finally {
            dateKey.fill(0)
        }
    }

    /**
     * 将摘要字节转换为小写十六进制文本。
     */
    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            "${LOWER_HEX[value ushr 4]}${LOWER_HEX[value and 0x0f]}"
        }

    /**
     * Signature V4 的固定协议常量和输入校验模式。
     */
    private companion object {
        const val HEX = "0123456789ABCDEF"
        const val LOWER_HEX = "0123456789abcdef"
        val AMZ_DATE_PATTERN = Regex("\\d{8}T\\d{6}Z")
        val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
        val WHITESPACE_PATTERN = Regex("\\s+")
    }
}
