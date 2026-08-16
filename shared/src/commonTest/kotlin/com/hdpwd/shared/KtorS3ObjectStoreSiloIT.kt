package com.hdpwd.shared

import com.hdpwd.shared.platform.platformHttpClient
import com.hdpwd.shared.sync.KtorS3ObjectStore
import com.hdpwd.shared.sync.S3Credentials
import com.hdpwd.shared.sync.awsAmzDate
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 对本地 Silo 的真实 HTTP 冒烟（需 compose 已启动 silo，并从可解析主机访问）。
 *
 * 默认忽略；在开发机验证时去掉 [@Ignore] 并保证 endpoint 可达。
 */
class KtorS3ObjectStoreSiloIT {
    @Test
    @Ignore
    fun listObjectsV2AgainstSilo() = runBlocking {
        val credentials = S3Credentials("hdpwd-test", "hdpwd-test-password".encodeToByteArray())
        try {
            val store = KtorS3ObjectStore(
                client = platformHttpClient(),
                endpoint = "http://host.docker.internal:9000",
                bucket = "hdpwd-s3-a",
                region = "us-east-1",
                credentials = credentials,
                clock = ::awsAmzDate,
                forcePathStyle = true,
            )
            val keys = store.list("")
            assertTrue(keys.isEmpty() || keys.all { it.isNotBlank() })
        } finally {
            credentials.clear()
        }
    }
}
