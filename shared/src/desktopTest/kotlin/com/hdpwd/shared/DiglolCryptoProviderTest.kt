package com.hdpwd.shared

import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.crypto.platformCryptoProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

/**
 * 验证 Desktop 实际提供者的 Argon2id、HKDF 和 XChaCha20-Poly1305 路径。
 */
class DiglolCryptoProviderTest {
    /**
     * 生产提供者应完成派生、认证加密和解密往返。
     */
    @Test
    fun providerRoundTrip() {
        runBlocking {
            val provider = platformCryptoProvider()
            val salt = provider.randomBytes(16)
            val derived = provider.argon2id(
                password = "test-password".encodeToByteArray(),
                salt = salt,
                parameters = KdfParameters(memoryKiB = 16, iterations = 1, parallelism = 1),
            )
            val derivedD = provider.argon2d(
                password = "test-password".encodeToByteArray(),
                salt = salt,
                parameters = KdfParameters(memoryKiB = 16, iterations = 1, parallelism = 1),
            )
            assertEquals(32, derived.size)
            assertFalse(derived.contentEquals(derivedD))
            val key = provider.hkdfSha256(derived, salt, "test-domain".encodeToByteArray(), 32)
            val nonce = provider.randomBytes(24)
            val aad = "header".encodeToByteArray()
            val plaintext = "payload".encodeToByteArray()
            val ciphertext = provider.seal(key, nonce, plaintext, aad)
            assertContentEquals(plaintext, provider.open(key, nonce, ciphertext, aad))
            assertFails { provider.open(ByteArray(32), nonce, ciphertext, aad) }
            assertFails { provider.open(key, nonce, ciphertext, "wrong-header".encodeToByteArray()) }
            assertFails {
                provider.open(
                    key,
                    nonce,
                    ciphertext.copyOf().also {
                        it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
                    },
                    aad,
                )
            }
        }
    }
}
