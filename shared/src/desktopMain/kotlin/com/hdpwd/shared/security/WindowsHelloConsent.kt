package com.hdpwd.shared.security

/**
 * Windows Hello 用户在场验证。
 *
 * 这是应用内闸门，不是 TPM 密钥绑定：通过后仍用 DPAPI 解封装。
 * 实现可注入，单元测试不得弹出系统 UI。
 */
interface WindowsHelloConsent {
    /**
     * 查询 Hello 是否可用、未录入或不可用。
     */
    fun availability(): BiometricAvailability

    /**
     * 弹出系统 Hello（指纹、面容或 PIN）。取消或失败必须抛错。
     */
    suspend fun requestVerification(message: String)
}
