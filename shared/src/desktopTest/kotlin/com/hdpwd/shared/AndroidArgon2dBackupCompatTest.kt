package com.hdpwd.shared

import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.EncryptedContainerCodec
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.crypto.platformCryptoProvider
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.PasswordPolicy
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.storage.AuthenticatedVaultCipher
import com.hdpwd.shared.storage.BackupService
import com.hdpwd.shared.storage.vaultJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 验证 Windows 能解开 diglol Android 错用 Argon2d 写出的备份。
 */
class AndroidArgon2dBackupCompatTest {
    /**
     * 同一口令下 Argon2id 与 Argon2d 必须得到不同根密钥，否则兼容回退没有意义。
     */
    @Test
    fun argon2idDiffersFromArgon2d() {
        runBlocking {
            val crypto = platformCryptoProvider()
            val salt = ByteArray(16) { it.toByte() }
            val password = "compat-recovery".encodeToByteArray()
            val parameters = KdfParameters(memoryKiB = 16, iterations = 1, parallelism = 1)
            val id = crypto.argon2id(password, salt, parameters)
            val d = crypto.argon2d(password, salt, parameters)
            assertEquals(32, id.size)
            assertEquals(32, d.size)
            assertFalse(id.contentEquals(d))
        }
    }

    /**
     * 用 Argon2d + BackupKey 域密封的容器，生产导入路径必须能还原条目。
     */
    @Test
    fun importAcceptsDiglolAndroidArgon2dBackup() {
        runBlocking {
            val recovery = "compat-recovery"
            val vault = VaultState(
                vaultId = EntityId("39ee0e0de31619607ae7f1f695dc07aa"),
                entries = listOf(
                    PasswordEntry(
                        id = EntityId("entry-1"),
                        parentId = null,
                        key = "GitHub.Work",
                        title = "GitHub",
                        policy = PasswordPolicy(),
                    ),
                ),
            )
            val bytes = sealLikeDiglolAndroid(recovery, vault)
            val imported = BackupService.production(platformCryptoProvider()).import(recovery, bytes)
            assertEquals(vault.vaultId, imported.vaultId)
            assertEquals("GitHub", imported.entries.single().title)
        }
    }

    /**
     * 错误恢复密码在 Argon2d 回退后仍应失败。
     */
    @Test
    fun wrongPasswordStillFailsForArgon2dBackup() {
        runBlocking {
            val vault = VaultState(EntityId("vault-compat"))
            val bytes = sealLikeDiglolAndroid("correct-password", vault)
            val cipher = AuthenticatedVaultCipher(
                crypto = platformCryptoProvider(),
                kdfParameters = compatKdf,
                keyDomain = CryptoDomains.BACKUP,
            )
            var failed = false
            try {
                cipher.decrypt("wrong-password", bytes)
            } catch (_: Throwable) {
                failed = true
            }
            assertEquals(true, failed)
        }
    }

    private suspend fun sealLikeDiglolAndroid(
        recoveryPassword: String,
        vault: VaultState,
    ): ByteArray {
        val crypto = platformCryptoProvider()
        val salt = crypto.randomBytes(16)
        val password = recoveryPassword.encodeToByteArray()
        val rootKey = crypto.argon2d(password, salt, compatKdf)
        val dataKey = crypto.hkdfSha256(
            keyMaterial = rootKey,
            salt = salt,
            info = CryptoDomains.BACKUP.encodeToByteArray(),
            length = 32,
        )
        return try {
            EncryptedContainerCodec(crypto).seal(
                key = dataKey,
                plaintext = vaultJson.encodeToString(vault).encodeToByteArray(),
                kdfParameters = compatKdf,
                associatedData = vault.vaultId.value.encodeToByteArray(),
                salt = salt,
            )
        } finally {
            password.fill(0)
            rootKey.fill(0)
            dataKey.fill(0)
        }
    }

    private companion object {
        val compatKdf = KdfParameters(memoryKiB = 16, iterations = 1, parallelism = 1)
    }
}
