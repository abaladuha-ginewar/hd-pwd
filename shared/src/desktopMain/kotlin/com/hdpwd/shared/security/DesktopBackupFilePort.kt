package com.hdpwd.shared.security

import com.hdpwd.shared.platform.BackupFilePort
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Desktop 备份导入/导出：导入走 Swing 文件选择器（Windows 下可正确处理中文路径），
 * 导出写入用户 Downloads。
 */
class DesktopBackupFilePort : BackupFilePort {
    override suspend fun openBackup(): ByteArray? {
        val selected = pickExistingFile() ?: return null
        return withContext(Dispatchers.IO) {
            val path = selected.toPath()
            if (!Files.isRegularFile(path)) null else Files.readAllBytes(path)
        }
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

    private suspend fun pickExistingFile(): File? =
        suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater {
                val owner = Frame().apply {
                    isUndecorated = true
                    isAlwaysOnTop = true
                    setLocationRelativeTo(null)
                    isVisible = true
                }
                try {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "选择 hd-pwd 备份"
                        fileSelectionMode = JFileChooser.FILES_ONLY
                        isAcceptAllFileFilterUsed = true
                        isMultiSelectionEnabled = false
                        val datFilter = FileNameExtensionFilter("hd-pwd 备份 (*.dat)", "dat")
                        addChoosableFileFilter(datFilter)
                        addChoosableFileFilter(FileNameExtensionFilter("二进制文件 (*.bin)", "bin"))
                        fileFilter = datFilter
                        preferredDownloadsDirectory()?.let { currentDirectory = it }
                    }
                    val approved = chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION
                    val file = chooser.selectedFile?.takeIf { approved }
                    if (continuation.isActive) continuation.resume(file)
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    owner.dispose()
                }
            }
        }

    private fun preferredDownloadsDirectory(): File? {
        val downloads = File(System.getProperty("user.home"), "Downloads")
        return downloads.takeIf { it.isDirectory }
    }
}
