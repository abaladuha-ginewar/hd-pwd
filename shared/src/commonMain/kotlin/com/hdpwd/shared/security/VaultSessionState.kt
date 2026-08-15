package com.hdpwd.shared.security

import com.hdpwd.shared.domain.VaultState

/**
 * 仅在解锁期间保存的 Vault 内存状态。
 */
class VaultSessionState {
    private var currentVault: VaultState? = null
    private var searchIndex: List<String> = emptyList()

    /**
     * 设置当前解密 Vault 和搜索索引。
     */
    fun open(vault: VaultState, index: List<String> = emptyList()) {
        clear()
        currentVault = vault
        searchIndex = index
    }

    /**
     * 在当前解密会话内访问 Vault。
     */
    fun <T> useVault(block: (VaultState) -> T): T =
        block(currentVault ?: error("Vault 会话已锁定"))

    /**
     * 清理解密 Vault、搜索索引和引用。
     */
    fun clear() {
        currentVault = null
        searchIndex = emptyList()
    }

    /**
     * 判断当前是否存在解密 Vault。
     */
    fun isOpen(): Boolean = currentVault != null
}
