package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId

/**
 * 不泄露用户名、key、标题和标签的 S3 对象路径。
 */
object S3ObjectPaths {
    /**
     * 返回协议版本对象路径。
     */
    fun protocol(vaultId: EntityId, version: Int): String =
        "vault/${vaultId.value}/protocol/$version"

    /**
     * 返回设备 head 对象路径。
     */
    fun deviceHead(vaultId: EntityId, deviceId: EntityId): String =
        "vault/${vaultId.value}/devices/${deviceId.value}/head"

    /**
     * 返回设备增量对象路径。
     */
    fun delta(vaultId: EntityId, deviceId: EntityId, sequence: Long): String =
        "vault/${vaultId.value}/deltas/${deviceId.value}/$sequence.dat"

    /**
     * 返回快照对象路径。
     */
    fun snapshot(vaultId: EntityId, snapshotId: EntityId): String =
        "vault/${vaultId.value}/snapshots/${snapshotId.value}.dat"
}
