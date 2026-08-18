package com.hdpwd.shared.ui

import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.sync.S3ProviderPreset
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 面向用户的中文文案与错误映射。
 */
object UserFacingText {
    /**
     * 同步状态中文。
     */
    fun syncStatus(status: SyncStatus): String = when (status) {
        SyncStatus.IDLE -> "空闲"
        SyncStatus.PENDING -> "待同步"
        SyncStatus.SYNCING -> "同步中"
        SyncStatus.SUCCESS -> "成功"
        SyncStatus.FAILED -> "失败"
    }

    /**
     * 将 epoch 毫秒格式化为本地可读时间；无效时返回破折号。
     */
    fun formatDateTime(epochMillis: Long?): String {
        val ms = epochMillis ?: return "—"
        if (ms <= 0L) return "—"
        val dateTime = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        fun Int.pad(width: Int = 2) = toString().padStart(width, '0')
        return buildString {
            append(dateTime.year)
            append('-')
            append(dateTime.monthNumber.pad())
            append('-')
            append(dateTime.dayOfMonth.pad())
            append(' ')
            append(dateTime.hour.pad())
            append(':')
            append(dateTime.minute.pad())
            append(':')
            append(dateTime.second.pad())
        }
    }

    /**
     * 提供商代码对应的显示名。
     */
    fun providerName(providerCode: String): String =
        S3ProviderPreset.fromProviderCode(providerCode).displayName

    /**
     * 将异常转为可展示的中文提示。
     */
    fun fromThrowable(error: Throwable?, fallback: String = "操作失败，请重试"): String {
        val chain = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .toList()
        for (raw in chain) {
            localizeMessage(raw)?.let { return it }
        }
        val raw = chain.firstOrNull().orEmpty()
        if (raw.isEmpty()) return fallback
        // 已是中文业务提示则直接展示；英文/技术异常改用中文兜底
        if (!looksForeignOrTechnical(raw)) return raw
        return fallback
    }

    /**
     * 将错误码 / 类名转为中文。
     */
    fun fromErrorCode(code: String?): String? {
        val raw = code?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return ERROR_CODES[raw] ?: localizeMessage(raw) ?: raw.takeUnless { looksForeignOrTechnical(it) }
    }

    private fun localizeMessage(raw: String): String? {
        ERROR_EXACT[raw]?.let { return it }
        ERROR_EXACT.entries.firstOrNull { raw.equals(it.key, ignoreCase = true) }?.let { return it.value }
        for ((pattern, message) in ERROR_CONTAINS) {
            if (raw.contains(pattern, ignoreCase = true)) return message
        }
        return null
    }

    private fun looksForeignOrTechnical(raw: String): Boolean {
        val hasCjk = raw.any { it.code in 0x4E00..0x9FFF }
        if (hasCjk) return false
        val hasLatin = raw.any { it in 'A'..'Z' || it in 'a'..'z' }
        return hasLatin ||
            raw.any { it in "[]{}<>" } ||
            raw.contains("Exception", ignoreCase = true) ||
            raw.contains("Error", ignoreCase = true)
    }

    private val ERROR_EXACT = mapOf(
        "connection" to "无法连接存储服务，请检查网络与配置",
        "connection-failed" to "连接失败，请检查网络与配置",
        "sync-error" to "同步失败，请稍后重试",
        "temporary" to "临时错误，请稍后重试",
        "remote failure" to "远端写入失败，请稍后重试",
        "KeystoreException" to "系统密钥库异常，请重试或检查生物识别设置",
        "UserNotAuthenticatedException" to "需要先完成生物识别验证",
        "InvalidKeyException" to "密钥无效，请重新启用生物识别",
        "CancellationException" to "操作已取消",
        "本机主密码错误" to "本机主密码错误",
        "恢复密码错误，或备份/密码库数据已损坏" to "恢复密码错误，或备份/密码库数据已损坏",
        "加密容器 magic 无效" to "所选文件不是有效的哈密备份",
        "加密容器长度不足" to "所选文件不是有效的哈密备份",
        "加密容器头部长度无效" to "所选文件不是有效的哈密备份",
        "加密容器头部不匹配" to "所选文件不是有效的哈密备份",
        "不支持的加密容器版本" to "备份格式不受支持",
    )

    private val ERROR_CODES = mapOf(
        "connection-failed" to "连接失败",
        "sync-error" to "同步失败",
        "IllegalArgumentException" to "参数无效",
        "IllegalStateException" to "状态异常，请重试",
        "IOException" to "网络或文件读写失败",
        "SocketTimeoutException" to "连接超时，请检查网络",
        "UnknownHostException" to "无法解析服务器地址",
        "SSLException" to "安全连接失败，请确认使用 https",
        "CancellationException" to "已取消",
    )

    private val ERROR_CONTAINS = listOf(
        "恢复密码错误" to "恢复密码错误，或备份/密码库数据已损坏",
        "加密容器 magic" to "所选文件不是有效的哈密备份",
        "本机主密码错误" to "本机主密码错误",
        "tag" to "恢复密码或主密码错误，解密失败",
        "mac" to "恢复密码或主密码错误，解密失败",
        "aead" to "恢复密码或主密码错误，解密失败",
        "poly1305" to "恢复密码或主密码错误，解密失败",
        "chacha" to "恢复密码或主密码错误，解密失败",
        "authenticat" to "恢复密码或主密码错误，解密失败",
        "decrypt" to "恢复密码或主密码错误，解密失败",
        "ciphertext" to "恢复密码或主密码错误，解密失败",
        "Unable to resolve host" to "无法解析服务器地址，请检查 Endpoint",
        "failed to connect" to "无法连接到服务器，请检查网络与 Endpoint",
        "timeout" to "连接超时，请稍后重试",
        "Unauthorized" to "访问未授权，请检查密钥是否正确",
        "Access Denied" to "访问被拒绝，请检查权限与 Bucket",
        "Forbidden" to "没有权限访问该资源",
        "NoSuchBucket" to "Bucket 不存在，请检查名称与区域",
        "InvalidAccessKeyId" to "Access Key 无效",
        "SignatureDoesNotMatch" to "签名不匹配，请检查密钥、区域与 Endpoint",
        "User canceled" to "已取消生物识别",
        "Too many attempts" to "尝试次数过多，请稍后再试",
        "Lockout" to "生物识别已锁定，请稍后再试或使用本机主密码",
        "Key permanently invalidated" to "生物识别密钥已失效，请重新启用",
        "No biometrics" to "设备未录入可用的生物识别",
        "BIOMETRIC_ERROR" to "生物识别失败，请改用本机主密码",
        "keystore" to "系统密钥库异常，请重试",
    )
}
