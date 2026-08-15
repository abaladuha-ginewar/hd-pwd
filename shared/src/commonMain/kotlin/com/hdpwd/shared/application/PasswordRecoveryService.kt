package com.hdpwd.shared.application

import com.hdpwd.shared.crypto.PasswordGenerator

/**
 * 无需现有 Vault 的恢复配方生成服务。
 */
class PasswordRecoveryService {
    /**
     * 解析恢复配方并临时生成子密码，不读取或创建本地用户。
     */
    fun recover(recoveryPassword: CharSequence, recipeText: String): String {
        val recipe = PasswordGenerator.parseRecipe(recipeText)
        return PasswordGenerator.generate(recoveryPassword, recipe.key, recipe.policy)
    }
}
