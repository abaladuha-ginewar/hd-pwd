package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.sync.HybridLogicalClock
import com.hdpwd.shared.sync.SyncEvent
import com.hdpwd.shared.sync.SyncOperation
import com.hdpwd.shared.sync.SyncSnapshotService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证快照设备水位和增量安全回收判断。
 */
class SyncSnapshotTest {
    /**
     * 水位内事件可回收，水位外事件必须保留。
     */
    @Test
    fun snapshotCoversDeviceSequence() {
        val device = EntityId("device")
        val snapshot = SyncSnapshotService().create(
            snapshotId = EntityId("snapshot"),
            nowMillis = 10,
            vault = VaultState(EntityId("vault")),
            appliedEventIds = emptySet(),
            watermarks = mapOf(device to 3),
        )
        val covered = event(device, 3)
        val pending = event(device, 4)
        assertTrue(SyncSnapshotService().isCovered(snapshot, covered))
        assertFalse(SyncSnapshotService().isCovered(snapshot, pending))
    }

    private fun event(device: EntityId, sequence: Long) = SyncEvent(
        eventId = EntityId("event-$sequence"),
        deviceId = device,
        deviceSequence = sequence,
        entityId = EntityId("entry"),
        operation = SyncOperation.UPDATE,
        clock = HybridLogicalClock(sequence, 0, device),
    )
}
