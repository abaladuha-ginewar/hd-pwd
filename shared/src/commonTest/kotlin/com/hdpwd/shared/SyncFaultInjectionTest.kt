package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.sync.S3ObjectStore
import com.hdpwd.shared.sync.SyncRetryExecutor
import com.hdpwd.shared.sync.RetryPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 同步故障注入：临时失败重试、永久失败隔离、部分目标失败不影响其他目标状态模型。
 */
class SyncFaultInjectionTest {
    @Test
    fun transientStoreFailureEventuallySucceeds() = runTest {
        val store = FlakyS3Store(failuresBeforeSuccess = 2)
        var attempts = 0
        val keys = SyncRetryExecutor { 0 }.execute(
            RetryPolicy(maxAttempts = 4, initialDelayMillis = 1, maxDelayMillis = 2),
        ) {
            attempts++
            store.list("")
        }
        assertTrue(keys.isEmpty())
        assertEquals(3, attempts)
    }

    @Test
    fun permanentFailureStopsAfterMaxAttempts() = runTest {
        val store = FlakyS3Store(failuresBeforeSuccess = 100)
        assertFailsWith<IllegalStateException> {
            SyncRetryExecutor { 0 }.execute(
                RetryPolicy(maxAttempts = 2, initialDelayMillis = 1, maxDelayMillis = 2),
            ) { store.list("") }
        }
    }

    @Test
    fun targetStatusIsolationModel() {
        val ok = SyncTarget(
            id = EntityId("a"),
            provider = "minio",
            endpoint = "http://localhost:9000",
            bucket = "a",
            region = "us-east-1",
            enabled = true,
            confirmed = true,
            status = SyncStatus.SUCCESS,
        )
        val failed = ok.copy(id = EntityId("b"), bucket = "b", status = SyncStatus.FAILED)
        val vault = VaultState(vaultId = EntityId("v"), syncTargets = listOf(ok, failed))
        assertEquals(1, vault.syncTargets.count { it.status == SyncStatus.SUCCESS })
        assertEquals(1, vault.syncTargets.count { it.status == SyncStatus.FAILED })
    }
}

private class FlakyS3Store(
    private val failuresBeforeSuccess: Int,
) : S3ObjectStore {
    private var attempts = 0

    override suspend fun list(prefix: String): List<String> {
        attempts++
        if (attempts <= failuresBeforeSuccess) error("injected-failure-$attempts")
        return emptyList()
    }

    override suspend fun get(path: String): ByteArray = error("unused")

    override suspend fun put(path: String, content: ByteArray) = error("unused")
}
