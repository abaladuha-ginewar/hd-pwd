package com.hdpwd.shared.storage

import com.hdpwd.shared.application.DirtyStateStore

/**
 * 基于 [AtomicByteStore] 的 dirty 标记持久化，供 Android/Web/Desktop 生命周期复用。
 */
class AtomicDirtyStateStore(
    private val bytes: AtomicByteStore,
) : DirtyStateStore {
    override suspend fun setDirty(userId: String, dirty: Boolean) {
        val key = dirtyKey(userId)
        if (dirty) {
            bytes.writeAtomically(key, byteArrayOf(1))
        } else {
            bytes.delete(key)
        }
    }

    override suspend fun isDirty(userId: String): Boolean =
        bytes.read(dirtyKey(userId)) != null

    private fun dirtyKey(userId: String): String = "$userId-dirty"
}
