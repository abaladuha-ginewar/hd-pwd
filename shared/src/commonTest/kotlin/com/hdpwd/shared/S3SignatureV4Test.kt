package com.hdpwd.shared

import com.hdpwd.shared.sync.S3Credentials
import com.hdpwd.shared.sync.S3HttpMethod
import com.hdpwd.shared.sync.S3QueryParameter
import com.hdpwd.shared.sync.S3SignatureV4Signer
import com.hdpwd.shared.sync.S3SigningRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证共享 S3 Signature V4 纯计算模块的协议输出和敏感数据边界。
 */
class S3SignatureV4Test {
    /**
     * 使用 AWS 官方 GET Object 向量验证完整 Authorization 结果。
     */
    @Test
    fun matchesAwsGetObjectVector() {
        val credentials = S3Credentials(
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY".encodeToByteArray(),
        )
        try {
            val result = S3SignatureV4Signer("us-east-1").sign(
                request = S3SigningRequest(
                    method = S3HttpMethod.GET,
                    host = "examplebucket.s3.amazonaws.com",
                    path = "/test.txt",
                    headers = mapOf("Range" to "bytes=0-9"),
                    payloadHash = EMPTY_SHA256,
                    amzDate = "20130524T000000Z",
                ),
                credentials = credentials,
            )

            assertEquals(
                "AWS4-HMAC-SHA256 " +
                    "Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                    "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                    "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
                result.authorization,
            )
            assertEquals("20130524T000000Z", result.amzDate)
            assertEquals(EMPTY_SHA256, result.contentSha256)
            assertEquals(
                mapOf(
                    "Authorization" to result.authorization,
                    "x-amz-date" to "20130524T000000Z",
                    "x-amz-content-sha256" to EMPTY_SHA256,
                ),
                result.asHeaders(),
            )
        } finally {
            credentials.clear()
        }
    }

    /**
     * 验证 GET、PUT、HEAD、查询排序和路径编码均由同一共享签名入口处理。
     */
    @Test
    fun supportsMethodsAndCanonicalQuery() {
        val credentials = S3Credentials("access", "secret".encodeToByteArray())
        try {
            val base = S3SigningRequest(
                method = S3HttpMethod.GET,
                host = "s3.example.test:9000",
                path = "vault/a b/%2F",
                query = listOf(
                    S3QueryParameter("prefix", "z"),
                    S3QueryParameter("continuation-token", "a/b"),
                    S3QueryParameter("prefix", "a"),
                ),
                headers = mapOf("Content-Type" to "application/octet-stream"),
                payloadHash = EMPTY_SHA256,
                amzDate = "20260815T141500Z",
            )
            val get = S3SignatureV4Signer("cn-test-1").sign(base, credentials)
            val reordered = S3SignatureV4Signer("cn-test-1").sign(
                base.copy(
                    query = base.query.reversed(),
                ),
                credentials,
            )
            assertEquals(get.authorization, reordered.authorization)
            assertEquals(
                "content-type;host;x-amz-content-sha256;x-amz-date",
                get.signedHeaders,
            )
            assertTrue(get.authorization.contains("Credential=access/20260815/cn-test-1/s3/aws4_request"))
            assertTrue(get.asHeaders().containsKey("Authorization"))

            listOf(S3HttpMethod.PUT, S3HttpMethod.HEAD).forEach { method ->
                val result = S3SignatureV4Signer("cn-test-1").sign(
                    base.copy(method = method),
                    credentials,
                )
                assertTrue(result.authorization.isNotEmpty())
                assertEquals(EMPTY_SHA256, result.asHeaders()["x-amz-content-sha256"])
            }
        } finally {
            credentials.clear()
        }
    }

    /**
     * 验证凭据和认证结果的默认文本表示不会记录 Secret 或完整认证头。
     */
    @Test
    fun redactsSecretFromTextRepresentations() {
        val secret = "temporary-secret"
        val credentials = S3Credentials("access", secret.encodeToByteArray())
        try {
            val result = S3SignatureV4Signer("us-east-1").sign(
                S3SigningRequest(
                    method = S3HttpMethod.HEAD,
                    host = "example.test",
                    path = "/object",
                    payloadHash = "UNSIGNED-PAYLOAD",
                    amzDate = "20260815T141500Z",
                ),
                credentials,
            )
            assertFalse(credentials.toString().contains(secret))
            assertFalse(result.toString().contains(result.authorization))
        } finally {
            credentials.clear()
        }
    }

    private companion object {
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
