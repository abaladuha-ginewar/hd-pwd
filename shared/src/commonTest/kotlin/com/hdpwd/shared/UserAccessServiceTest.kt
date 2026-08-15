package com.hdpwd.shared

import com.hdpwd.shared.application.UserAccessService
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.KdfParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 验证解锁前用户索引的唯一性和随机定位。
 */
class UserAccessServiceTest {
    /**
     * 用户记录不得使用用户名作为 Vault 文件定位。
     */
    @Test
    fun createsRandomVaultLocation() {
        val service = UserAccessService(UserIndexFakeCryptoProvider())
        val (records, record) = service.createUserRecord(emptyList(), "alice")
        assertEquals(1, records.size)
        assertNotEquals("alice", record.vaultLocation)
        assertTrue(record.vaultLocation.startsWith("vault/"))
    }

    /**
     * 用户名在当前设备索引中必须唯一。
     */
    @Test
    fun duplicateUserNameIsRejected() {
        val service = UserAccessService(UserIndexFakeCryptoProvider())
        val (records, _) = service.createUserRecord(emptyList(), "alice")
        assertFails { service.createUserRecord(records, "alice") }
    }
}

/**
 * 用户索引测试用的确定性随机门面。
 */
private class UserIndexFakeCryptoProvider : CryptoProvider {
    override fun randomBytes(size: Int): ByteArray = ByteArray(size) { (it + 1).toByte() }
    override suspend fun argon2id(password: ByteArray, salt: ByteArray, parameters: KdfParameters) = password
    override fun hkdfSha256(keyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int) =
        ByteArray(length)
    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray) = plaintext
    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray) = ciphertext
}
