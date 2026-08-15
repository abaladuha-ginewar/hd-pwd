package com.hdpwd.shared.domain

/**
 * 对导入、同步和本地读取的 VaultState 执行完整结构约束校验。
 */
object VaultValidator {
    /**
     * 返回全部结构错误；空列表表示合法。
     */
    fun validate(vault: VaultState): List<String> {
        val errors = mutableListOf<String>()
        val folderIds = vault.folders.map { it.id }
        val entryIds = vault.entries.map { it.id }
        if (folderIds.size != folderIds.toSet().size) errors += "文件夹对象标识重复"
        if (entryIds.size != entryIds.toSet().size) errors += "密码项对象标识重复"
        if ((folderIds + entryIds).size != (folderIds + entryIds).toSet().size) {
            errors += "文件夹和密码项对象标识冲突"
        }
        if (vault.entries.groupBy { it.key }.any { it.value.size > 1 }) {
            errors += "密码项 key 不唯一"
        }
        vault.entries.forEach { entry ->
            if (!KeyRules.isValid(entry.key)) errors += "密码项 key 格式无效: ${entry.id.value}"
            if (entry.parentId != null && vault.folders.none { it.id == entry.parentId }) {
                errors += "密码项父目录不存在: ${entry.id.value}"
            }
            if (entry.policy.validationError() != null) errors += "密码项规则无效: ${entry.id.value}"
            if (!ColorRules.isValidHex(entry.colorHex)) errors += "密码项颜色无效: ${entry.id.value}"
        }
        vault.folders.forEach { folder ->
            if (folder.parentId != null && vault.folders.none { it.id == folder.parentId }) {
                errors += "文件夹父目录不存在: ${folder.id.value}"
            }
            if (folder.depth !in 2..3) errors += "文件夹层级无效: ${folder.id.value}"
            if (!ColorRules.isValidHex(folder.colorHex)) errors += "文件夹颜色无效: ${folder.id.value}"
            if (hasCycle(vault.folders, folder.id)) errors += "文件夹目录存在环: ${folder.id.value}"
        }
        return errors.distinct()
    }

    /**
     * 校验失败时抛出脱敏结构错误。
     */
    fun requireValid(vault: VaultState) {
        val errors = validate(vault)
        require(errors.isEmpty()) { errors.joinToString("; ") }
    }

    private fun hasCycle(folders: List<Folder>, start: EntityId): Boolean {
        val seen = mutableSetOf<EntityId>()
        var current: EntityId? = start
        while (current != null) {
            val node = current ?: break
            if (!seen.add(node)) return true
            current = folders.firstOrNull { it.id == node }?.parentId
        }
        return false
    }
}
