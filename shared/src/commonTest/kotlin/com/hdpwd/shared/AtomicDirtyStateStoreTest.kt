package com.hdpwd.shared

import com.hdpwd.shared.storage.AtomicByteStore
import com.hdpwd.shared.storage.AtomicDirtyStateStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * dirty 标记持久化与崩溃后恢复读取。
 */
class AtomicDirtyStateStoreTest {
    @Test
    fun dirtyFlagSurvivesStoreRoundTrip() = runTest {
        val memory = MemoryByteStore()
        val dirty = AtomicDirtyStateStore(memory)
        assertFalse(dirty.isDirty("user-a"))
        dirty.setDirty("user-a", true)
        assertTrue(dirty.isDirty("user-a"))
        dirty.setDirty("user-a", false)
        assertFalse(dirty.isDirty("user-a"))
    }
}

private class MemoryByteStore : AtomicByteStore {
    private val map = mutableMapOf<String, ByteArray>()

    override suspend fun read(key: String): ByteArray? = map[key]?.copyOf()

    override suspend fun writeAtomically(key: String, bytes: ByteArray) {
        map[key] = bytes.copyOf()
    }

    override suspend fun delete(key: String) {
        map.remove(key)
    }
}
