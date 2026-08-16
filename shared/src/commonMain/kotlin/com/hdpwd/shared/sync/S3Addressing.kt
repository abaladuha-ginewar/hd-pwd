package com.hdpwd.shared.sync

/**
 * 一次 S3 HTTP 请求的寻址结果：实际 URL、签名 Host 与规范路径必须一致。
 */
data class S3RequestTarget(
    val url: String,
    val host: String,
    val canonicalPath: String,
)

/**
 * 按 Path-Style 或 Virtual-Hosted 解析对象请求目标。
 *
 * Path-Style：`https://endpoint/bucket/key`，Host 为 endpoint 主机。
 * Virtual-Hosted：`https://bucket.endpointHost/key`，Host 为 `bucket.endpointHost`。
 */
fun resolveS3RequestTarget(
    endpoint: String,
    bucket: String,
    objectKey: String = "",
    forcePathStyle: Boolean = true,
    queryString: String = "",
): S3RequestTarget {
    require(bucket.isNotBlank()) { "S3 bucket 不能为空" }
    val normalizedEndpoint = endpoint.trim().trimEnd('/')
    val scheme = when {
        normalizedEndpoint.startsWith("https://", ignoreCase = true) -> "https"
        normalizedEndpoint.startsWith("http://", ignoreCase = true) -> "http"
        else -> error("S3 endpoint 必须包含 http(s) 协议")
    }
    val endpointHost = normalizedEndpoint.substringAfter("://").substringBefore('/')
    require(endpointHost.isNotBlank()) { "S3 endpoint 主机无效" }

    val key = objectKey.trimStart('/')
    val (urlBase, host, canonicalPath) = if (forcePathStyle) {
        val base = if (key.isEmpty()) {
            "$normalizedEndpoint/$bucket"
        } else {
            "$normalizedEndpoint/$bucket/$key"
        }
        Triple(base, endpointHost, if (key.isEmpty()) "/$bucket" else "/$bucket/$key")
    } else {
        val base = if (key.isEmpty()) {
            "$scheme://$bucket.$endpointHost"
        } else {
            "$scheme://$bucket.$endpointHost/$key"
        }
        Triple(base, "$bucket.$endpointHost", if (key.isEmpty()) "/" else "/$key")
    }
    val url = if (queryString.isBlank()) urlBase else "$urlBase?$queryString"
    return S3RequestTarget(url = url, host = host, canonicalPath = canonicalPath)
}
