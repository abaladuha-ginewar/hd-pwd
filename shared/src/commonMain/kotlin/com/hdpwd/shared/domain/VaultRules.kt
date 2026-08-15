package com.hdpwd.shared.domain

/**
 * 密码项 key 的跨平台约束。
 */
object KeyRules {
    private val pattern = Regex("[A-Za-z_.-]+")

    /**
     * 返回 key 的校验错误，合法时返回 null。
     */
    fun validate(key: String): String? = when {
        key.length !in 1..128 -> "key 长度必须为 1 至 128"
        !pattern.matches(key) -> "key 只能包含 A-Z、a-z、下划线、点和连字符"
        else -> null
    }

    /**
     * 判断 key 是否符合格式。
     */
    fun isValid(key: String): Boolean = validate(key) == null
}

/**
 * 密码库颜色白名单和格式校验器。
 */
object ColorRules {
    val fixedColors = listOf(
        "#EF4444",
        "#FB923C",
        "#FBBF24",
        "#34D399",
        "#2DD4BF",
        "#60A5FA",
        "#6366F1",
        "#C084FC",
        "#F472B6",
        "#94A3B8",
    )

    /**
     * 校验不透明 RGB HEX 颜色。
     */
    fun isValidHex(color: String): Boolean =
        color.matches(Regex("#[0-9A-Fa-f]{6}"))
}

/**
 * 判断编辑是否会改变确定性子密码。
 */
fun PasswordEntry.generationInputsChanged(updated: PasswordEntry): Boolean =
    key != updated.key || policy.canonical() != updated.policy.canonical() ||
        generatorVersion != updated.generatorVersion

/**
 * 密码库领域校验和查询服务。
 */
class VaultQueries {
    /**
     * 检查 key 是否在当前用户内唯一。
     */
    fun validateUniqueKey(entries: List<PasswordEntry>, key: String, editingId: EntityId? = null): String? {
        KeyRules.validate(key)?.let { return it }
        if (entries.any { it.id != editingId && it.key == key }) {
            return "当前用户下 key 已存在"
        }
        return null
    }

    /**
     * 从当前目录递归搜索密码项，并返回所在目录路径。
     */
    fun search(vault: VaultState, currentFolderId: EntityId?, query: String): List<SearchResult> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        val descendants = descendantFolders(vault.folders, currentFolderId)
        val allowedFolders = descendants + currentFolderId
        return vault.entries
            .asSequence()
            .filter { it.parentId in allowedFolders }
            .filter { entry ->
                (listOf(entry.key, entry.title) +
                    entry.labels.flatMap { listOf(it.name, it.value) })
                    .any { it.lowercase().contains(normalized) }
            }
            .map { SearchResult(it, folderPath(vault.folders, it.parentId)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.entry.title })
            .toList()
    }

    /**
     * 返回密码项相对当前 Vault 根目录的文件夹路径。
     */
    fun folderPath(vault: VaultState, folderId: EntityId?): String =
        folderPath(vault.folders, folderId)

    /**
     * 返回当前目录下文件夹优先、同类稳定排序的内容。
     */
    fun browse(vault: VaultState, folderId: EntityId?): List<BrowseItem> {
        val folders = vault.folders
            .filter { it.parentId == folderId }
            .sortedBy { it.name.lowercase() }
            .map { BrowseItem.FolderItem(it) }
        val entries = vault.entries
            .filter { it.parentId == folderId }
            .sortedBy { it.title.lowercase() }
            .map { BrowseItem.EntryItem(it) }
        return folders + entries
    }

    private fun descendantFolders(folders: List<Folder>, root: EntityId?): Set<EntityId?> {
        val result = mutableSetOf<EntityId?>()
        val queue = ArrayDeque<EntityId?>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            folders.filter { it.parentId == parent }.forEach {
                if (result.add(it.id)) queue.add(it.id)
            }
        }
        return result
    }

    private fun folderPath(folders: List<Folder>, folderId: EntityId?): String {
        val byId = folders.associateBy { it.id }
        val path = mutableListOf<String>()
        var current = folderId
        while (current != null) {
            val folder = byId[current] ?: break
            path += folder.name
            current = folder.parentId
        }
        return path.asReversed().joinToString("/")
    }
}

/**
 * 搜索结果及其相对目录路径。
 */
data class SearchResult(
    val entry: PasswordEntry,
    val folderPath: String,
)

/**
 * 当前目录中可展示的对象。
 */
sealed interface BrowseItem {
    /**
     * 文件夹展示项。
     */
    data class FolderItem(val folder: Folder) : BrowseItem

    /**
     * 密码项展示项。
     */
    data class EntryItem(val entry: PasswordEntry) : BrowseItem
}
