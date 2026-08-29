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

    /**
     * 必须与 Web libsodium crypto_pwhash(ARGON2ID13) + IETF XChaCha20-Poly1305 字节级一致，
     * 否则 PC 导出的备份在浏览器里会提示恢复密码错误。
     */
    @Test
    fun argon2idAndAeadMatchLibsodiumWebVectors() {
        runBlocking {
            val provider = platformCryptoProvider()
            val salt = ByteArray(16) { it.toByte() }
            val derived = provider.argon2id(
                password = "test-password".encodeToByteArray(),
                salt = salt,
                parameters = KdfParameters(memoryKiB = 16, iterations = 1, parallelism = 1),
            )
            assertEquals(
                "a6cf0bdb176032421ac106918f63d71cb0d8f80371ba81ab4fe8bbb77060cb8d",
                derived.toHex(),
            )
            val nonce = ByteArray(24) { it.toByte() }
            val ciphertext = provider.seal(
                key = derived,
                nonce = nonce,
                plaintext = "payload".encodeToByteArray(),
                aad = "header".encodeToByteArray(),
            )
            assertEquals(
                "000102030405060708090a0b0c0d0e0f1011121314151617" +
                    "36d5ef42de29e8ee814ab9bdc77e48a719b7876606c404",
                ciphertext.toHex(),
            )
        }
    }

    /**
     * Web hash-wasm Argon2d 必须与 Diglol Type.D 字节级一致。
     */
    @Test
    fun argon2dMatchesHashWasmWebVector() {
        runBlocking {
            val derived = platformCryptoProvider().argon2d(
                password = "test-password".encodeToByteArray(),
                salt = ByteArray(16) { it.toByte() },
                parameters = KdfParameters(memoryKiB = 16, iterations = 1, parallelism = 1),
            )
            assertEquals(
                "21f8123d8ae4297e398c7c68f79406b9ddc48a13098a210c08975848533eef6a",
                derived.toHex(),
            )
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
