package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.security.VaultSessionState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * 验证锁定后解密 Vault 和搜索索引都不可访问。
 */
class VaultSessionStateTest {
    /**
     * clear 应立即关闭会话。
     */
    @Test
    fun clearRemovesVault() {
        val session = VaultSessionState()
        session.open(VaultState(EntityId("vault")), listOf("secret-index"))
        assertTrue(session.isOpen())
        session.clear()
        assertFalse(session.isOpen())
        assertFails { session.useVault { it } }
    }
}
