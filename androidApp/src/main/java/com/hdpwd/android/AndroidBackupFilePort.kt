package com.hdpwd.android

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.hdpwd.shared.platform.BackupFilePort
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android 备份导入/导出：导入走文档选择器，导出写入系统 Downloads。
 */
class AndroidBackupFilePort(
    private val activity: ComponentActivity,
) : BackupFilePort {
    private var pending: ((ByteArray?) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val callback = pending
        pending = null
        if (uri == null) {
            callback?.invoke(null)
            return@registerForActivityResult
        }
        val bytes = runCatching {
            activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        callback?.invoke(bytes)
    }

    override suspend fun openBackup(): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            pending = { bytes ->
                if (continuation.isActive) continuation.resume(bytes)
            }
            continuation.invokeOnCancellation { pending = null }
            launcher.launch(arrayOf("application/octet-stream", "*/*"))
        }

    override suspend fun saveBackup(fileName: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法在下载目录创建文件")
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        output.write(bytes)
                        output.flush()
                    } ?: error("无法写入下载目录")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (error: Throwable) {
                    resolver.delete(uri, null, null)
                    throw error
                }
                "Download/$safeName"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists() && !dir.mkdirs()) {
                    error("无法创建下载目录")
                }
                val target = File(dir, safeName)
                FileOutputStream(target).use { it.write(bytes) }
                target.absolutePath
            }
        }
}
