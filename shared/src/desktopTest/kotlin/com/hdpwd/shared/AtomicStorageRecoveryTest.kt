package com.hdpwd.shared

import com.hdpwd.shared.storage.DesktopAtomicByteStore
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * 验证进程中断留下临时文件时仍读取最后成功版本。
 */
class AtomicStorageRecoveryTest {
    /**
     * stale tmp 文件不得覆盖最后成功快照。
     */
    @Test
    fun staleTemporaryFileDoesNotReplaceLastGoodSnapshot() = runTest {
        val root = Files.createTempDirectory("hd-pwd-recovery")
        try {
            val store = DesktopAtomicByteStore(root)
            val good = byteArrayOf(1, 2, 3)
            store.writeAtomically("user", good)
            Files.write(root.resolve("user.tmp"), byteArrayOf(9, 9, 9))
            assertContentEquals(good, store.read("user"))
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
