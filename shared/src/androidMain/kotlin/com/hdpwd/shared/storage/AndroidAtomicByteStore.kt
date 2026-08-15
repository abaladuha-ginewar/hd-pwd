package com.hdpwd.shared.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 应用私有目录中的原子 Vault 文件存储。
 */
class AndroidAtomicByteStore(
    private val root: File,
) : AtomicByteStore {
    /**
     * 读取最后一个完整快照。
     */
    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = pathFor(key)
        if (file.exists()) file.readBytes() else null
    }

    /**
     * 临时写入后替换目标文件，避免直接覆盖最后成功版本。
     */
    override suspend fun writeAtomically(key: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            require(root.exists() || root.mkdirs()) { "无法创建应用私有目录" }
            val target = pathFor(key)
            val temporary = File(root, "$key.tmp")
            temporary.writeBytes(bytes)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
    }

    /**
     * 删除本地用户快照。
     */
    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            pathFor(key).delete()
            File(root, "$key.tmp").delete()
        }
    }

    private fun pathFor(key: String): File {
        require(key.matches(Regex("[A-Za-z0-9._-]+"))) { "存储 key 包含非法路径字符" }
        return File(root, "$key.vault")
    }
}
