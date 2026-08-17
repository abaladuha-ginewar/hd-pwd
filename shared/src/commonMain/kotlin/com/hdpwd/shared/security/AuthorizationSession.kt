package com.hdpwd.shared.security

/**
 * 敏感操作的用途范围。
 */
enum class OperationPurpose {
    GENERATE_PASSWORD,
    EXPORT_BACKUP,
    IMPORT_BACKUP,
    SYNC,
    CHANGE_RECOVERY_PASSWORD,
    CREATE_USER,
    DEVICE_SETTINGS,
}

/**
 * 五分钟绝对授权会话和操作许可管理器。
 *
 * 会话只缓存 DeviceLEK；返回用户列表不得因此调用 [clear]，到期才清除密钥。
 */
class AuthorizationSession(
    private val clock: () -> Long,
    private val lifetimeMillis: Long = 5 * 60 * 1000L,
) {
    private var envelopeKey: LocalEnvelopeKey? = null
    private var expiresAt: Long = 0
    private var activeOperations = 0

    /**
     * 建立新的绝对期限会话，只在内存保存 LEK。
     */
    fun open(key: LocalEnvelopeKey) {
        clear()
        envelopeKey = key
        expiresAt = clock() + lifetimeMillis
    }

    /**
     * 判断当前会话是否仍可发起新敏感操作。
     */
    fun canStart(): Boolean = envelopeKey != null && clock() < expiresAt

    /**
     * 获取限定用途的操作许可，到期后不会签发新许可。
     */
    fun acquire(purpose: OperationPurpose): OperationPermit? {
        if (!canStart()) {
            expireIfIdle()
            return null
        }
        activeOperations++
        return OperationPermit(
            purpose = purpose,
            keyProvider = { envelopeKey ?: error("授权密钥不存在") },
            onClose = { release() },
        )
    }

    /**
     * 在会话内临时访问 LEK。
     */
    fun <T> withEnvelopeKey(block: (LocalEnvelopeKey) -> T): T {
        check(canStart()) { "授权会话已失效" }
        return block(envelopeKey ?: error("授权密钥不存在"))
    }

    /**
     * 使用已经取得许可的 LEK，即使会话刚刚到期也允许当前操作完成。
     */
    fun <T> withEnvelopeKey(permit: OperationPermit, block: (LocalEnvelopeKey) -> T): T =
        permit.withKey(block)

    /**
     * 在已有操作许可内执行需要挂起的敏感操作。
     */
    suspend fun <T> withEnvelopeKeySuspending(
        permit: OperationPermit,
        block: suspend (LocalEnvelopeKey) -> T,
    ): T = permit.withKeySuspending(block)

    /**
     * 清除会话及其 LEK。
     */
    fun clear() {
        envelopeKey?.clear()
        envelopeKey = null
        expiresAt = 0
        activeOperations = 0
    }

    private fun release() {
        activeOperations = (activeOperations - 1).coerceAtLeast(0)
        expireIfIdle()
    }

    private fun expireIfIdle() {
        if (clock() >= expiresAt && activeOperations == 0) clear()
    }
}

/**
 * 已经开始的限定用途操作，关闭时释放会话引用。
 */
class OperationPermit internal constructor(
    val purpose: OperationPurpose,
    private val keyProvider: () -> LocalEnvelopeKey,
    private val onClose: () -> Unit,
) : AutoCloseable {
    private var closed = false

    /**
     * 释放本次操作许可。
     */
    override fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }

    /**
     * 在许可生命周期内访问最小必要 LEK。
     */
    internal fun <T> withKey(block: (LocalEnvelopeKey) -> T): T {
        check(!closed) { "操作许可已关闭" }
        return block(keyProvider())
    }

    /**
     * 在许可生命周期内访问 LEK 并支持挂起调用。
     */
    internal suspend fun <T> withKeySuspending(
        block: suspend (LocalEnvelopeKey) -> T,
    ): T {
        check(!closed) { "操作许可已关闭" }
        return block(keyProvider())
    }
}
