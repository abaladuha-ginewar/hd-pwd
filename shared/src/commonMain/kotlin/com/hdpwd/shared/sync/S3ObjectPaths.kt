package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId

/**
 * 不泄露用户名、key、标题和标签的 S3 对象路径。
 */
object S3ObjectPaths {
    /**
     * 将目标级对象目录前缀与相对路径拼接。
     */
    fun joinPrefix(objectPrefix: String, relativePath: String): String {
        val prefix = objectPrefix.trim().trim('/')
        val relative = relativePath.trimStart('/')
        return if (prefix.isEmpty()) relative else "$prefix/$relative"
    }

    /**
     * 返回协议版本对象路径。
     */
    fun protocol(vaultId: EntityId, version: Int, objectPrefix: String = ""): String =
        joinPrefix(objectPrefix, "vault/${vaultId.value}/protocol/$version")

    /**
     * 返回设备 head 对象路径。
     */
    fun deviceHead(vaultId: EntityId, deviceId: EntityId, objectPrefix: String = ""): String =
        joinPrefix(objectPrefix, "vault/${vaultId.value}/devices/${deviceId.value}/head")

    /**
     * 返回设备增量对象路径。
     */
    fun delta(vaultId: EntityId, deviceId: EntityId, sequence: Long, objectPrefix: String = ""): String =
        joinPrefix(objectPrefix, "vault/${vaultId.value}/deltas/${deviceId.value}/$sequence.dat")

    /**
     * 返回快照对象路径。
     */
    fun snapshot(vaultId: EntityId, snapshotId: EntityId, objectPrefix: String = ""): String =
        joinPrefix(objectPrefix, "vault/${vaultId.value}/snapshots/${snapshotId.value}.dat")
}
