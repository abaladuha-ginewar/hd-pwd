package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.sync.S3ObjectStore
import com.hdpwd.shared.sync.S3TargetService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * 验证 S3 配置编辑、TLS 约束和单目标状态隔离。
 */
class S3TargetServiceTest {
    /**
     * 非 TLS 生产端点必须拒绝，连接失败只影响当前目标。
     */
    @Test
    fun validatesEndpointAndStatus() = runTest {
        val service = S3TargetService()
        val invalid = target("invalid", "http://remote.example")
        assertFails { service.add(emptyList(), invalid) }
        val local = service.add(emptyList(), target("local", "http://localhost:9000")).single()
        assertEquals(true, local.confirmed)
        assertEquals(true, local.enabled)
        assertEquals(SyncStatus.PENDING, local.status)
        val failed = service.testConnection(local, FailingS3Store())
        assertEquals(SyncStatus.FAILED, failed.status)
    }

    private fun target(id: String, endpoint: String) = SyncTarget(
        id = EntityId(id),
        provider = "s3",
        endpoint = endpoint,
        bucket = "hdpwd-test",
        region = "us-east-1",
        accessKeyId = "AKIAEXAMPLEKEY",
        encryptedCredentialsHex = "00ff",
        credentialsSaltHex = "1122",
    )
}

/**
 * 连接测试失败用的对象存储。
 */
private class FailingS3Store : S3ObjectStore {
    override suspend fun list(prefix: String): List<String> = error("connection")
    override suspend fun get(path: String): ByteArray = error("connection")
    override suspend fun put(path: String, content: ByteArray) = error("connection")
}
