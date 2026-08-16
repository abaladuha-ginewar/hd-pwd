package com.hdpwd.shared.domain

import kotlinx.serialization.Serializable

/**
 * 使用不可变字符串表示跨设备同步身份。
 */
@Serializable
data class EntityId(val value: String)

/**
 * 密码项的密码生成规则。
 */
@Serializable
data class PasswordPolicy(
    val version: Int = 1,
    val length: Int = 20,
    val requireUppercase: Boolean = true,
    val requireLowercase: Boolean = true,
    val requireDigits: Boolean = true,
    val requireSymbols: Boolean = true,
    val minimumUppercase: Int = 1,
    val minimumLowercase: Int = 1,
    val minimumDigits: Int = 1,
    val minimumSymbols: Int = 1,
    val symbols: String = "!@#$%^&*_-+=.?",
    val excluded: String = "",
) {
    /**
     * 校验规则可满足性，返回 null 表示有效。
     */
    fun validationError(): String? {
        if (version != 1) return "不支持的密码规则版本"
        if (length !in 4..256) return "密码长度必须为 4 至 256"
        if (symbols.any { it.isWhitespace() }) return "符号集合不能包含空白字符"
        if (excluded.any { it.isWhitespace() }) return "排除字符不能包含空白字符"
        val required = listOf(
            requireUppercase to minimumUppercase,
            requireLowercase to minimumLowercase,
            requireDigits to minimumDigits,
            requireSymbols to minimumSymbols,
        ).filter { it.first }.sumOf { it.second }
        if (required > length) return "必选字符数量超过密码长度"
        if (requireSymbols && symbols.isEmpty()) return "必须包含符号时符号集合不能为空"
        if (symbols.all { it in excluded } && requireSymbols) return "符号集合被全部排除"
        return null
    }

    /**
     * 返回稳定的规则序列化文本，用作生成协议输入。
     */
    fun canonical(): String =
        buildString {
            append("v").append(version)
            append(";l=").append(length)
            append(";u=").append(requireUppercase).append(":").append(minimumUppercase)
            append(";lo=").append(requireLowercase).append(":").append(minimumLowercase)
            append(";d=").append(requireDigits).append(":").append(minimumDigits)
            append(";s=").append(requireSymbols).append(":").append(minimumSymbols)
            append(";symbols=").append(symbols)
            append(";excluded=").append(excluded)
        }
}

/**
 * 密码项中的用户自定义标签。
 */
@Serializable
data class Label(
    val name: String,
    val value: String,
)

/**
 * 密码库文件夹，根目录使用 null parentId 表示。
 */
@Serializable
data class Folder(
    val id: EntityId,
    val parentId: EntityId?,
    val name: String,
    val colorHex: String,
    val depth: Int,
    /** 最近一次增删改的修改戳，用于跨设备合并裁决。 */
    val mutation: MutationStamp = MutationStamp(),
)

/**
 * 不保存生成后的子密码的密码项。
 */
@Serializable
data class PasswordEntry(
    val id: EntityId,
    val parentId: EntityId?,
    val key: String,
    val title: String,
    val labels: List<Label> = emptyList(),
    val policy: PasswordPolicy = PasswordPolicy(),
    val generatorVersion: Int = 1,
    val colorHex: String = "#94A3B8",
    /** 最近一次增删改的修改戳，用于跨设备合并裁决。 */
    val mutation: MutationStamp = MutationStamp(),
)

/**
 * 单次修改的唯一标识：墙钟时间戳 + 本机递增序号。
 *
 * 合并同 id 冲突时先比 [updatedAt]，再比 [revision]；二者都相等时由合并方优先保留本地。
 */
@Serializable
data class MutationStamp(
    val updatedAt: Long = 0L,
    val revision: Long = 0L,
) : Comparable<MutationStamp> {
    override fun compareTo(other: MutationStamp): Int {
        val byTime = updatedAt.compareTo(other.updatedAt)
        if (byTime != 0) return byTime
        return revision.compareTo(other.revision)
    }
}

/**
 * S3 同步目标的非敏感配置和状态。
 */
@Serializable
data class SyncTarget(
    val id: EntityId,
    val provider: String,
    val endpoint: String,
    val bucket: String,
    val region: String,
    val enabled: Boolean = false,
    val confirmed: Boolean = false,
    val status: SyncStatus = SyncStatus.IDLE,
    val lastErrorCode: String? = null,
    /** 最近一次同步完成时间（epoch millis）；尚未同步成功时为 null。 */
    val lastSyncAt: Long? = null,
    /** 最近一次同步完成时的密码库版本号（deviceSequence）。 */
    val lastSyncRevision: Long? = null,
    val objectPrefix: String = "",
    /** Access Key Id（非 Secret，可展示）。 */
    val accessKeyId: String = "",
    /** 使用 SyncKey 封装后的凭据载荷（十六进制）。 */
    val encryptedCredentialsHex: String = "",
    /** 派生 SyncKey 时使用的盐（十六进制）。 */
    val credentialsSaltHex: String = "",
)

/**
 * 单个同步目标的可观察状态。
 */
@Serializable
enum class SyncStatus {
    IDLE,
    PENDING,
    SYNCING,
    SUCCESS,
    FAILED,
}

/**
 * 删除后保留的同步墓碑。
 */
@Serializable
data class Tombstone(
    val entityId: EntityId,
    val deletedAt: Long,
    val transactionId: EntityId? = null,
    /** 与 [deletedAt] 组成修改戳，用于和条目/文件夹版本比较。 */
    val revision: Long = 0L,
) {
    val mutation: MutationStamp
        get() = MutationStamp(updatedAt = deletedAt, revision = revision)
}

/**
 * 当前设备可见的同步冲突。
 */
@Serializable
data class Conflict(
    val id: EntityId,
    val entityId: EntityId,
    val field: String,
    val winner: String,
    val candidate: String,
    val createdAt: Long,
)

/**
 * 加密持久化的密码库状态。
 */
@Serializable
data class VaultState(
    val vaultId: EntityId,
    val schemaVersion: Int = 1,
    val folders: List<Folder> = emptyList(),
    val entries: List<PasswordEntry> = emptyList(),
    val syncTargets: List<SyncTarget> = emptyList(),
    val tombstones: List<Tombstone> = emptyList(),
    val conflicts: List<Conflict> = emptyList(),
    val deviceSequence: Long = 0,
) {
    /**
     * 业务内容的最新修改戳（条目 / 文件夹 / 墓碑），用于展示最后修改时间与版本。
     */
    fun latestContentMutation(): MutationStamp {
        var best = MutationStamp()
        folders.forEach { if (it.mutation > best) best = it.mutation }
        entries.forEach { if (it.mutation > best) best = it.mutation }
        tombstones.forEach { if (it.mutation > best) best = it.mutation }
        if (best.revision == 0L && best.updatedAt == 0L && deviceSequence > 0L) {
            return MutationStamp(updatedAt = 0L, revision = deviceSequence)
        }
        return best
    }

    /**
     * 对外展示与同步对齐用的内容版本：库序号与最新修改戳 revision 取较大值。
     */
    fun contentVersion(): Long = maxOf(deviceSequence, latestContentMutation().revision)
}
