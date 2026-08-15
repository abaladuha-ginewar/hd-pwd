package com.hdpwd.shared

import com.hdpwd.shared.crypto.platformCryptoProvider
import com.hdpwd.shared.storage.DefaultKdfParameters
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 在 Docker JVM 环境中验证首版 KDF 参数可接受且输出长度稳定。
 */
class KdfBenchmarkTest {
    /**
     * 使用非敏感固定测试输入测量 Argon2id，不输出密码或派生密钥。
     */
    @Test
    fun defaultKdfParametersAreUsable() = runBlocking {
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val result = platformCryptoProvider().argon2id(
            password = "benchmark-only".encodeToByteArray(),
            salt = ByteArray(16) { it.toByte() },
            parameters = DefaultKdfParameters,
        )
        val elapsedMillis = started.elapsedNow().inWholeMilliseconds
        assertEquals(32, result.size)
        assertTrue(elapsedMillis < 20_000, "KDF 参数在构建环境中超过 20 秒")
        result.fill(0)
    }
}
