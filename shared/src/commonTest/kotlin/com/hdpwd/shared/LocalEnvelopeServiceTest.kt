package com.hdpwd.shared

import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.crypto.PortableSha256
import com.hdpwd.shared.security.LocalEnvelopeService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证设备级 DeviceLEK 包装、按用户恢复密码封装和世代隔离。
 */
class LocalEnvelopeServiceTest {
    /**
     * 正确主密码应能解开 DeviceLEK 并读取绑定用户的恢复密码。
     */
    @Test
    fun deviceLockRoundTrip() {
        runTest {
            val service = service()
            val created = service.createDeviceLock("device-password")
            val key = service.unlockWithMasterPassword(created.record, "device-password")
            val envelope = service.sealRecoveryPassword(key, created.record.generation, "user-a", "recovery-password")
            service.withRecoveryPassword(envelope, key, "user-a", created.record.generation) { recovery ->
                assertEquals("recovery-password", recovery.toString())
            }
            key.clear()
            created.deviceKey.clear()
        }
    }

    /**
     * 错误主密码必须在认证解密层失败。
     */
    @Test
    fun wrongMasterPasswordIsRejected() {
        runTest {
            val service = service()
            val created = service.createDeviceLock("device-password")
            assertFails { service.unlockWithMasterPassword(created.record, "wrong-password") }
            created.deviceKey.clear()
        }
    }

    /**
     * 修改主密码不更换世代，旧密码失效，已绑定用户封装仍可用。
     */
    @Test
    fun rewrapKeepsGenerationAndUserEnvelopes() {
        runTest {
            val service = service()
            val created = service.createDeviceLock("old-local")
            val envelope = service.sealRecoveryPassword(
                created.deviceKey,
                created.record.generation,
                "user-a",
                "recovery-password",
            )
            val rewrapped = service.rewrapMasterPassword(created.record, created.deviceKey, "new-local")
            assertEquals(created.record.generation, rewrapped.generation)
            assertFails { service.unlockWithMasterPassword(rewrapped, "old-local") }
            val key = service.unlockWithMasterPassword(rewrapped, "new-local")
            service.withRecoveryPassword(envelope, key, "user-a", rewrapped.generation) { recovery ->
                assertEquals("recovery-password", recovery.toString())
            }
            key.clear()
            created.deviceKey.clear()
        }
    }

    /**
     * 轮换设备锁后旧世代用户必须待重绑。
     */
    @Test
    fun rotateLeavesOtherUsersUnbound() {
        runTest {
            val service = service()
            val original = service.createDeviceLock("old-local")
            val userA = service.sealRecoveryPassword(
                original.deviceKey,
                original.record.generation,
                "user-a",
                "recovery-a",
            )
            val userB = service.sealRecoveryPassword(
                original.deviceKey,
                original.record.generation,
                "user-b",
                "recovery-b",
            )
            val rotated = service.rotateDeviceLock("new-local")
            assertTrue(userB.needsRebind(rotated.record.generation))
            assertFails {
                service.withRecoveryPassword(userB, rotated.deviceKey, "user-b", rotated.record.generation) { }
            }
            val reboundA = service.sealRecoveryPassword(
                rotated.deviceKey,
                rotated.record.generation,
                "user-a",
                "recovery-a",
            )
            assertFalse(reboundA.needsRebind(rotated.record.generation))
            assertTrue(userA.needsRebind(rotated.record.generation))
            original.deviceKey.clear()
            rotated.deviceKey.clear()
        }
    }

    /**
     * 不得用另一用户 id 解开封装，即使 DeviceLEK 相同。
     */
    @Test
    fun userIdMismatchIsRejected() {
        runTest {
            val service = service()
            val created = service.createDeviceLock("device-password")
            val envelope = service.sealRecoveryPassword(
                created.deviceKey,
                created.record.generation,
                "user-a",
                "recovery-password",
            )
            assertFails {
                service.withRecoveryPassword(
                    envelope,
                    created.deviceKey,
                    "user-b",
                    created.record.generation,
                ) { }
            }
            created.deviceKey.clear()
        }
    }

    /**
     * 旧每用户主密码封装仍可用旧密码解开并读出恢复密码。
     */
    @Test
    fun legacyEnvelopeCanBeMigratedWithOldLocalPassword() {
        runTest {
            val service = service()
            val legacy = service.createLegacyEnvelope("recovery-password", "old-user-local")
            assertTrue(legacy.isLegacy())
            val legacyKey = service.unlockLegacyLocalKey(legacy, "old-user-local")
            service.withLegacyRecoveryPassword(legacy, legacyKey) { recovery ->
                assertEquals("recovery-password", recovery.toString())
            }
            legacyKey.clear()
        }
    }

    private fun service() = LocalEnvelopeService(
        DeterministicEnvelopeCryptoProvider(),
        KdfParameters(16, 1, 1),
    )
}

/**
 * 本机封装结构测试的确定性密码学门面。
 */
private class DeterministicEnvelopeCryptoProvider : CryptoProvider {
    private var counter = 0

    override fun randomBytes(size: Int): ByteArray {
        counter += 1
        return ByteArray(size) { index -> (counter + index).toByte() }
    }
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
        require(ciphertext.copyOfRange(0, 32).contentEquals(key.copyOf(32))) { "认证失败" }
        return ciphertext.copyOfRange(32 + aad.size, ciphertext.size)
    }
}
