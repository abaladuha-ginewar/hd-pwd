package com.hdpwd.shared.security

/**
 * Desktop 生物识别：Windows Hello 用户在场后再用 DPAPI 封装 DeviceLEK。
 *
 * 闸门只约束本应用路径，不是 Android Keystore 那种硬件绑定。
 * 同 Windows 用户令牌下直接调用 CryptUnprotectData 仍可能解开密文。
 */
class DesktopWindowsHelloDpapiProvider(
    private val consent: WindowsHelloConsent = WinRtWindowsHelloConsent(),
    private val protector: DpapiProtector = Crypt32DpapiProtector(),
) : BiometricProvider {
    /**
     * Hello 可用才报告 AVAILABLE；未录入或非 Windows 不得视为可启用。
     */
    override fun availability(): BiometricAvailability = consent.availability()

    /**
     * 先 Hello，再 DPAPI 封装；Hello 失败不得写入密文。
     */
    override suspend fun seal(label: String, envelopeKey: ByteArray): ByteArray {
        requireAvailable()
        consent.requestVerification("启用生物识别以保护本机密钥")
        return protector.protect(label, envelopeKey)
    }

    /**
     * 先 Hello，再 DPAPI 解封装；未验证不得触碰密文。
     */
    override suspend fun open(label: String, sealedKey: ByteArray): ByteArray {
        requireAvailable()
        consent.requestVerification("验证身份以解锁本机密钥")
        return protector.unprotect(label, sealedKey)
    }

    /**
     * DPAPI 无独立密钥槽；密文由调用方删除本地存储。
     */
    override suspend fun delete(label: String) = Unit

    private fun requireAvailable() {
        val status = availability()
        require(status == BiometricAvailability.AVAILABLE) {
            when (status) {
                BiometricAvailability.NOT_ENROLLED ->
                    "尚未配置 Windows Hello，请先在系统设置中添加指纹、面容或 PIN"
                else -> "当前设备不支持 Windows Hello"
            }
        }
    }
}
