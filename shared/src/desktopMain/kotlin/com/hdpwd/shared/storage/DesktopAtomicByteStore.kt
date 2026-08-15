package com.hdpwd.shared.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Windows/Desktop 应用私有目录中的原子 Vault 文件存储。
 */
class DesktopAtomicByteStore(
    private val root: Path,
) : AtomicByteStore {
    /**
     * 读取最后一个完整快照。
     */
    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val path = pathFor(key)
        if (Files.exists(path)) Files.readAllBytes(path) else null
    }

    /**
     * 临时写入后原子替换目标文件。
     */
    override suspend fun writeAtomically(key: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            Files.createDirectories(root)
            val target = pathFor(key)
            val temporary = root.resolve("$key.tmp")
            Files.write(temporary, bytes)
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /**
     * 删除当前用户本地快照。
     */
    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            Files.deleteIfExists(pathFor(key))
            Files.deleteIfExists(root.resolve("$key.tmp"))
        }
    }

    private fun pathFor(key: String): Path {
        require(key.matches(Regex("[A-Za-z0-9._-]+"))) { "存储 key 包含非法路径字符" }
        return root.resolve("$key.vault")
    }
}
