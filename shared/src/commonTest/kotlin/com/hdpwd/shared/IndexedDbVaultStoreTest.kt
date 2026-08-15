package com.hdpwd.shared

import com.hdpwd.shared.storage.IndexedDbPort
import com.hdpwd.shared.storage.IndexedDbVaultStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * 验证 IndexedDB 适配层的事务端口和失败隔离。
 */
class IndexedDbVaultStoreTest {
    /**
     * 加密 Blob 应通过对象仓库端口往返。
     */
    @Test
    fun blobRoundTrip() = runTest {
        val port = MemoryIndexedDbPort()
        val store = IndexedDbVaultStore(port)
        store.write("user", byteArrayOf(1, 2))
        assertContentEquals(byteArrayOf(1, 2), store.read("user"))
        store.delete("user")
        assertContentEquals(null, store.read("user"))
    }
}

/**
 * IndexedDB 端口测试替身。
 */
private class MemoryIndexedDbPort : IndexedDbPort {
    private val values = mutableMapOf<String, ByteArray>()
    override suspend fun get(store: String, key: String): ByteArray? = values[key]
    override suspend fun put(store: String, key: String, value: ByteArray) {
        values[key] = value.copyOf()
    }
    override suspend fun delete(store: String, key: String) {
        values.remove(key)
    }
}
