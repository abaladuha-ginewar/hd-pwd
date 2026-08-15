package com.hdpwd.shared

import com.hdpwd.shared.storage.DesktopAtomicByteStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * 验证 Desktop 临时文件和原子替换存储。
 */
class DesktopAtomicByteStoreTest {
    /**
     * 写入后应能读取完整快照并删除临时文件。
     */
    @Test
    fun atomicSnapshotRoundTrip() = runBlocking {
        val root = Files.createTempDirectory("hd-pwd-store")
        try {
            val store = DesktopAtomicByteStore(root)
            val value = byteArrayOf(1, 2, 3)
            store.writeAtomically("user-id", value)
            assertContentEquals(value, store.read("user-id"))
            store.delete("user-id")
            assertContentEquals(null, store.read("user-id"))
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
