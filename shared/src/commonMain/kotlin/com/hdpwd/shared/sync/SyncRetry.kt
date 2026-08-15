package com.hdpwd.shared.sync

import kotlinx.coroutines.delay

/**
 * S3 目标独立重试参数。
 */
data class RetryPolicy(
    val maxAttempts: Int = 5,
    val initialDelayMillis: Long = 500,
    val maxDelayMillis: Long = 30_000,
)

/**
 * 对单个同步目标执行指数退避和随机抖动重试。
 */
class SyncRetryExecutor(
    private val randomJitter: (Long) -> Long = {
        kotlin.random.Random.nextLong(0, it.coerceAtLeast(1))
    },
) {
    /**
     * 失败目标独立重试，成功后返回结果，最终失败抛出最后异常。
     */
    suspend fun <T> execute(
        policy: RetryPolicy = RetryPolicy(),
        operation: suspend (attempt: Int) -> T,
    ): T {
        var delayMillis = policy.initialDelayMillis
        var lastFailure: Throwable? = null
        for (attempt in 1..policy.maxAttempts) {
            try {
                return operation(attempt)
            } catch (failure: Throwable) {
                lastFailure = failure
                if (attempt == policy.maxAttempts) break
                delay(delayMillis + randomJitter(delayMillis).coerceAtLeast(0))
                delayMillis = (delayMillis * 2).coerceAtMost(policy.maxDelayMillis)
            }
        }
        throw lastFailure ?: IllegalStateException("同步重试失败")
    }
}
