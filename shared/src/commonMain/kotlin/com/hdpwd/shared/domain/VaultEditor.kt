package com.hdpwd.shared.domain

import kotlinx.datetime.Clock

/**
 * 纯领域层密码库编辑器，所有调用均返回新的不可变 VaultState。
 *
 * 每次增删改都会写入 [MutationStamp]（时间戳 + 递增序号），供跨设备合并裁决。
 */
class VaultEditor(
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /**
     * 创建文件夹并校验根目录起算的三级层级限制。
     */
    fun addFolder(
        vault: VaultState,
        id: EntityId,
        parentId: EntityId?,
        name: String,
        colorHex: String,
    ): VaultState {
        require(name.isNotBlank()) { "文件夹名不能为空" }
        require(ColorRules.isValidHex(colorHex)) { "颜色格式无效" }
        require(vault.folders.none { it.id == id } && vault.entries.none { it.id == id }) {
            "对象标识已存在"
        }
        val parentDepth = vault.folders.firstOrNull { it.id == parentId }?.depth ?: 1
        require(parentDepth < 3) { "已达到三级目录上限" }
        require(vault.folders.none { it.parentId == parentId && it.name == name }) {
            "当前目录下文件夹名已存在"
        }
        val (revision, mutation) = nextMutation(vault)
        return vault.copy(
            folders = vault.folders + Folder(
                id = id,
                parentId = parentId,
                name = name,
                colorHex = colorHex,
                depth = parentDepth + 1,
                mutation = mutation,
            ),
            deviceSequence = revision,
        )
    }

    /**
     * 创建不含生成密码的密码项。
     */
    fun addEntry(vault: VaultState, entry: PasswordEntry): VaultState {
        require(KeyRules.isValid(entry.key)) { "key 格式无效" }
        require(vault.folders.none { it.id == entry.id } && vault.entries.none { it.id == entry.id }) {
            "对象标识已存在"
        }
        require(vault.entries.none { it.key == entry.key }) { "当前用户下 key 已存在" }
        require(entry.policy.validationError() == null) { entry.policy.validationError() ?: "规则无效" }
        require(ColorRules.isValidHex(entry.colorHex)) { "颜色格式无效" }
        val (revision, mutation) = nextMutation(vault)
        return vault.copy(
            entries = vault.entries + entry.copy(mutation = mutation),
            deviceSequence = revision,
        )
    }

    /**
     * 更新文件夹名称和颜色并保留其层级及同步身份。
     */
    fun updateFolder(vault: VaultState, folder: Folder): VaultState {
        require(vault.folders.any { it.id == folder.id }) { "文件夹不存在" }
        require(folder.name.isNotBlank()) { "文件夹名不能为空" }
        require(ColorRules.isValidHex(folder.colorHex)) { "颜色格式无效" }
        require(folder.depth in 2..3) { "文件夹层级无效" }
        require(vault.folders.none { it.id != folder.id && it.parentId == folder.parentId && it.name == folder.name }) {
            "当前目录下文件夹名已存在"
        }
        val (revision, mutation) = nextMutation(vault)
        return vault.copy(
            folders = vault.folders.map {
                if (it.id == folder.id) folder.copy(mutation = mutation) else it
            },
            deviceSequence = revision,
        )
    }

    /**
     * 更新密码项并保留其不可变身份。
     */
    fun updateEntry(vault: VaultState, entry: PasswordEntry): VaultState {
        require(KeyRules.isValid(entry.key)) { "key 格式无效" }
        require(vault.entries.any { it.id == entry.id }) { "密码项不存在" }
        require(vault.entries.none { it.id != entry.id && it.key == entry.key }) {
            "当前用户下 key 已存在"
        }
        require(entry.policy.validationError() == null) { entry.policy.validationError() ?: "规则无效" }
        require(ColorRules.isValidHex(entry.colorHex)) { "颜色格式无效" }
        val (revision, mutation) = nextMutation(vault)
        return vault.copy(
            entries = vault.entries.map {
                if (it.id == entry.id) entry.copy(mutation = mutation) else it
            },
            deviceSequence = revision,
        )
    }

    /**
     * 删除单个密码项并追加同步墓碑。
     */
    fun deleteEntry(
        vault: VaultState,
        entryId: EntityId,
        timestamp: Long,
        transactionId: EntityId? = null,
    ): VaultState {
        require(vault.entries.any { it.id == entryId }) { "密码项不存在" }
        val (revision, _) = nextMutation(vault)
        return vault.copy(
            entries = vault.entries.filterNot { it.id == entryId },
            tombstones = vault.tombstones + Tombstone(
                entityId = entryId,
                deletedAt = timestamp,
                transactionId = transactionId,
                revision = revision,
            ),
            deviceSequence = revision,
        )
    }

    /**
     * 移动密码项到合法目录，不改变其同步身份。
     */
    fun moveEntry(vault: VaultState, entryId: EntityId, parentId: EntityId?): VaultState {
        require(vault.entries.any { it.id == entryId }) { "密码项不存在" }
        require(parentId == null || vault.folders.any { it.id == parentId }) { "目标目录不存在" }
        val (revision, mutation) = nextMutation(vault)
        return vault.copy(
            entries = vault.entries.map {
                if (it.id == entryId) it.copy(parentId = parentId, mutation = mutation) else it
            },
            deviceSequence = revision,
        )
    }

    /**
     * 移动文件夹并拒绝形成环或超过三级目录。
     */
    fun moveFolder(vault: VaultState, folderId: EntityId, parentId: EntityId?): VaultState {
        require(folderId != parentId) { "文件夹不能移动到自身" }
        require(parentId == null || vault.folders.any { it.id == parentId }) { "目标目录不存在" }
        val subtree = descendants(vault.folders, folderId) + folderId
        require(parentId !in subtree) { "文件夹移动会形成目录环" }
        val parentDepth = vault.folders.firstOrNull { it.id == parentId }?.depth ?: 1
        val subtreeDepth = vault.folders.filter { it.id in subtree }.maxOfOrNull { it.depth } ?: parentDepth
        require(parentDepth + (subtreeDepth - (vault.folders.first { it.id == folderId }.depth)) <= 3) {
            "移动后会超过三级目录"
        }
        val rootDepth = vault.folders.first { it.id == folderId }.depth
        val depthDelta = parentDepth + 1 - rootDepth
        val (revision, mutation) = nextMutation(vault)
        return vault.copy(
            folders = vault.folders.map {
                when {
                    it.id == folderId -> it.copy(
                        parentId = parentId,
                        depth = it.depth + depthDelta,
                        mutation = mutation,
                    )
                    it.id in subtree -> it.copy(depth = it.depth + depthDelta, mutation = mutation)
                    else -> it
                }
            },
            deviceSequence = revision,
        )
    }

    /**
     * 删除文件夹及其后代对象，并生成同步墓碑。
     */
    fun deleteFolder(vault: VaultState, folderId: EntityId, timestamp: Long, transactionId: EntityId): VaultState {
        val removedFolders = descendants(vault.folders, folderId) + folderId
        val removedEntries = vault.entries.filter { it.parentId in removedFolders }.map { it.id }
        val removedIds = removedFolders + removedEntries
        val (revision, _) = nextMutation(vault)
        return vault.copy(
            folders = vault.folders.filterNot { it.id in removedFolders },
            entries = vault.entries.filterNot { it.id in removedEntries },
            tombstones = vault.tombstones + removedIds.map {
                Tombstone(
                    entityId = it,
                    deletedAt = timestamp,
                    transactionId = transactionId,
                    revision = revision,
                )
            },
            deviceSequence = revision,
        )
    }

    private fun nextMutation(vault: VaultState): Pair<Long, MutationStamp> {
        val revision = vault.deviceSequence + 1
        return revision to MutationStamp(updatedAt = nowMillis(), revision = revision)
    }

    private fun descendants(folders: List<Folder>, root: EntityId): Set<EntityId> {
        val result = mutableSetOf<EntityId>()
        val pending = ArrayDeque<EntityId>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val parent = pending.removeFirst()
            folders.filter { it.parentId == parent }.forEach {
                if (result.add(it.id)) pending.add(it.id)
            }
        }
        return result
    }
}
