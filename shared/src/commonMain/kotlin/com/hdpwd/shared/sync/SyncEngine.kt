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
 *
 * 每次 [schedule] 会取消未执行的任务并重新开始静默计时，从而实现
 * 「无修改 5 秒后同步；5 秒内再修改则重新计时」。
 *
 * 静默期结束后若本地尚未保存完成，会等待保存完成再同步，而不是静默丢弃任务。
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
     *
     * @param quietPeriodMillis 覆盖默认静默期；传 0 表示立即同步。
     */
    fun schedule(
        targets: List<SyncTarget>,
        quietPeriodMillis: Long = this.quietPeriodMillis,
    ) {
        generation++
        val scheduledGeneration = generation
        val ready = targets.filter { it.enabled && it.confirmed }
        // 取消已不在就绪集合中的任务
        jobs.keys.filter { id -> ready.none { it.id == id } }.forEach { id ->
            jobs.remove(id)?.cancel()
        }
        ready.forEach { target ->
            jobs[target.id]?.cancel()
            jobs[target.id] = scope.launch {
                if (quietPeriodMillis > 0) {
                    delay(quietPeriodMillis)
                }
                while (scheduledGeneration == generation && !localSaveCompleted()) {
                    delay(50)
                }
                if (scheduledGeneration == generation && localSaveCompleted()) {
                    sync(target)
                }
            }
        }
    }

    /**
     * 是否仍有等待静默期或正在执行的同步任务。
     */
    fun hasPendingJobs(): Boolean = jobs.values.any { it.isActive }

    /**
     * 挂起直到当前调度代次的任务全部结束（或已取消）。
     */
    suspend fun awaitIdle() {
        while (hasPendingJobs()) {
            delay(50)
        }
    }

    /**
     * 取消尚未开始的同步工作。
     */
    fun cancel() {
        generation++
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }
}
