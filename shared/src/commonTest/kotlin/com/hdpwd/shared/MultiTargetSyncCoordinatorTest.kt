package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.sync.FieldChange
import com.hdpwd.shared.sync.HybridLogicalClock
import com.hdpwd.shared.sync.MultiTargetSyncCoordinator
import com.hdpwd.shared.sync.S3ObjectStore
import com.hdpwd.shared.sync.SyncDelta
import com.hdpwd.shared.sync.SyncEvent
import com.hdpwd.shared.sync.SyncOperation
import com.hdpwd.shared.sync.SyncTargetStore
import com.hdpwd.shared.storage.vaultJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证从一个 S3 副本发现事件后传播到其他副本。
 */
class MultiTargetSyncCoordinatorTest {
    /**
     * A 副本的新事件必须最终写入 B 副本。
     */
    @Test
    fun propagatesMissingEventToOtherTarget() = runTest {
        val event = SyncEvent(
            eventId = EntityId("event"),
            deviceId = EntityId("device"),
            deviceSequence = 1,
            entityId = EntityId("entry"),
            operation = SyncOperation.UPDATE,
            changes = listOf(FieldChange("title", "new")),
            clock = HybridLogicalClock(1, 0, EntityId("device")),
        )
        val a = MemoryS3Store()
        val b = MemoryS3Store()
        val c = MemoryS3Store()
        a.put(
            "deltas/device/1.dat",
            vaultJson.encodeToString(SyncDelta(EntityId("device"), 1, listOf(event))).encodeToByteArray(),
        )
        val targetA = SyncTargetStore(SyncTarget(EntityId("a"), "s3", "https://a", "a", "r", true, true), a)
        val targetB = SyncTargetStore(SyncTarget(EntityId("b"), "s3", "https://b", "b", "r", true, true), b)
        val targetC = SyncTargetStore(SyncTarget(EntityId("c"), "s3", "https://c", "c", "r", true, true), c)
        MultiTargetSyncCoordinator().synchronize(
            vaultId = EntityId("vault"),
            vault = VaultState(EntityId("vault")),
            targets = listOf(targetA, targetB, targetC),
            appliedEventIds = emptySet(),
            decode = { vaultJson.decodeFromString(it.decodeToString()) },
            encode = { vaultJson.encodeToString(SyncDelta(it.deviceId, it.deviceSequence, listOf(it))).encodeToByteArray() },
        )
        assertTrue(b.objects.keys.any { it.contains("/device/1.dat") })
        assertTrue(c.objects.keys.any { it.contains("/device/1.dat") })
    }
}

/**
 * 多副本协调器测试用的内存对象存储。
 */
private class MemoryS3Store : S3ObjectStore {
    val objects = mutableMapOf<String, ByteArray>()
    override suspend fun list(prefix: String): List<String> = objects.keys.filter { it.startsWith(prefix) }
    override suspend fun get(path: String): ByteArray = objects[path] ?: error("missing")
    override suspend fun put(path: String, content: ByteArray) {
        objects[path] = content
    }
}
