package com.hdpwd.shared

import com.hdpwd.shared.application.LocalUserDeletionService
import com.hdpwd.shared.application.LocalUserRecord
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.storage.VaultStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * 验证删除用户只影响本地数据并要求二次确认。
 */
class LocalUserDeletionServiceTest {
    /**
     * 未确认时必须拒绝删除。
     */
    @Test
    fun deletionRequiresConfirmation() = runTest {
        val store = RecordingVaultStore()
        val service = LocalUserDeletionService(store)
        assertFails {
            runTest {
                service.delete(
                    listOf(LocalUserRecord(EntityId("u"), "alice", "vault/u.dat")),
                    EntityId("u"),
                    confirmed = false,
                )
            }
        }
        assertEquals(null, store.deletedUser)
    }

    /**
     * 确认后只删除本地 Vault。
     */
    @Test
    fun confirmedDeletionRemovesLocalVaultOnly() = runTest {
        val store = RecordingVaultStore()
        val service = LocalUserDeletionService(store)
        val remaining = service.delete(
            listOf(LocalUserRecord(EntityId("u"), "alice", "vault/u.dat")),
            EntityId("u"),
            confirmed = true,
        )
        assertEquals("u", store.deletedUser)
        assertEquals(emptyList(), remaining)
    }
}

/**
 * 记录本地删除调用的测试存储。
 */
private class RecordingVaultStore : VaultStore {
    var deletedUser: String? = null
    override suspend fun read(userId: String): ByteArray? = null
    override suspend fun write(userId: String, encryptedSnapshot: ByteArray) = Unit
    override suspend fun delete(userId: String) {
        deletedUser = userId
    }
}
