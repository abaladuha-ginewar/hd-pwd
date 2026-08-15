package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.VaultState
import kotlinx.serialization.Serializable

/**
 * 加密快照中记录的每设备事件水位。
 */
@Serializable
data class DeviceWatermark(
    val deviceId: EntityId,
    val sequence: Long,
)

/**
 * 包含状态、墓碑、冲突和设备水位的同步快照。
 */
@Serializable
data class SyncSnapshot(
    val snapshotId: EntityId,
    val createdAt: Long,
    val vault: VaultState,
    val appliedEventIds: Set<EntityId>,
    val watermarks: List<DeviceWatermark>,
)

/**
 * 生成快照并判断增量是否已被快照覆盖。
 */
class SyncSnapshotService {
    /**
     * 创建当前合并状态快照。
     */
    fun create(
        snapshotId: EntityId,
        nowMillis: Long,
        vault: VaultState,
        appliedEventIds: Set<EntityId>,
        watermarks: Map<EntityId, Long>,
    ): SyncSnapshot = SyncSnapshot(
        snapshotId = snapshotId,
        createdAt = nowMillis,
        vault = vault,
        appliedEventIds = appliedEventIds,
        watermarks = watermarks.entries
            .map { DeviceWatermark(it.key, it.value) }
            .sortedBy { it.deviceId.value },
    )

    /**
     * 判断事件是否已被指定快照的设备水位覆盖。
     */
    fun isCovered(snapshot: SyncSnapshot, event: SyncEvent): Boolean =
        snapshot.watermarks.firstOrNull { it.deviceId == event.deviceId }?.sequence
            ?.let { event.deviceSequence <= it } == true
}
