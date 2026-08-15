package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.sync.FieldChange
import com.hdpwd.shared.sync.HybridLogicalClock
import com.hdpwd.shared.sync.SyncEvent
import com.hdpwd.shared.sync.SyncMerger
import com.hdpwd.shared.sync.SyncOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证同步去重、字段合并和删除墓碑。
 */
class SyncMergerTest {
    /**
     * 不同字段并发更新应合并，同一字段更新保留冲突记录。
     */
    @Test
    fun mergeFieldsAndKeepConflict() {
        val entryId = EntityId("entry")
        val vault = VaultState(
            EntityId("vault"),
            entries = listOf(PasswordEntry(entryId, null, "key", "old")),
        )
        val titleEvent = event("title-event", entryId, "title", "new-title", 1)
        val concurrentTitle = event("title-event-2", entryId, "title", "other-title", 3)
        val colorEvent = event("color-event", entryId, "colorHex", "#EF4444", 2)
        val result = SyncMerger().merge(vault, listOf(titleEvent, colorEvent, concurrentTitle), emptySet())
        assertEquals("other-title", result.vault.entries.single().title)
        assertEquals("#EF4444", result.vault.entries.single().colorHex)
        assertEquals(3, result.appliedEventIds.size)
        assertTrue(result.vault.conflicts.any { it.field == "title" })
    }

    /**
     * 删除事件应留下墓碑并阻止对象继续存在。
     */
    @Test
    fun deleteLeavesTombstone() {
        val entryId = EntityId("entry")
        val vault = VaultState(
            EntityId("vault"),
            entries = listOf(PasswordEntry(entryId, null, "key", "title")),
        )
        val event = SyncEvent(
            eventId = EntityId("delete"),
            deviceId = EntityId("device"),
            deviceSequence = 1,
            entityId = entryId,
            operation = SyncOperation.DELETE,
            clock = HybridLogicalClock(1, 0, EntityId("device")),
        )
        val result = SyncMerger().merge(vault, listOf(event, event), emptySet())
        assertTrue(result.vault.entries.isEmpty())
        assertEquals(1, result.vault.tombstones.size)
        assertEquals(1, result.appliedEventIds.size)
    }

    /**
     * 不同对象并发占用同一 key 时必须保留人工冲突。
     */
    @Test
    fun duplicateKeyCreatesConflict() {
        val first = PasswordEntry(EntityId("first"), null, "same", "one")
        val second = PasswordEntry(EntityId("second"), null, "other", "two")
        val vault = VaultState(EntityId("vault"), entries = listOf(first, second))
        val event = event("key-event", second.id, "key", "same", 1)
        val result = SyncMerger().merge(vault, listOf(event), emptySet())
        assertTrue(result.vault.conflicts.any { it.field == "key" })
        assertEquals("other", result.vault.entries.single { it.id == second.id }.key)
    }

    private fun event(
        id: String,
        entityId: EntityId,
        field: String,
        value: String,
        sequence: Long,
    ) = SyncEvent(
        eventId = EntityId(id),
        deviceId = EntityId("device"),
        deviceSequence = sequence,
        entityId = entityId,
        operation = SyncOperation.UPDATE,
        changes = listOf(FieldChange(field, value)),
        clock = HybridLogicalClock(sequence, 0, EntityId("device")),
    )
}
