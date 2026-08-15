package com.hdpwd.shared.sync

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * S3 对象操作的最小平台无关接口。
 */
interface S3ObjectStore {
    /**
     * 列出目标中指定前缀的对象。
     */
    suspend fun list(prefix: String): List<String>

    /**
     * 读取对象密文。
     */
    suspend fun get(path: String): ByteArray

    /**
     * 上传对象密文。
     */
    suspend fun put(path: String, content: ByteArray)
}

/**
 * 为多个 S3 目标分别管理健康状态的同步调度器。
 */
class SyncScheduler(
    private val scope: CoroutineScope,
    private val sync: suspend (SyncTarget) -> Unit,
    private val localSaveCompleted: suspend () -> Boolean = { true },
    private val quietPeriodMillis: Long = 5_000L,
) {
    private val jobs = mutableMapOf<EntityId, Job>()
    private var generation = 0L

    /**
     * 在最后一次本地修改后等待静默期再同步。
     */
    fun schedule(targets: List<SyncTarget>) {
        generation++
        val scheduledGeneration = generation
        targets.filter { it.enabled && it.confirmed }.forEach { target ->
            jobs[target.id]?.cancel()
            jobs[target.id] = scope.launch {
                delay(quietPeriodMillis)
                if (scheduledGeneration == generation && localSaveCompleted()) {
                    sync(target)
                }
            }
        }
    }

    /**
     * 取消尚未开始的同步工作。
     */
    fun cancel() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }
}
