package com.hdpwd.shared.application

import com.hdpwd.shared.crypto.PasswordGenerator
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.VaultEditor
import com.hdpwd.shared.domain.VaultState

/**
 * 编排 UI 与领域服务的共享应用层入口。
 */
class VaultApplicationService(
    private val editor: VaultEditor = VaultEditor(),
) {
    /**
     * 校验并保存密码项到当前状态，生成结果不会进入 VaultState。
     */
    fun addEntry(vault: VaultState, entry: PasswordEntry): VaultState =
        editor.addEntry(vault, entry)

    /**
     * 在得到授权许可后临时生成密码。
     */
    fun generatePassword(
        recoveryPassword: CharSequence,
        entry: PasswordEntry,
    ): String = PasswordGenerator.generate(recoveryPassword, entry.key, entry.policy)
}
