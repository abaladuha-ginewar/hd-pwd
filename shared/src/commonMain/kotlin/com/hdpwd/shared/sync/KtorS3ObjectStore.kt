package com.hdpwd.shared.sync

import com.hdpwd.shared.crypto.PortableSha256
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.ContentType

/**
 * 使用 Ktor 执行基础 S3 对象操作的共享客户端。
 */
class KtorS3ObjectStore(
    private val client: HttpClient,
    private val endpoint: String,
    private val bucket: String,
    private val region: String,
    private val credentials: S3Credentials,
    private val clock: () -> String,
) : S3ObjectStore {
    private val normalizedEndpoint = endpoint.trimEnd('/')
    private val signer = S3SignatureV4Signer(region)

    init {
        require(
            normalizedEndpoint.startsWith("https://") ||
                normalizedEndpoint.startsWith("http://localhost") ||
                normalizedEndpoint.startsWith("http://127.0.0.1"),
        ) { "生产 S3 端点必须使用 TLS" }
    }

    /**
     * 列出指定前缀下的对象路径。
     */
    override suspend fun list(prefix: String): List<String> {
        val response = client.get(urlFor("")) {
            val signature = sign(S3HttpMethod.GET, "/$bucket", listOf(S3QueryParameter("prefix", prefix)))
            applySignature(signature)
        }
        require(response.status.isSuccess()) { "S3 列举失败: ${response.status.value}" }
        return KEY_PATTERN.findAll(response.bodyAsText())
            .map { decodeXml(it.groupValues[1]) }
            .toList()
    }

    /**
     * 下载指定对象的密文。
     */
    override suspend fun get(path: String): ByteArray {
        val objectPath = objectPath(path)
        val response = client.get(urlFor(path)) {
            applySignature(sign(S3HttpMethod.GET, objectPath))
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
        val objectPath = objectPath(path)
        val payloadHash = sha256Hex(content)
        val response = client.put(urlFor(path)) {
            contentType(ContentType.Application.OctetStream)
            if (etag != null) headers.append("If-Match", etag)
            setBody(content)
            applySignature(
                sign(
                    method = S3HttpMethod.PUT,
                    path = objectPath,
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
        val response = client.head(urlFor("")) {
            applySignature(sign(S3HttpMethod.HEAD, "/$bucket"))
        }
        require(response.status.isSuccess()) { "S3 连接失败: ${response.status.value}" }
    }

    /**
     * 删除对象，仅用于同步清理而不是删除用户远端 Vault。
     */
    suspend fun deleteObject(path: String) {
        val response = client.delete(urlFor(path)) {
            applySignature(sign(S3HttpMethod.DELETE, objectPath(path)))
        }
        require(response.status.isSuccess()) { "S3 删除对象失败: ${response.status.value}" }
    }

    private fun sign(
        method: S3HttpMethod,
        path: String,
        query: List<S3QueryParameter> = emptyList(),
        headers: Map<String, String> = emptyMap(),
        payloadHash: String = EMPTY_SHA256,
    ) = signer.sign(
        request = S3SigningRequest(
            method = method,
            host = host,
            path = path,
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
        signature.asHeaders().forEach { (name, value) ->
            headers.append(name, value)
        }
    }

    private fun objectPath(path: String): String =
        "/$bucket/${path.trimStart('/')}"

    private fun urlFor(path: String): String =
        "$normalizedEndpoint/$bucket/${path.trimStart('/')}".trimEnd('/')

    private val host: String
        get() = normalizedEndpoint.substringAfter("://").substringBefore('/')

    private fun sha256Hex(content: ByteArray): String =
        PortableSha256.digest(content).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun decodeXml(value: String): String =
        value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    private companion object {
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val KEY_PATTERN = Regex("<Key>(.*?)</Key>")
    }
}
