package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.MutationStamp
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.Tombstone
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.sync.VaultS3SyncService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证共享 S3 密码库按修改戳合并，并保留本机 vaultId。
 */
class VaultS3SyncMergeTest {
    private fun entry(
        id: String,
        key: String,
        title: String = "标题",
        updatedAt: Long = 0,
        revision: Long = 0,
    ) = PasswordEntry(
        id = EntityId(id),
        parentId = null,
        key = key,
        title = title,
        mutation = MutationStamp(updatedAt, revision),
    )

    /**
     * 不同 id 的本地与远端条目应并存，并保留本机 vaultId。
     */
    @Test
    fun mergesDistinctEntriesAndKeepsLocalVaultId() {
        val localId = EntityId("local-user")
        val local = VaultState(
            vaultId = localId,
            entries = listOf(entry("a", "LocalKey", "本地", updatedAt = 10, revision = 1)),
            deviceSequence = 1,
        )
        val remote = VaultState(
            vaultId = EntityId("shared"),
            entries = listOf(entry("b", "RemoteKey", "远端", updatedAt = 20, revision = 3)),
            deviceSequence = 3,
        )
        val merged = VaultS3SyncService().mergeSharedVault(local, remote)
        assertEquals(localId, merged.vaultId)
        assertTrue(merged.entries.any { it.key == "LocalKey" })
        assertTrue(merged.entries.any { it.key == "RemoteKey" })
        assertEquals(3, merged.deviceSequence)
    }

    /**
     * 同 id 时修改戳更新的一侧胜出（本地改 key）。
     */
    @Test
    fun prefersNewerMutationStampForSameEntry() {
        val id = "entry"
        val local = VaultState(
            vaultId = EntityId("local"),
            entries = listOf(entry(id, "NewKey", updatedAt = 100, revision = 5)),
            deviceSequence = 5,
        )
        val remote = VaultState(
            vaultId = EntityId("shared"),
            entries = listOf(entry(id, "OldKey", updatedAt = 50, revision = 4)),
            deviceSequence = 4,
        )
        val merged = VaultS3SyncService().mergeSharedVault(local, remote)
        assertEquals("NewKey", merged.entries.single().key)
    }

    /**
     * 时间戳相同则比较递增序号。
     */
    @Test
    fun prefersHigherRevisionWhenTimestampEqual() {
        val id = "entry"
        val local = VaultState(
            vaultId = EntityId("local"),
            entries = listOf(entry(id, "LocalKey", updatedAt = 100, revision = 2)),
            deviceSequence = 2,
        )
        val remote = VaultState(
            vaultId = EntityId("shared"),
            entries = listOf(entry(id, "RemoteKey", updatedAt = 100, revision = 7)),
            deviceSequence = 7,
        )
        val merged = VaultS3SyncService().mergeSharedVault(local, remote)
        assertEquals("RemoteKey", merged.entries.single().key)
    }

    /**
     * 修改戳完全相等且整库戳相当时保留本地。
     */
    @Test
    fun prefersLocalOnEqualMutationStamp() {
        val id = "entry"
        val stamp = MutationStamp(updatedAt = 100, revision = 2)
        val local = VaultState(
            vaultId = EntityId("local"),
            entries = listOf(entry(id, "LocalKey").copy(mutation = stamp)),
            deviceSequence = 2,
        )
        val remote = VaultState(
            vaultId = EntityId("shared"),
            entries = listOf(entry(id, "RemoteKey").copy(mutation = stamp)),
            deviceSequence = 2,
        )
        val merged = VaultS3SyncService().mergeSharedVault(local, remote)
        assertEquals("LocalKey", merged.entries.single().key)
    }

    /**
     * 远端条目时间戳更新时，即使本机 deviceSequence 更大也要采用远端。
     */
    @Test
    fun prefersRemoteNewerEditEvenWhenLocalSequenceHigher() {
        val id = "entry"
        val local = VaultState(
            vaultId = EntityId("local"),
            entries = listOf(entry(id, "OldKey", updatedAt = 50, revision = 100)),
            deviceSequence = 100,
        )
        val remote = VaultState(
            vaultId = EntityId("shared"),
            entries = listOf(entry(id, "NewKey", updatedAt = 200, revision = 3)),
            deviceSequence = 3,
        )
        val merged = VaultS3SyncService().mergeSharedVault(local, remote)
        assertEquals("NewKey", merged.entries.single().key)
        assertEquals(100, merged.deviceSequence)
    }

    /**
     * 合并结果比远端旧时不得上传，避免覆盖云端新版本。
     */
    @Test
    fun shouldNotUploadWhenRemoteContentIsNewer() {
        val service = VaultS3SyncService()
        val older = VaultState(
            vaultId = EntityId("local"),
            entries = listOf(entry("e", "Old", updatedAt = 10, revision = 1)),
            deviceSequence = 50,
        )
        val newer = VaultState(
            vaultId = EntityId("shared"),
            entries = listOf(entry("e", "New", updatedAt = 99, revision = 2)),
            deviceSequence = 2,
        )
        assertTrue(!service.shouldUpload(merged = older, remote = newer))
        assertTrue(service.shouldUpload(merged = newer, remote = older))
    }

    /**
     * 较新的墓碑应删除较旧的存活条目。
     */
    @Test
    fun newerTombstoneRemovesOlderEntry() {
        val id = EntityId("entry")
        val local = VaultState(
            vaultId = EntityId("local"),
            entries = listOf(entry("entry", "Alive", updatedAt = 10, revision = 1)),
            deviceSequence = 1,
        )
        val remote = VaultState(
            vaultId = EntityId("shared"),
            tombstones = listOf(Tombstone(id, deletedAt = 20, revision = 2)),
            deviceSequence = 2,
        )
        val merged = VaultS3SyncService().mergeSharedVault(local, remote)
        assertTrue(merged.entries.isEmpty())
        assertEquals(1, merged.tombstones.size)
    }

    /**
     * 业务内容相同时对齐库序号，避免各设备版本号漂移。
     */
    @Test
    fun alignsDeviceSequenceWhenBusinessContentSame() {
        val entries = listOf(entry("e", "Key", updatedAt = 10, revision = 3))
        val local = VaultState(
            vaultId = EntityId("local"),
            entries = entries,
            deviceSequence = 3,
        )
        val remote = VaultState(
            vaultId = EntityId("shared"),
            entries = entries,
            deviceSequence = 9,
        )
        val merged = VaultS3SyncService().mergeSharedVault(local, remote)
        assertEquals(9, merged.deviceSequence)
        assertEquals("Key", merged.entries.single().key)
    }

    /**
     * 业务内容相同时判定为无变化。
     */
    @Test
    fun sameBusinessContentIgnoresVaultIdAndSequence() {
        val service = VaultS3SyncService()
        val left = VaultState(
            vaultId = EntityId("a"),
            entries = listOf(entry("e", "Key")),
            deviceSequence = 1,
        )
        val right = VaultState(
            vaultId = EntityId("b"),
            entries = listOf(entry("e", "Key")),
            deviceSequence = 9,
        )
        assertTrue(service.sameBusinessContent(left, right))
    }
}
