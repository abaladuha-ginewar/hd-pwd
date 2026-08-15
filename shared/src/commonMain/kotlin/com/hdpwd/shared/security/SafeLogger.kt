package com.hdpwd.shared.security

/**
 * 生产日志的脱敏门面，调用方不得直接输出敏感对象。
 */
interface SafeLogger {
    /**
     * 记录非敏感诊断信息。
     */
    fun info(event: String, attributes: Map<String, String> = emptyMap())

    /**
     * 记录脱敏错误类别。
     */
    fun error(event: String, errorCode: String)
}

/**
 * 默认不向控制台输出任何内容的日志实现。
 */
object NoopSafeLogger : SafeLogger {
    /**
     * 丢弃普通诊断信息。
     */
    override fun info(event: String, attributes: Map<String, String>) = Unit

    /**
     * 丢弃错误详情，仅保留调用方可观察的返回状态。
     */
    override fun error(event: String, errorCode: String) = Unit
}
