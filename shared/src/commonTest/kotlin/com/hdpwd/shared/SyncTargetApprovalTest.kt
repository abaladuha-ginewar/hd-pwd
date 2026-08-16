package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.sync.SyncTargetApprovalService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证远端新增 S3 配置不会在确认前自动连接。
 */
class SyncTargetApprovalTest {
    /**
     * 导入目标默认停用，确认后才启用。
     */
    @Test
    fun importedTargetRequiresConfirmation() {
        val target = SyncTarget(EntityId("target"), "s3", "https://example.test", "bucket", "region")
        val service = SyncTargetApprovalService()
        val pending = service.pendingImportedTargets(VaultState(EntityId("vault"), syncTargets = listOf(target)))
        assertFalse(pending.syncTargets.single().enabled)
        assertFalse(pending.syncTargets.single().confirmed)
        val approved = service.confirm(pending, EntityId("target"))
        assertTrue(approved.syncTargets.single().enabled)
        assertTrue(approved.syncTargets.single().confirmed)
    }

    /**
     * 本机创建用户导入备份时，含完整凭据的目标应自动启用。
     */
    @Test
    fun localBackupImportActivatesCredentialedTargets() {
        val ready = SyncTarget(
            id = EntityId("ready"),
            provider = "s3",
            endpoint = "https://example.test",
            bucket = "bucket",
            region = "us-east-1",
            accessKeyId = "AKIA",
            encryptedCredentialsHex = "abcd",
            credentialsSaltHex = "1234",
        )
        val incomplete = SyncTarget(
            id = EntityId("incomplete"),
            provider = "s3",
            endpoint = "https://example.test",
            bucket = "bucket",
            region = "us-east-1",
        )
        val activated = SyncTargetApprovalService().activateLocalBackupTargets(
            VaultState(EntityId("vault"), syncTargets = listOf(ready, incomplete)),
        )
        assertTrue(activated.syncTargets.single { it.id == EntityId("ready") }.confirmed)
        assertTrue(activated.syncTargets.single { it.id == EntityId("ready") }.enabled)
        assertFalse(activated.syncTargets.single { it.id == EntityId("incomplete") }.confirmed)
    }
}