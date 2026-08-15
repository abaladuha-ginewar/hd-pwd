package com.hdpwd.shared

import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.storage.AuthenticatedVaultCipher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 VaultState 经过 DataKey 派生和容器编解码后可往返。
 */
class AuthenticatedVaultCipherTest {
    /**
     * Vault 业务状态必须保持完整。
     */
    @Test
    fun vaultStateRoundTrip() {
        val cipher = AuthenticatedVaultCipher(
            crypto = DeterministicCryptoProvider(),
            kdfParameters = KdfParameters(16, 1, 1),
        )
        val original = VaultState(EntityId("vault"))
        var encrypted = byteArrayOf()
        var restored = VaultState(EntityId("empty"))
        runTest {
            encrypted = cipher.encrypt("recovery", original)
            restored = cipher.decrypt("recovery", encrypted)
        }
        assertEquals(original, restored)
    }
}

/**
 * 只用于 Vault 编解码结构测试的确定性密码学门面。
 */
private class DeterministicCryptoProvider : CryptoProvider {
    override fun randomBytes(size: Int): ByteArray = ByteArray(size) { (it + 1).toByte() }
    override suspend fun argon2id(password: ByteArray, salt: ByteArray, parameters: KdfParameters) =
        password.copyOf(32)
    override fun hkdfSha256(keyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int) =
        ByteArray(length) { index -> keyMaterial[index % keyMaterial.size] }
    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray) =
        aad + plaintext
    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray) =
        ciphertext.copyOfRange(aad.size, ciphertext.size)
}
