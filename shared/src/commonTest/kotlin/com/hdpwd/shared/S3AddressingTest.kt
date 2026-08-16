package com.hdpwd.shared

import com.hdpwd.shared.sync.S3ProviderPreset
import com.hdpwd.shared.sync.S3QueryParameter
import com.hdpwd.shared.sync.S3SignatureV4Signer
import com.hdpwd.shared.sync.resolveS3RequestTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 Path-Style / Virtual-Hosted 寻址与查询串编码一致。
 */
class S3AddressingTest {
    @Test
    fun cstcloudCapsuleUsesPathStyleUrlAndHost() {
        val target = resolveS3RequestTarget(
            endpoint = "https://s3.cstcloud.cn",
            bucket = "my-space",
            objectKey = "family/vault.dat",
            forcePathStyle = true,
        )
        assertEquals("https://s3.cstcloud.cn/my-space/family/vault.dat", target.url)
        assertEquals("s3.cstcloud.cn", target.host)
        assertEquals("/my-space/family/vault.dat", target.canonicalPath)
        assertTrue(S3ProviderPreset.CSTCLOUD_CAPSULE.forcePathStyle)
    }

    @Test
    fun listObjectsV2QueryIsAppendedToPathStyleBucketUrl() {
        val query = listOf(
            S3QueryParameter("list-type", "2"),
            S3QueryParameter("prefix", "family/"),
        )
        val queryString = S3SignatureV4Signer("us-east-1").encodeQueryString(query)
        val target = resolveS3RequestTarget(
            endpoint = "https://s3.cstcloud.cn",
            bucket = "my-space",
            objectKey = "",
            forcePathStyle = true,
            queryString = queryString,
        )
        assertEquals("https://s3.cstcloud.cn/my-space?list-type=2&prefix=family%2F", target.url)
        assertEquals("/my-space", target.canonicalPath)
        assertEquals("s3.cstcloud.cn", target.host)
    }

    @Test
    fun virtualHostedUsesBucketSubdomain() {
        val target = resolveS3RequestTarget(
            endpoint = "https://oss-cn-hangzhou.aliyuncs.com",
            bucket = "demo",
            objectKey = "a.txt",
            forcePathStyle = false,
        )
        assertEquals("https://demo.oss-cn-hangzhou.aliyuncs.com/a.txt", target.url)
        assertEquals("demo.oss-cn-hangzhou.aliyuncs.com", target.host)
        assertEquals("/a.txt", target.canonicalPath)
    }
}
