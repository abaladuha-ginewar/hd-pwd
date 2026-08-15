package com.hdpwd.shared.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 平台原子文件访问接口，具体实现负责临时文件和原子替换。
 */
interface AtomicByteStore {
    /**
     * 读取最后一次成功提交的完整快照。
     */
    suspend fun read(key: String): ByteArray?

    /**
     * 通过临时文件和原子替换提交快照。
     */
    suspend fun writeAtomically(key: String, bytes: ByteArray)

    /**
     * 删除本地用户全部数据。
     */
    suspend fun delete(key: String)
}

/**
 * 将平台原子字节存储适配为 VaultStore。
 */
class AtomicVaultStore(
    private val storage: AtomicByteStore,
) : VaultStore {
    /**
     * 读取加密快照。
     */
    override suspend fun read(userId: String): ByteArray? = storage.read(userId)

    /**
     * 原子写入加密快照。
     */
    override suspend fun write(userId: String, encryptedSnapshot: ByteArray) {
        storage.writeAtomically(userId, encryptedSnapshot)
    }

    /**
     * 删除本地快照。
     */
    override suspend fun delete(userId: String) {
        storage.delete(userId)
    }
}

/**
 * 变更后立即保存最新状态的本地保存队列。
 */
class VaultSaveQueue(
    private val scope: CoroutineScope,
    private val save: suspend (ByteArray) -> Unit,
) {
    private var saveJob: Job? = null
    private var dirtyState = false

    /**
     * 调度最新加密快照，旧的尚未开始任务会被取消。
     */
    fun schedule(encryptedSnapshot: ByteArray) {
        saveJob?.cancel()
        dirtyState = true
        saveJob = scope.launch {
            try {
                save(encryptedSnapshot)
                dirtyState = false
            } catch (_: Throwable) {
                // 保存失败必须保持 dirty，由上层在下一次生命周期恢复时重试。
                dirtyState = true
            } finally {
                encryptedSnapshot.fill(0)
            }
        }
    }

    /**
     * 返回是否仍有未成功保存的本地状态。
     */
    fun isDirty(): Boolean = dirtyState

    /**
     * 取消尚未开始的保存，不改变 dirty 状态。
     */
    fun cancel() {
        saveJob?.cancel()
        saveJob = null
    }
}
