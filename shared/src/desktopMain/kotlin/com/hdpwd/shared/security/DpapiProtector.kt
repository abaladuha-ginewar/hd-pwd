package com.hdpwd.shared.security

import com.sun.jna.platform.win32.Crypt32Util
import java.util.Locale

/**
 * 用 Windows DPAPI 保护 DeviceLEK 明文。
 *
 * 只绑定当前 Windows 用户登录态，不含用户在场验证；必须先经过 [WindowsHelloConsent]。
 */
interface DpapiProtector {
    /**
     * 封装 DeviceLEK，密文带稳定 label 前缀。
     */
    fun protect(label: String, envelopeKey: ByteArray): ByteArray

    /**
     * 解封装 DeviceLEK，并校验 label 前缀。
     */
    fun unprotect(label: String, sealedKey: ByteArray): ByteArray
}

/**
 * 现有 Crypt32 DPAPI 实现；密文格式与升级前静默封装兼容。
 */
class Crypt32DpapiProtector : DpapiProtector {
    /**
     * 使用 CryptProtectData 封装，附加 `hdpwd-dpapi:` label 前缀。
     */
    override fun protect(label: String, envelopeKey: ByteArray): ByteArray {
        require(isWindowsOs()) { "当前系统不支持 Windows DPAPI 封装" }
        val payload = labelPrefix(label) + envelopeKey
        return Crypt32Util.cryptProtectData(payload)
    }

    /**
     * 使用 CryptUnprotectData 解封装；前缀不匹配则拒绝。
     */
    override fun unprotect(label: String, sealedKey: ByteArray): ByteArray {
        require(isWindowsOs()) { "当前系统不支持 Windows DPAPI 封装" }
        val payload = Crypt32Util.cryptUnprotectData(sealedKey)
        val prefix = labelPrefix(label)
        require(payload.size > prefix.size) { "DPAPI 解封装数据过短" }
        val actualPrefix = payload.copyOfRange(0, prefix.size)
        require(actualPrefix.contentEquals(prefix)) { "DPAPI 封装与用户标识不匹配" }
        return payload.copyOfRange(prefix.size, payload.size)
    }
}

/**
 * 判断当前 JVM 是否运行在 Windows 上。
 */
internal fun isWindowsOs(): Boolean =
    System.getProperty("os.name")
        .orEmpty()
        .lowercase(Locale.ROOT)
        .contains("windows")

/**
 * 写入 DPAPI 明文前的稳定前缀，防止串用不同 label 的密文。
 */
internal fun labelPrefix(label: String): ByteArray =
    ("hdpwd-dpapi:$label\u0000").encodeToByteArray()
