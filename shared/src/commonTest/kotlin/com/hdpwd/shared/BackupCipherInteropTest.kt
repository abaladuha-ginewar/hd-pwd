package com.hdpwd.shared

import com.hdpwd.shared.crypto.platformCryptoProvider
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.PasswordPolicy
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.storage.BackupService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 真实 AEAD 备份密文互通：导出后再导入必须还原业务内容。
 */
class BackupCipherInteropTest {
    @Test
    fun exportImportRoundTripPreservesEntries() = runTest {
        val recovery = "backup-recovery-phrase"
        val service = BackupService.production(platformCryptoProvider())
        val vault = VaultState(
            vaultId = EntityId("vault-1"),
            entries = listOf(
                PasswordEntry(
                    id = EntityId("entry-1"),
                    parentId = null,
                    key = "GitHub.Work",
                    title = "GitHub",
                    policy = PasswordPolicy(),
                ),
            ),
        )
        val bytes = service.export(recovery, vault)
        assertTrue(bytes.isNotEmpty())
        val imported = service.import(recovery, bytes)
        assertEquals(vault.vaultId, imported.vaultId)
        assertEquals(1, imported.entries.size)
        assertEquals("GitHub.Work", imported.entries.single().key)
        assertEquals("GitHub", imported.entries.single().title)
    }

    @Test
    fun wrongRecoveryPasswordFailsClosed() = runTest {
        val service = BackupService.production(platformCryptoProvider())
        val vault = VaultState(vaultId = EntityId("vault-2"))
        val bytes = service.export("correct-password", vault)
        assertFailsWith<Throwable> {
            service.import("wrong-password", bytes)
        }
    }
}
