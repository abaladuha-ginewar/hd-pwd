package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.sync.S3ObjectPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证 S3 对象路径只使用随机身份和序号，且共享备份不嵌入 vaultId。
 */
class S3ProtocolTest {
    /**
     * 对象路径不得出现用户业务字段。
     */
    @Test
    fun objectPathContainsNoBusinessFields() {
        val path = S3ObjectPaths.delta(EntityId("vault-id"), EntityId("device-id"), 4)
        assertTrue(path.endsWith("/4.dat"))
        assertFalse(path.contains("GitHub"))
        assertFalse(path.contains("title"))
        val prefixed = S3ObjectPaths.delta(
            EntityId("vault-id"),
            EntityId("device-id"),
            4,
            objectPrefix = "family-vault",
        )
        assertEquals("family-vault/deltas/device-id/4.dat", prefixed)
        assertFalse(prefixed.contains("vault-id"))
    }

    /**
     * 共享密码库只落在用户配置目录下的固定文件名。
     */
    @Test
    fun sharedVaultBlobUsesConfiguredDirectoryOnly() {
        assertEquals("vault.dat", S3ObjectPaths.sharedVaultBlob(""))
        assertEquals("family-vault/vault.dat", S3ObjectPaths.sharedVaultBlob("family-vault"))
        assertEquals("a/b/vault.dat", S3ObjectPaths.sharedVaultBlob("/a/b/"))
        assertFalse(S3ObjectPaths.sharedVaultBlob("family-vault").contains("shared"))
    }
}
