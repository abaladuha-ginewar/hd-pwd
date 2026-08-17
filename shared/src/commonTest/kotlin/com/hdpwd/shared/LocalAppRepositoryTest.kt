package com.hdpwd.shared

import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.security.DeviceLockRecord
import com.hdpwd.shared.security.LocalEnvelopeService
import com.hdpwd.shared.security.UserRecoveryEnvelope
import com.hdpwd.shared.storage.AtomicByteStore
import com.hdpwd.shared.storage.LocalAppRepository
import com.hdpwd.shared.storage.PersistedUserMeta
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 验证设备锁与用户索引分离、删除用户不删设备锁。
 */
class LocalAppRepositoryTest {
    /**
     * 用户索引不得包含 DeviceLEK 包装或恢复密码密文。
     */
    @Test
    fun userIndexOmitsKeyMaterial() = runTest {
        val memory = RepositoryMemoryByteStore()
        val repo = LocalAppRepository(memory, RepositoryCrypto())
        val service = LocalEnvelopeService(RepositoryCrypto(), KdfParameters(16, 1, 1))
        val created = service.createDeviceLock("device")
        repo.writeDeviceLock(created.record)
        val envelope = service.sealRecoveryPassword(
            created.deviceKey,
            created.record.generation,
            "user-1",
            "recovery",
        )
        repo.writeEnvelope("user-1", envelope)
        repo.saveUsers(listOf(PersistedUserMeta(id = "user-1", username = "alice")))
        val index = memory.read("users-index")!!.decodeToString()
        assertTrue(index.contains("alice"))
        assertFalse(index.contains("wrappedDeviceLek"))
        assertFalse(index.contains("encryptedRecoveryPassword"))
        assertEquals("alice", repo.listUsers().single().username)
        created.deviceKey.clear()
    }

    /**
     * 世代不匹配的封装必须被识别为待重绑。
     */
    @Test
    fun generationMismatchNeedsRebind() = runTest {
        val memory = RepositoryMemoryByteStore()
        val repo = LocalAppRepository(memory, RepositoryCrypto())
        val service = LocalEnvelopeService(RepositoryCrypto(), KdfParameters(16, 1, 1))
        val first = service.createDeviceLock("device")
        val envelope = service.sealRecoveryPassword(first.deviceKey, first.record.generation, "user-1", "recovery")
        repo.writeEnvelope("user-1", envelope)
        val rotated = service.rotateDeviceLock("new-device")
        repo.writeDeviceLock(rotated.record)
        val stored = repo.readEnvelope("user-1")!!
        assertTrue(stored.needsRebind(rotated.record.generation))
        first.deviceKey.clear()
        rotated.deviceKey.clear()
    }

    /**
     * 删除用户后设备锁与其他用户封装仍在。
     */
    @Test
    fun deleteUserKeepsDeviceLock() = runTest {
        val memory = RepositoryMemoryByteStore()
        val repo = LocalAppRepository(memory, RepositoryCrypto())
        val lock = DeviceLockRecord(
            generation = "gen-1",
            wrappedDeviceLek = byteArrayOf(1, 2, 3),
            preferBiometric = true,
        )
        repo.writeDeviceLock(lock)
        repo.writeDeviceBiometricSealed(byteArrayOf(9, 9))
        repo.writeEnvelope(
            "user-a",
            UserRecoveryEnvelope(encryptedRecoveryPassword = byteArrayOf(4), deviceGeneration = "gen-1"),
        )
        repo.writeEnvelope(
            "user-b",
            UserRecoveryEnvelope(encryptedRecoveryPassword = byteArrayOf(5), deviceGeneration = "gen-1"),
        )
        repo.deleteUser("user-a")
        assertNotNull(repo.readDeviceLock())
        assertNotNull(repo.readDeviceBiometricSealed())
        assertNull(repo.readEnvelope("user-a"))
        assertNotNull(repo.readEnvelope("user-b"))
    }

    /**
     * 跳过重绑时必须保留旧封装，不得删除 Vault。
     */
    @Test
    fun skippedRebindKeepsLegacyEnvelope() = runTest {
        val memory = RepositoryMemoryByteStore()
        val repo = LocalAppRepository(memory, RepositoryCrypto())
        val service = LocalEnvelopeService(RepositoryCrypto(), KdfParameters(16, 1, 1))
        val legacy = service.createLegacyEnvelope("recovery", "old-local")
        repo.writeEnvelope("user-legacy", legacy)
        repo.writeDeviceLock(
            DeviceLockRecord(generation = "new-gen", wrappedDeviceLek = byteArrayOf(1)),
        )
        val stored = repo.readEnvelope("user-legacy")!!
        assertTrue(stored.isLegacy())
        assertTrue(stored.needsRebind("new-gen"))
    }
}

/**
 * 仓储测试用内存字节存储。
 */
private class RepositoryMemoryByteStore : AtomicByteStore {
    private val map = mutableMapOf<String, ByteArray>()

    override suspend fun read(key: String): ByteArray? = map[key]?.copyOf()

    override suspend fun writeAtomically(key: String, bytes: ByteArray) {
        map[key] = bytes.copyOf()
    }

    override suspend fun delete(key: String) {
        map.remove(key)
    }
}

/**
 * 仓储测试用确定性密码学门面。
 */
private class RepositoryCrypto : CryptoProvider {
    private var counter = 0

    override fun randomBytes(size: Int): ByteArray {
        counter += 1
        return ByteArray(size) { index -> (counter + index + 3).toByte() }
    }
    override suspend fun argon2id(password: ByteArray, salt: ByteArray, parameters: KdfParameters): ByteArray =
        ByteArray(32) { index -> password[index % password.size] }
    override fun hkdfSha256(keyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray =
        ByteArray(length) { index -> keyMaterial[index % keyMaterial.size] }
    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray =
        key.copyOf(32) + aad + plaintext
    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray =
        ciphertext.copyOfRange(32 + aad.size, ciphertext.size)
}
