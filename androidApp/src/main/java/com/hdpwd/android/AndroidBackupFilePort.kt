package com.hdpwd.android

import android.content.ContentResolver
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
            launcher.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream", "*/*"))
        }

    override suspend fun saveBackup(fileName: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    // 不用 application/octet-stream：部分系统会把未知类型改成 .dat.bin，
                    // Windows 按 *.dat 选择时就会选错文件或导入失败。
                    put(MediaStore.Downloads.MIME_TYPE, BACKUP_MIME_TYPE)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = activity.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法在下载目录创建文件")
                try {
                    resolver.openOutputStream(uri, "w")?.use { output ->
                        output.write(bytes)
                        output.flush()
                    } ?: error("无法写入下载目录")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    restoreDatExtensionIfNeeded(resolver, uri, safeName)
                } catch (error: Throwable) {
                    resolver.delete(uri, null, null)
                    throw error
                }
                "Download/${queryDisplayName(resolver, uri) ?: safeName}"
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

    private fun restoreDatExtensionIfNeeded(
        resolver: ContentResolver,
        uri: Uri,
        safeName: String,
    ) {
        val actual = queryDisplayName(resolver, uri) ?: return
        if (actual.equals(safeName, ignoreCase = true)) return
        if (!actual.endsWith(".bin", ignoreCase = true)) return
        runCatching {
            val update = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, BACKUP_MIME_TYPE)
            }
            resolver.update(uri, update, null, null)
        }
    }

    private fun queryDisplayName(
        resolver: ContentResolver,
        uri: Uri,
    ): String? =
        resolver.query(
            uri,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    companion object {
        private const val BACKUP_MIME_TYPE = "application/x-hdpwd-backup"
    }
}
