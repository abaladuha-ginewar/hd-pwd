package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.VaultState
import kotlinx.serialization.Serializable

/**
 * 多副本同步事件的操作类型。
 */
@Serializable
enum class SyncOperation {
    CREATE,
    UPDATE,
    MOVE,
    DELETE,
}

/**
 * 单个字段的变更记录。
 */
@Serializable
data class FieldChange(
    val field: String,
    val value: String?,
)

/**
 * 兼容离线设备的逻辑时钟。
 */
@Serializable
data class HybridLogicalClock(
    val physicalMillis: Long,
    val logical: Int,
    val deviceId: EntityId,
) : Comparable<HybridLogicalClock> {
    /**
     * 为同一字段冲突提供确定性排序。
     */
    override fun compareTo(other: HybridLogicalClock): Int =
        compareValuesBy(this, other, HybridLogicalClock::physicalMillis, HybridLogicalClock::logical)
            .takeIf { it != 0 }
            ?: deviceId.value.compareTo(other.deviceId.value)
}

/**
 * 加密增量中的事件信封。
 */
@Serializable
data class SyncEvent(
    val eventId: EntityId,
    val deviceId: EntityId,
    val deviceSequence: Long,
    val entityId: EntityId,
    val operation: SyncOperation,
    val changes: List<FieldChange> = emptyList(),
    val clock: HybridLogicalClock,
    val transactionId: EntityId? = null,
)

/**
 * 增量包的非敏感协议元数据和事件载荷。
 */
@Serializable
data class SyncDelta(
    val deviceId: EntityId,
    val sequence: Long,
    val events: List<SyncEvent>,
)

/**
 * 按事件身份去重并应用删除优先策略的本地合并器。
 */
class SyncMerger {
    /**
     * 将未知事件合并到本地状态，返回新状态及新增事件身份。
     */
    fun merge(
        vault: VaultState,
        events: List<SyncEvent>,
        appliedEventIds: Set<EntityId>,
    ): MergeResult {
        var state = vault
        val applied = appliedEventIds.toMutableSet()
        val conflicts = state.conflicts.toMutableList()
        events
            .filterNot { it.eventId in applied }
            .sortedWith(compareBy<SyncEvent> { it.clock }.thenBy { it.eventId.value })
            .forEach { event ->
                state = applyEvent(state, event, conflicts)
                applied += event.eventId
            }
        return MergeResult(state.copy(conflicts = conflicts), applied)
    }

    private fun applyEvent(
        vault: VaultState,
        event: SyncEvent,
        conflicts: MutableList<com.hdpwd.shared.domain.Conflict>,
    ): VaultState {
        if (event.operation == SyncOperation.DELETE) {
            val tombstone = com.hdpwd.shared.domain.Tombstone(
                entityId = event.entityId,
                deletedAt = event.clock.physicalMillis,
                transactionId = event.transactionId,
                revision = event.deviceSequence,
            )
            return vault.copy(
                folders = vault.folders.filterNot { it.id == event.entityId },
                entries = vault.entries.filterNot { it.id == event.entityId },
                tombstones = (vault.tombstones + tombstone).distinctBy { it.entityId },
            )
        }
        val entry = vault.entries.firstOrNull { it.id == event.entityId } ?: return vault
        var updated = entry
        event.changes.forEach { change ->
            val oldValue = fieldValue(updated, change.field)
            if (oldValue != null && change.value != null && oldValue != change.value) {
                conflicts += com.hdpwd.shared.domain.Conflict(
                    id = EntityId("${event.eventId.value}-${change.field}"),
                    entityId = event.entityId,
                    field = change.field,
                    winner = change.value,
                    candidate = oldValue,
                    createdAt = event.clock.physicalMillis,
                )
            }
            if (change.field == "key" && change.value != null) {
                val conflict = vault.entries.any { it.id != entry.id && it.key == change.value }
                if (conflict) {
                    conflicts += com.hdpwd.shared.domain.Conflict(
                        id = EntityId("${event.eventId.value}-key"),
                        entityId = event.entityId,
                        field = "key",
                        winner = entry.key,
                        candidate = change.value,
                        createdAt = event.clock.physicalMillis,
                    )
                } else {
                    updated = updated.copy(key = change.value)
                }
            }
            if (change.field == "title" && change.value != null) updated = updated.copy(title = change.value)
            if (change.field == "colorHex" && change.value != null) {
                updated = updated.copy(colorHex = change.value)
            }
            if (change.field == "parentId" && change.value != null) {
                updated = updated.copy(parentId = EntityId(change.value))
            }
        }
        return vault.copy(entries = vault.entries.map { if (it.id == updated.id) updated else it })
    }

    private fun fieldValue(entry: PasswordEntry, field: String): String? = when (field) {
        "key" -> entry.key
        "title" -> entry.title
        "parentId" -> entry.parentId?.value
        "colorHex" -> entry.colorHex
        else -> null
    }
}

/**
 * 合并结果，供本地存储和多 S3 传播共同消费。
 */
data class MergeResult(
    val vault: VaultState,
    val appliedEventIds: Set<EntityId>,
)
