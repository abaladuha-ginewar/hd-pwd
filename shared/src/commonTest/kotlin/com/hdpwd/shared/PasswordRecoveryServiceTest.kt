package com.hdpwd.shared

import com.hdpwd.shared.application.PasswordRecoveryService
import com.hdpwd.shared.crypto.PasswordGenerator
import com.hdpwd.shared.domain.PasswordPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证无 Vault 恢复入口只依赖恢复密码和恢复配方。
 */
class PasswordRecoveryServiceTest {
    /**
     * 有效配方应生成与密码项一致的子密码。
     */
    @Test
    fun recoversWithoutVault() {
        val recipe = PasswordGenerator.recipe("GitHub.Work", PasswordPolicy()).encode()
        val recovered = PasswordRecoveryService().recover("recovery", recipe)
        assertEquals(
            PasswordGenerator.generate("recovery", "GitHub.Work", PasswordPolicy()),
            recovered,
        )
    }
}
