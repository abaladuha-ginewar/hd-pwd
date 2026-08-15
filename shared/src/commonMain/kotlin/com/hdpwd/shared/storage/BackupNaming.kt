package com.hdpwd.shared.storage

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 手动导出备份文件名生成器。
 */
object BackupNaming {
    /**
     * 生成 `<用户名>_<年>-<月>-<日>_<时>-<分>-<秒>.dat` 文件名。
     */
    fun fileName(username: String, epochMillis: Long): String {
        val safeName = username
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifEmpty { "user" }
        val date = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        return buildString {
            append(safeName)
            append('_')
            append(date.year.toString().padStart(4, '0'))
            append('-').append(date.monthNumber.toString().padStart(2, '0'))
            append('-').append(date.dayOfMonth.toString().padStart(2, '0'))
            append('_').append(date.hour.toString().padStart(2, '0'))
            append('-').append(date.minute.toString().padStart(2, '0'))
            append('-').append(date.second.toString().padStart(2, '0'))
            append(".dat")
        }
    }
}
