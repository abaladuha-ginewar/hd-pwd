package com.hdpwd.shared.security

import com.hdpwd.shared.platform.BackupFilePort
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop 备份导入/导出：导入走文件对话框，导出写入用户 Downloads。
 */
class DesktopBackupFilePort : BackupFilePort {
    override suspend fun openBackup(): ByteArray? = withContext(Dispatchers.IO) {
        val dialog = FileDialog(null as Frame?, "选择 hd-pwd 备份", FileDialog.LOAD)
        dialog.file = "*.dat"
        dialog.isVisible = true
        val name = dialog.file ?: return@withContext null
        val dir = dialog.directory ?: return@withContext null
        File(dir, name).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun saveBackup(fileName: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
            val downloads = File(System.getProperty("user.home"), "Downloads")
            if (!downloads.exists()) downloads.mkdirs()
            val target = File(downloads, safeName)
            target.writeBytes(bytes)
            target.absolutePath
        }
}
