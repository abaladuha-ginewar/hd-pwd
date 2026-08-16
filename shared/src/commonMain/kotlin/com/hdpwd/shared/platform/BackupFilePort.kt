package com.hdpwd.shared.platform

/**
 * 备份文件读写端口。
 */
interface BackupFilePort {
    /**
     * 打开系统文件选择器并读取 `.dat` 备份内容；取消时返回 null。
     */
    suspend fun openBackup(): ByteArray?

    /**
     * 将备份写入平台默认下载目录，返回最终可读路径或描述。
     */
    suspend fun saveBackup(fileName: String, bytes: ByteArray): String
}

/**
 * 不支持文件选择的平台回退实现。
 */
object UnsupportedBackupFilePort : BackupFilePort {
    override suspend fun openBackup(): ByteArray? = null

    override suspend fun saveBackup(fileName: String, bytes: ByteArray): String =
        error("当前平台不支持导出到文件")
}
