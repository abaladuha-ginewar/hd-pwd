package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId

/**
 * 不泄露用户名、key、标题和标签的 S3 对象路径。
 *
 * 共享密码库对象只使用用户配置的存储目录，不自动追加 vaultId / 用户名等前缀目录。
 */
object S3ObjectPaths {
    /** 各设备共享的固定对象名；目录由用户在 S3 配置中指定。 */
    const val SHARED_VAULT_FILE = "vault.dat"

    /** 写入共享快照时使用的稳定 vaultId，避免设备本地用户 id 分裂同一密码库。 */
    val SHARED_VAULT_ID = EntityId("shared")

    /**
     * 将目标级对象目录与相对路径拼接；目录为空时写在 Bucket 根下。
     */
    fun joinPrefix(objectPrefix: String, relativePath: String): String {
        val prefix = objectPrefix.trim().trim('/')
        val relative = relativePath.trimStart('/')
        return if (prefix.isEmpty()) relative else "$prefix/$relative"
    }

    /**
     * 共享密码库备份对象路径：仅使用配置中的存储目录 + 固定文件名。
     */
    fun sharedVaultBlob(objectDirectory: String, fileName: String = SHARED_VAULT_FILE): String {
        val file = fileName.trim().trim('/')
        require(file.isNotEmpty()) { "对象文件名不能为空" }
        require(!file.contains("..") && !file.contains('\\')) { "对象文件名无效" }
        return joinPrefix(objectDirectory, file)
    }

    /**
     * 返回协议版本对象路径。
     */
    fun protocol(vaultId: EntityId, version: Int, objectPrefix: String = ""): String =
        joinPrefix(objectPrefix, "protocol/$version")

    /**
     * 返回设备 head 对象路径。
     */
    fun deviceHead(vaultId: EntityId, deviceId: EntityId, objectPrefix: String = ""): String =
        joinPrefix(objectPrefix, "devices/${deviceId.value}/head")

    /**
     * 返回设备增量对象路径。
     */
    fun delta(vaultId: EntityId, deviceId: EntityId, sequence: Long, objectPrefix: String = ""): String =
        joinPrefix(objectPrefix, "deltas/${deviceId.value}/$sequence.dat")

    /**
     * 返回命名快照对象路径（仍落在用户目录下，不嵌入 vaultId）。
     */
    fun snapshot(vaultId: EntityId, snapshotId: EntityId, objectPrefix: String = ""): String =
        joinPrefix(objectPrefix, "snapshots/${snapshotId.value}.dat")
}
