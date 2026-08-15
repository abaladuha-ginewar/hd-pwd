package com.hdpwd.shared

import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.crypto.PortableSha256
import com.hdpwd.shared.security.LocalEnvelopeService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * 验证本机 LEK 封装、主密码校验和恢复密码临时清理。
 */
class LocalEnvelopeServiceTest {
    /**
     * 正确本机主密码应能解封 LEK 并读取恢复密码。
     */
    @Test
    fun localEnvelopeRoundTrip() {
        runTest {
            val service = LocalEnvelopeService(
                DeterministicEnvelopeCryptoProvider(),
                KdfParameters(16, 1, 1),
            )
            val record = service.create("recovery-password", "local-password")
            val key = service.unlockLocalKey(record, "local-password")
            service.withRecoveryPassword(record, key) { recovery ->
                assertEquals("recovery-password", recovery.toString())
            }
            key.clear()
        }
    }

    /**
     * 错误本机主密码必须在认证解密层失败。
     */
    @Test
    fun wrongLocalPasswordIsRejected() {
        runTest {
            val service = LocalEnvelopeService(
                DeterministicEnvelopeCryptoProvider(),
                KdfParameters(16, 1, 1),
            )
            val record = service.create("recovery-password", "local-password")
            assertFails { service.unlockLocalKey(record, "wrong-password") }
        }
    }

    /**
     * 重置本机主密码后旧密码失效、新密码可解封同一恢复密码。
     */
    @Test
    fun resetLocalPasswordRewrapsSameRecoveryPassword() {
        runTest {
            val service = LocalEnvelopeService(
                DeterministicEnvelopeCryptoProvider(),
                KdfParameters(16, 1, 1),
            )
            val original = service.create("recovery-password", "old-local")
            val reset = service.resetLocalPassword(original, "old-local", "new-local")
            assertFails { service.unlockLocalKey(reset, "old-local") }
            val key = service.unlockLocalKey(reset, "new-local")
            service.withRecoveryPassword(reset, key) { recovery ->
                assertEquals("recovery-password", recovery.toString())
            }
            key.clear()
        }
    }
}

/**
 * 本机封装结构测试的确定性密码学门面。
 */
private class DeterministicEnvelopeCryptoProvider : CryptoProvider {
    override fun randomBytes(size: Int): ByteArray = ByteArray(size) { (it + 7).toByte() }
    override suspend fun argon2id(password: ByteArray, salt: ByteArray, parameters: KdfParameters): ByteArray =
        PortableSha256.digest(password + salt)

    override fun hkdfSha256(keyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray =
        ByteArray(length) { index -> keyMaterial[index % keyMaterial.size] }

    override suspend fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray = key.copyOf(32) + aad + plaintext

    override suspend fun open(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(ciphertext.copyOfRange(0, 32).contentEquals(key)) { "认证失败" }
        return ciphertext.copyOfRange(32 + aad.size, ciphertext.size)
    }
}
