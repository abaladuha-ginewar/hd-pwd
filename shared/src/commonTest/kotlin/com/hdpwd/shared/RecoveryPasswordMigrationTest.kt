package com.hdpwd.shared

import com.hdpwd.shared.application.MigrationTarget
import com.hdpwd.shared.application.RecoveryPasswordMigrationService
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.security.LocalEnvelopeService
import com.hdpwd.shared.storage.VaultCipher
import com.hdpwd.shared.storage.VaultStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * 验证恢复密码迁移的远端失败回滚。
 */
class RecoveryPasswordMigrationTest {
    /**
     * 远端提交失败时本地和已写入目标都恢复旧载荷。
     */
    @Test
    fun migrationRollsBackOnRemoteFailure() = runTest {
        val store = MigrationStore()
        val target = RecordingMigrationTarget(failOnWrite = true)
        val service = RecoveryPasswordMigrationService(
            vaultCipher = MigrationCipher(),
            localEnvelopeService = LocalEnvelopeService(MigrationCrypto(), KdfParameters(16, 1, 1)),
            localStore = store,
        )
        val old = "old".encodeToByteArray()
        store.current = old
        runCatching {
            service.migrate(
                VaultState(EntityId("vault")),
                "old",
                "new",
                "local",
                listOf(target),
            )
        }
        assertContentEquals(old, store.current)
        assertContentEquals(old, target.restored)
    }
}

/**
 * 迁移用 Vault 密码载荷编码器。
 */
private class MigrationCipher : VaultCipher {
    override suspend fun encrypt(recoveryPassword: CharSequence, vault: VaultState) =
        recoveryPassword.toString().encodeToByteArray()
    override suspend fun decrypt(recoveryPassword: CharSequence, encrypted: ByteArray) = VaultState(EntityId("vault"))
}

/**
 * 记录迁移本地写入。
 */
private class MigrationStore : VaultStore {
    var current: ByteArray = byteArrayOf()
    override suspend fun read(userId: String): ByteArray = current
    override suspend fun write(userId: String, encryptedSnapshot: ByteArray) {
        current = encryptedSnapshot.copyOf()
    }
    override suspend fun delete(userId: String) = Unit
}

/**
 * 记录远端写入和回滚的迁移目标。
 */
private class RecordingMigrationTarget(
    private val failOnWrite: Boolean,
) : MigrationTarget {
    var restored: ByteArray = byteArrayOf()
    override suspend fun writeNew(payload: ByteArray) {
        if (failOnWrite) error("remote failure")
    }
    override suspend fun restoreOld(payload: ByteArray) {
        restored = payload.copyOf()
    }
}

/**
 * 迁移测试使用的确定性密码学门面。
 */
private class MigrationCrypto : CryptoProvider {
    override fun randomBytes(size: Int) = ByteArray(size) { it.toByte() }
    override suspend fun argon2id(password: ByteArray, salt: ByteArray, parameters: KdfParameters) =
        ByteArray(32)
    override fun hkdfSha256(keyMaterial: ByteArray, salt: ByteArray, info: ByteArray, length: Int) =
        ByteArray(length)
    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray) =
        key + aad + plaintext
    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray) =
        ciphertext.copyOfRange(key.size + aad.size, ciphertext.size)
}
