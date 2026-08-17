package com.hdpwd.shared.security

import com.sun.jna.platform.win32.Crypt32Util
import java.util.Locale

/**
 * Windows DPAPI（用户登录态绑定）封装 DeviceLEK。
 *
 * 在可用时作为 Desktop「安全密钥 / 用户验证」能力；非 Windows 环境回落为 [UNAVAILABLE]，
 * UI 将隐藏生物识别选项并使用本机主密码。
 */
class DesktopWindowsDpapiProvider : BiometricProvider {
    override fun availability(): BiometricAvailability =
        if (isWindows()) BiometricAvailability.AVAILABLE else BiometricAvailability.UNAVAILABLE

    override suspend fun seal(label: String, envelopeKey: ByteArray): ByteArray {
        ensureWindows()
        val payload = labelPrefix(label) + envelopeKey
        return Crypt32Util.cryptProtectData(payload)
    }

    override suspend fun open(label: String, sealedKey: ByteArray): ByteArray {
        ensureWindows()
        val payload = Crypt32Util.cryptUnprotectData(sealedKey)
        val prefix = labelPrefix(label)
        require(payload.size > prefix.size) { "DPAPI 解封装数据过短" }
        val actualPrefix = payload.copyOfRange(0, prefix.size)
        require(actualPrefix.contentEquals(prefix)) { "DPAPI 封装与用户标识不匹配" }
        return payload.copyOfRange(prefix.size, payload.size)
    }

    override suspend fun delete(label: String) {
        // DPAPI 密文由调用方删除本地存储；无独立密钥槽需要清理
    }

    private fun ensureWindows() {
        require(isWindows()) { "当前系统不支持 Windows DPAPI 封装" }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name")
            .orEmpty()
            .lowercase(Locale.ROOT)
            .contains("windows")

    private fun labelPrefix(label: String): ByteArray =
        ("hdpwd-dpapi:$label\u0000").encodeToByteArray()
}
