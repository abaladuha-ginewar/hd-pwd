package com.hdpwd.shared.security

/**
 * 用于缩短敏感字节生命周期的可覆盖缓冲区。
 */
open class SensitiveBytes(bytes: ByteArray) {
    private var value: ByteArray? = bytes.copyOf()

    /**
     * 在回调期间访问敏感数据，回调结束后由调用方决定何时清理。
     */
    fun <T> use(block: (ByteArray) -> T): T {
        val current = value ?: error("敏感缓冲区已清理")
        return block(current)
    }

    /**
     * 覆盖并释放内部字节数组。
     */
    fun clear() {
        value?.fill(0)
        value = null
    }
}

/**
 * 会话中仅允许缓存的本机封装密钥。
 */
class LocalEnvelopeKey(bytes: ByteArray) : SensitiveBytes(bytes)

/**
 * 用途隔离后的临时密钥，禁止通过 toString 暴露内容。
 */
class SensitiveKey(bytes: ByteArray) : SensitiveBytes(bytes) {
    /**
     * 仅返回脱敏类型名。
     */
    override fun toString(): String = "SensitiveKey(<redacted>)"
}
