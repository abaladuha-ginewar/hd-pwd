package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId

/**
 * 统一生成新增、修改、移动和删除事件的工厂。
 */
class SyncEventFactory(
    private val deviceId: EntityId,
    private val nowMillis: () -> Long,
) {
    private var nextSequence = 0L

    /**
     * 创建一个带设备序号和逻辑时钟的事件。
     */
    fun create(
        eventId: EntityId,
        entityId: EntityId,
        operation: SyncOperation,
        changes: List<FieldChange> = emptyList(),
        transactionId: EntityId? = null,
    ): SyncEvent {
        nextSequence++
        return SyncEvent(
            eventId = eventId,
            deviceId = deviceId,
            deviceSequence = nextSequence,
            entityId = entityId,
            operation = operation,
            changes = changes,
            clock = HybridLogicalClock(nowMillis(), nextSequence.toInt(), deviceId),
            transactionId = transactionId,
        )
    }
}
