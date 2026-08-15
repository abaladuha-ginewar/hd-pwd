package com.hdpwd.shared

import com.hdpwd.shared.storage.BackupNaming
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证导出备份文件名不会把路径控制字符带入文件系统。
 */
class BackupNamingTest {
    /**
     * 文件名应保留固定时间格式并转义危险字符。
     */
    @Test
    fun sanitizesBackupName() {
        val name = BackupNaming.fileName("alice:/vault", 0)
        assertTrue(name.startsWith("alice__vault_"))
        assertTrue(name.endsWith(".dat"))
        assertTrue(':' !in name)
    }
}
