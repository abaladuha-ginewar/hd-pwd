package com.hdpwd.shared.sync

import com.hdpwd.shared.crypto.PortableSha256
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * 使用 Ktor 执行基础 S3 对象操作的共享客户端。
 *
 * [forcePathStyle] 为 true 时使用路径寻址（`endpoint/bucket/key`），中科院数据胶囊 / MinIO 等必须开启。
 * 读请求使用 `UNSIGNED-PAYLOAD`，与 AWS SDK / 多数兼容端对 SigV4 的默认行为一致。
 */
class KtorS3ObjectStore(
    private val client: HttpClient,
    private val endpoint: String,
    private val bucket: String,
    private val region: String,
    private val credentials: S3Credentials,
    private val clock: () -> String,
    private val forcePathStyle: Boolean = true,
) : S3ObjectStore {
    private val normalizedEndpoint = endpoint.trimEnd('/')
    private val signer = S3SignatureV4Signer(region)

    init {
        require(
            normalizedEndpoint.startsWith("https://") ||
                normalizedEndpoint.startsWith("http://localhost") ||
                normalizedEndpoint.startsWith("http://127.0.0.1") ||
                normalizedEndpoint.startsWith("http://host.docker.internal") ||
                normalizedEndpoint.startsWith("http://10.0.2.2"),
        ) { "生产 S3 端点必须使用 TLS" }
    }

    /**
     * 列出指定前缀下的对象路径（ListObjectsV2，`list-type=2`）。
     */
    override suspend fun list(prefix: String): List<String> {
        val query = buildList {
            add(S3QueryParameter("list-type", "2"))
            if (prefix.isNotEmpty()) add(S3QueryParameter("prefix", prefix))
        }
        val target = targetFor("", query)
        val response = client.get(Url(target.url)) {
            // 禁止自动压缩，避免未签名的 Accept-Encoding 干扰兼容端校验
            header(HttpHeaders.AcceptEncoding, "identity")
            applySignature(sign(S3HttpMethod.GET, target, query, payloadHash = UNSIGNED_PAYLOAD))
        }
        val body = response.bodyAsText()
        require(response.status.isSuccess()) {
            "S3 列举失败: ${response.status.value}${hintForStatus(response.status.value)}" +
                body.take(160).let { if (it.isBlank()) "" else " $it" }
        }
        return KEY_PATTERN.findAll(body)
            .map { decodeXml(it.groupValues[1]) }
            .toList()
    }

    /**
     * 下载指定对象的密文。
     */
    override suspend fun get(path: String): ByteArray {
        val target = targetFor(path)
        val response = client.get(Url(target.url)) {
            header(HttpHeaders.AcceptEncoding, "identity")
            applySignature(sign(S3HttpMethod.GET, target, payloadHash = UNSIGNED_PAYLOAD))
        }
        require(response.status.isSuccess()) { "S3 下载失败: ${response.status.value}" }
        return response.body()
    }

    /**
     * 上传指定对象的密文。
     */
    override suspend fun put(path: String, content: ByteArray) {
        putIfMatch(path, content, null)
    }

    /**
     * 使用 If-Match 对象版本执行条件写入，避免覆盖并发更新。
     */
    suspend fun putIfMatch(path: String, content: ByteArray, etag: String?) {
        val target = targetFor(path)
        val payloadHash = sha256Hex(content)
        val response = client.put(Url(target.url)) {
            header(HttpHeaders.AcceptEncoding, "identity")
            contentType(ContentType.Application.OctetStream)
            if (etag != null) headers.append("If-Match", etag)
            setBody(content)
            applySignature(
                sign(
                    method = S3HttpMethod.PUT,
                    target = target,
                    headers = buildMap {
                        put("content-type", "application/octet-stream")
                        if (etag != null) put("if-match", etag)
                    },
                    payloadHash = payloadHash,
                ),
            )
        }
        require(response.status.isSuccess()) { "S3 上传失败: ${response.status.value}" }
    }

    /**
     * 测试目标是否能访问当前 bucket。
     */
    suspend fun checkConnection() {
        val target = targetFor("")
        val response = client.head(Url(target.url)) {
            header(HttpHeaders.AcceptEncoding, "identity")
            applySignature(sign(S3HttpMethod.HEAD, target, payloadHash = UNSIGNED_PAYLOAD))
        }
        require(response.status.isSuccess()) { "S3 连接失败: ${response.status.value}" }
    }

    /**
     * 删除对象，仅用于同步清理而不是删除用户远端 Vault。
     */
    suspend fun deleteObject(path: String) {
        val target = targetFor(path)
        val response = client.delete(Url(target.url)) {
            header(HttpHeaders.AcceptEncoding, "identity")
            applySignature(sign(S3HttpMethod.DELETE, target, payloadHash = UNSIGNED_PAYLOAD))
        }
        require(response.status.isSuccess()) { "S3 删除对象失败: ${response.status.value}" }
    }

    private fun targetFor(
        objectKey: String,
        query: List<S3QueryParameter> = emptyList(),
    ): S3RequestTarget =
        resolveS3RequestTarget(
            endpoint = normalizedEndpoint,
            bucket = bucket,
            objectKey = objectKey,
            forcePathStyle = forcePathStyle,
            queryString = signer.encodeQueryString(query),
        )

    private fun sign(
        method: S3HttpMethod,
        target: S3RequestTarget,
        query: List<S3QueryParameter> = emptyList(),
        headers: Map<String, String> = emptyMap(),
        payloadHash: String = EMPTY_SHA256,
    ) = signer.sign(
        request = S3SigningRequest(
            method = method,
            host = target.host,
            path = target.canonicalPath,
            query = query,
            headers = headers,
            payloadHash = payloadHash,
            amzDate = clock(),
        ),
        credentials = credentials,
    )

    private fun io.ktor.client.request.HttpRequestBuilder.applySignature(
        signature: S3SignatureV4Result,
    ) {
        // Host 由 URL 决定，交由引擎写入，避免与 OkHttp 自动 Host 冲突
        signature.asHeaders().forEach { (name, value) ->
            headers[name] = value
        }
    }

    private fun sha256Hex(content: ByteArray): String =
        PortableSha256.digest(content).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun decodeXml(value: String): String =
        value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    private fun hintForStatus(code: Int): String = when (code) {
        401, 403 -> "（请核对密钥/桶名/Path-Style；并确认设备时间准确）"
        else -> ""
    }

    private companion object {
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
        val KEY_PATTERN = Regex("<Key>(.*?)</Key>")
    }
}
