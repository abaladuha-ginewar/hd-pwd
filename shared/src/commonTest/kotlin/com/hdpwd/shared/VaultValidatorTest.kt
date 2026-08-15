package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.Folder
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.domain.VaultValidator
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * 验证导入和同步状态的结构约束。
 */
class VaultValidatorTest {
    /**
     * 无效 key 和颜色必须被拒绝。
     */
    @Test
    fun invalidVaultIsRejected() {
        val invalid = VaultState(
            vaultId = EntityId("vault"),
            folders = listOf(Folder(EntityId("f"), null, "folder", "#bad", 2)),
        )
        assertFails { VaultValidator.requireValid(invalid) }
    }

    /**
     * 合法空 Vault 应通过校验。
     */
    @Test
    fun emptyVaultIsValid() {
        assertTrue(VaultValidator.validate(VaultState(EntityId("vault"))).isEmpty())
    }
}
