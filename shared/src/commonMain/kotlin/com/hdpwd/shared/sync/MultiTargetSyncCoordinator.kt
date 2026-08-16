package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.domain.VaultState

/**
 * 已确认 S3 目标及其对象存储端口。
 */
data class SyncTargetStore(
    val target: SyncTarget,
    val store: S3ObjectStore,
)

/**
 * 多 S3 副本拉取、合并和缺失事件传播协调器。
 */
class MultiTargetSyncCoordinator(
    private val pathFactory: (vaultId: EntityId, deviceId: EntityId, sequence: Long, objectPrefix: String) -> String =
        S3ObjectPaths::delta,
) {
    /**
     * 从全部目标收集未知事件，再向全部目标补齐缺失事件。
     */
    suspend fun synchronize(
        vaultId: EntityId,
        vault: VaultState,
        targets: List<SyncTargetStore>,
        appliedEventIds: Set<EntityId>,
        decode: (ByteArray) -> SyncDelta,
        encode: (SyncEvent) -> ByteArray,
    ): MultiTargetSyncResult {
        val errors = mutableMapOf<EntityId, String>()
        val discovered = mutableListOf<SyncEvent>()
        targets.filter { it.target.enabled && it.target.confirmed }.forEach { target ->
            try {
                val deltaPrefix = S3ObjectPaths.joinPrefix(
                    target.target.objectPrefix,
                    "deltas/",
                )
                target.store.list(deltaPrefix).forEach { path ->
                    val delta = decode(target.store.get(path))
                    discovered += delta.events
                }
            } catch (failure: Throwable) {
                errors[target.target.id] = failure.message?.take(64)
                    ?: failure::class.simpleName
                    ?: "sync-error"
            }
        }
        val merged = SyncMerger().merge(vault, discovered, appliedEventIds)
        val unknownEvents = discovered.distinctBy { it.eventId }
        targets.filter { it.target.enabled && it.target.confirmed }.forEach { target ->
            if (target.target.id in errors) return@forEach
            try {
                unknownEvents
                    .filterNot { it.eventId in eventsKnownByTarget(target, vaultId, decode) }
                    .forEach { event ->
                        target.store.put(
                            pathFactory(
                                vaultId,
                                event.deviceId,
                                event.deviceSequence,
                                target.target.objectPrefix,
                            ),
                            encode(event),
                        )
                    }
            } catch (failure: Throwable) {
                errors[target.target.id] = failure.message?.take(64)
                    ?: failure::class.simpleName
                    ?: "sync-error"
            }
        }
        return MultiTargetSyncResult(merged.vault, merged.appliedEventIds, errors)
    }

    private suspend fun eventsKnownByTarget(
        target: SyncTargetStore,
        vaultId: EntityId,
        decode: (ByteArray) -> SyncDelta,
    ): Set<EntityId> {
        val deltaPrefix = S3ObjectPaths.joinPrefix(
            target.target.objectPrefix,
            "deltas/",
        )
        return target.store.list(deltaPrefix)
            .map { decode(target.store.get(it)) }
            .flatMap { it.events }
            .map { it.eventId }
            .toSet()
    }
}

/**
 * 多副本同步结果及按目标隔离的失败状态。
 */
data class MultiTargetSyncResult(
    val vault: VaultState,
    val appliedEventIds: Set<EntityId>,
    val targetErrors: Map<EntityId, String>,
)
