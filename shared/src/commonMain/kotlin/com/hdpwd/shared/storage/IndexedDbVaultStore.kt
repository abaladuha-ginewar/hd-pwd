package com.hdpwd.shared.storage

/**
 * Web IndexedDB 的最小事务适配接口。
 */
interface IndexedDbPort {
    /**
     * 在对象仓库中读取加密 Blob。
     */
    suspend fun get(store: String, key: String): ByteArray?

    /**
     * 在单事务中写入加密 Blob 和 dirty 状态。
     */
    suspend fun put(store: String, key: String, value: ByteArray)

    /**
     * 从对象仓库删除本地用户数据。
     */
    suspend fun delete(store: String, key: String)
}

/**
 * 将 IndexedDB 端口适配为 VaultStore。
 *
 * Web 生产路径优先使用 localStorage 原子字节存储（IndexedDB 在 Kotlin/Wasm 上兼容性较差）。
 */
class IndexedDbVaultStore(
    private val database: IndexedDbPort,
    private val storeName: String = "vault-snapshots",
) : VaultStore {
    /**
     * 读取加密 Vault Blob。
     */
    override suspend fun read(userId: String): ByteArray? = database.get(storeName, userId)

    /**
     * 写入加密 Vault Blob。
     */
    override suspend fun write(userId: String, encryptedSnapshot: ByteArray) {
        database.put(storeName, userId, encryptedSnapshot)
    }

    /**
     * 删除加密 Vault Blob。
     */
    override suspend fun delete(userId: String) {
        database.delete(storeName, userId)
    }
}
