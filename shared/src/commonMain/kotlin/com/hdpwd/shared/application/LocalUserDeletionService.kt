package com.hdpwd.shared.application

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.storage.VaultStore

/**
 * 只删除本机用户数据，不向远端 S3 发起删除请求。
 */
class LocalUserDeletionService(
    private val vaultStore: VaultStore,
    private val userAccessService: UserAccessService = UserAccessService(),
) {
    /**
     * 二次确认通过后删除本地快照和索引记录。
     */
    suspend fun delete(
        currentUsers: List<LocalUserRecord>,
        userId: EntityId,
        confirmed: Boolean,
    ): List<LocalUserRecord> {
        require(confirmed) { "删除用户需要二次确认" }
        vaultStore.delete(userId.value)
        return userAccessService.removeUser(currentUsers, userId)
    }
}
