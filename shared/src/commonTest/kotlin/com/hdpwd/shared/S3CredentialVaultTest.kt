package com.hdpwd.shared

import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.sync.S3Credentials
import com.hdpwd.shared.sync.S3CredentialVault
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 S3 Secret 只以加密载荷保存并能临时解封。
 */
class S3CredentialVaultTest {
    /**
     * 凭据加密往返后内容一致。
     */
    @Test
    fun credentialsRoundTrip() = runTest {
        val vault = S3CredentialVault(CredentialCrypto(), KdfParameters(16, 1, 1))
        val credentials = S3Credentials("access", "secret".encodeToByteArray())
        val encrypted = vault.seal(ByteArray(32) { 1 }, credentials)
        credentials.clear()
        val restored = vault.open(ByteArray(32) { 1 }, encrypted)
        assertEquals("access", restored.accessKeyId)
        restored.useSecret { assertEquals("secret", it.decodeToString()) }
        restored.clear()

        val sealed = vault.sealWithRecoveryPassword("recovery", S3Credentials("ak", "sk".encodeToByteArray()))
        val opened = vault.openWithRecoveryPassword(
            "recovery",
            sealed.encryptedCredentialsHex,
            sealed.credentialsSaltHex,
        )
        assertEquals("ak", opened.accessKeyId)
        opened.useSecret { assertEquals("sk", it.decodeToString()) }
        opened.clear()
    }
}

/**
 * S3 凭据结构测试用的确定性密码学门面。
 */
private class CredentialCrypto : CryptoProvider {
    override fun randomBytes(size: Int) = ByteArray(size) { it.toByte() }
    override suspend fun argon2id(password: ByteArray, salt: ByteArray, parameters: KdfParameters) =
        ByteArray(32) { 1 }
    override fun hkdfSha256(keyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int) =
        ByteArray(length) { 1 }
    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray) =
        aad + plaintext
    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray) =
        ciphertext.copyOfRange(aad.size, ciphertext.size)
}
