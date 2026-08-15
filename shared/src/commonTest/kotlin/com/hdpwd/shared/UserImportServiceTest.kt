package com.hdpwd.shared

import com.hdpwd.shared.application.UserAccessService
import com.hdpwd.shared.application.UserImportService
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.security.LocalEnvelopeService
import com.hdpwd.shared.storage.BackupService
import com.hdpwd.shared.storage.VaultCipher
import com.hdpwd.shared.storage.VaultStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证备份导入完成前不会提交半成品用户。
 */
class UserImportServiceTest {
    /**
     * 成功导入应同时得到 Vault、用户索引和本机封装。
     */
    @Test
    fun importsAtomically() = runTest {
        val vault = VaultState(EntityId("vault"))
        val store = RecordingImportStore()
        val service = UserImportService(
            backupService = BackupService(FakeVaultCipher(vault)),
            localEnvelopeService = LocalEnvelopeService(
                ImportCryptoProvider(),
                KdfParameters(16, 1, 1),
            ),
            vaultStore = store,
            userAccessService = UserAccessService(ImportCryptoProvider()),
        )
        val imported = service.import(
            currentUsers = emptyList(),
            username = "alice",
            recoveryPassword = "recovery",
            localPassword = "local",
            backup = byteArrayOf(1, 2),
        )
        assertEquals("alice", imported.user.username)
        assertEquals("vault", store.writtenUser)
        assertEquals(vault, imported.vault)
    }
}

/**
 * 导入测试使用的固定 Vault 解密器。
 */
private class FakeVaultCipher(
    private val vault: VaultState,
) : VaultCipher {
    override suspend fun encrypt(recoveryPassword: CharSequence, vault: VaultState): ByteArray = byteArrayOf(1)
    override suspend fun decrypt(recoveryPassword: CharSequence, encrypted: ByteArray): VaultState = vault
}

/**
 * 记录导入写入的测试存储。
 */
private class RecordingImportStore : VaultStore {
    var writtenUser: String? = null
    override suspend fun read(userId: String): ByteArray? = null
    override suspend fun write(userId: String, encryptedSnapshot: ByteArray) {
        writtenUser = userId
    }
    override suspend fun delete(userId: String) = Unit
}

/**
 * 导入流程测试用的确定性密码学门面。
 */
private class ImportCryptoProvider : CryptoProvider {
    override fun randomBytes(size: Int): ByteArray = ByteArray(size) { (it + 1).toByte() }
    override suspend fun argon2id(password: ByteArray, salt: ByteArray, parameters: KdfParameters) =
        ByteArray(32) { index -> password[index % password.size] }
    override fun hkdfSha256(keyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int) =
        ByteArray(length)
    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray) =
        key.copyOf(32) + aad + plaintext
    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray) =
        ciphertext.copyOfRange(32 + aad.size, ciphertext.size)
}
