package com.hdpwd.shared.sync

import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.Folder
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.domain.Tombstone
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.platform.platformHttpClient
import com.hdpwd.shared.storage.AuthenticatedVaultCipher
import com.hdpwd.shared.storage.DefaultKdfParameters
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 将本地 Vault 与远端共享快照双向同步。
 *
 * 对象路径只使用 S3 配置中的存储目录；同一目录 + 同一恢复密码即视为同一密码库，
 * 与本机用户名、本地 vaultId 无关。
 */
class VaultS3SyncService(
    private val crypto: CryptoProvider = platformCryptoProviderOrDefault(),
    private val httpClient: HttpClient = platformHttpClient(),
    private val now: () -> String = ::awsAmzDate,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val credentialVault = S3CredentialVault(crypto, DefaultKdfParameters)
    private val vaultCipher = AuthenticatedVaultCipher(
        crypto = crypto,
        kdfParameters = DefaultKdfParameters,
        keyDomain = CryptoDomains.BACKUP,
    )

    /**
     * 同步单个目标：先拉取同目录共享快照（若存在），合并后再上传，并返回合并后的 Vault。
     *
     * 拉取失败（非 404）不会用本地数据覆盖远端，避免把其它设备的新版本写丢。
     */
    suspend fun syncTarget(
        target: SyncTarget,
        vault: VaultState,
        recoveryPassword: CharSequence,
    ): VaultSyncResult {
        if (!target.enabled || !target.confirmed) {
            return VaultSyncResult(
                target = target.copy(status = SyncStatus.IDLE, lastErrorCode = null),
                vault = vault,
            )
        }
        val credentials = try {
            credentialVault.openWithRecoveryPassword(
                recoveryPassword = recoveryPassword,
                encryptedCredentialsHex = target.encryptedCredentialsHex,
                credentialsSaltHex = target.credentialsSaltHex,
            )
        } catch (failure: Throwable) {
            return VaultSyncResult(
                target = target.copy(
                    status = SyncStatus.FAILED,
                    lastErrorCode = (failure.message ?: "凭据无效").take(64),
                ),
                vault = vault,
            )
        }
        return try {
            val store = KtorS3ObjectStore(
                client = httpClient,
                endpoint = target.endpoint,
                bucket = target.bucket,
                region = target.region,
                credentials = credentials,
                clock = now,
                forcePathStyle = S3ProviderPreset.fromProviderCode(target.provider).forcePathStyle,
            )
            val path = S3ObjectPaths.sharedVaultBlob(target.objectPrefix)
            val remote = when (val loaded = loadRemoteVault(store, path, recoveryPassword)) {
                RemoteVaultLoad.Missing -> null
                is RemoteVaultLoad.Found -> loaded.vault
            }
            if (remote != null && sameBusinessContent(vault, remote)) {
                val alignedSeq = maxOf(vault.contentVersion(), remote.contentVersion())
                val aligned = vault.copy(
                    syncTargets = mergeSyncTargets(vault.syncTargets, remote.syncTargets),
                    deviceSequence = alignedSeq,
                )
                // 仅当远端序号落后时补写序号，不改业务内容
                if (remote.deviceSequence < alignedSeq) {
                    store.put(path, vaultCipher.encrypt(recoveryPassword, toSharedVault(aligned)))
                }
                return VaultSyncResult(
                    target = target.markSyncSuccess(alignedSeq),
                    vault = aligned,
                    changed = false,
                )
            }
            val merged = mergeSharedVault(local = vault, remote = remote)
            if (shouldUpload(merged = merged, remote = remote)) {
                store.put(
                    path,
                    vaultCipher.encrypt(recoveryPassword, toSharedVault(merged)),
                )
            }
            val applied = if (
                remote != null &&
                remote.latestContentMutation() > merged.latestContentMutation()
            ) {
                // 防御：合并结果不应比远端旧；若出现则采用远端业务内容
                remote.copy(
                    vaultId = vault.vaultId,
                    syncTargets = mergeSyncTargets(vault.syncTargets, remote.syncTargets),
                    deviceSequence = maxOf(vault.contentVersion(), remote.contentVersion()),
                )
            } else {
                merged
            }
            VaultSyncResult(
                target = target.markSyncSuccess(applied.contentVersion()),
                vault = applied,
                changed = remote == null || !sameBusinessContent(vault, applied),
            )
        } catch (failure: Throwable) {
            VaultSyncResult(
                target = target.copy(
                    status = SyncStatus.FAILED,
                    lastErrorCode = (failure.message ?: failure::class.simpleName ?: "sync-error").take(64),
                ),
                vault = vault,
                changed = false,
            )
        } finally {
            credentials.clear()
        }
    }

    private suspend fun loadRemoteVault(
        store: KtorS3ObjectStore,
        path: String,
        recoveryPassword: CharSequence,
    ): RemoteVaultLoad {
        val bytes = try {
            store.get(path)
        } catch (failure: Throwable) {
            if (isRemoteMissing(failure)) return RemoteVaultLoad.Missing
            throw failure
        }
        val vault = vaultCipher.decrypt(recoveryPassword, bytes)
        return RemoteVaultLoad.Found(vault)
    }

    /**
     * 仅在合并结果不比远端旧时上传，防止本机旧快照覆盖其它设备的新版本。
     */
    internal fun shouldUpload(merged: VaultState, remote: VaultState?): Boolean {
        if (remote == null) return true
        val cmp = merged.latestContentMutation().compareTo(remote.latestContentMutation())
        return when {
            cmp > 0 -> true
            cmp < 0 -> false
            else -> !sameBusinessContent(merged, remote) ||
                merged.deviceSequence > remote.deviceSequence
        }
    }

    private fun isRemoteMissing(failure: Throwable): Boolean {
        val message = failure.message.orEmpty()
        return message.contains("404") ||
            message.contains("NoSuchKey", ignoreCase = true) ||
            message.contains("Not Found", ignoreCase = true)
    }

    /**
     * 比较密码库业务内容（忽略本机 vaultId、库级序号与同步状态；修改戳参与比较）。
     */
    internal fun sameBusinessContent(left: VaultState, right: VaultState): Boolean =
        left.folders == right.folders &&
            left.entries == right.entries &&
            left.tombstones == right.tombstones &&
            left.conflicts == right.conflicts

    /**
     * 按实体 [MutationStamp] 做 LWW 合并：同 id 取较新修改；墓碑与存活对象也按戳裁决。
     *
     * 戳相等时，优先保留「整库最新修改戳」更大的一侧，避免本机旧副本在平局时盖住远端新版本。
     */
    internal fun mergeSharedVault(local: VaultState, remote: VaultState?): VaultState {
        if (remote == null) {
            return local.copy(deviceSequence = local.deviceSequence.coerceAtLeast(1))
        }
        if (sameBusinessContent(local, remote)) {
            return local.copy(
                syncTargets = mergeSyncTargets(local.syncTargets, remote.syncTargets),
                deviceSequence = maxOf(local.contentVersion(), remote.contentVersion()),
            )
        }
        val preferLocalOnTie = local.latestContentMutation() >= remote.latestContentMutation()
        val tombstones = mergeTombstones(local.tombstones, remote.tombstones, preferLocalOnTie)
        val folders = mergeFolders(local.folders, remote.folders, tombstones, preferLocalOnTie)
        val entries = mergeEntries(local.entries, remote.entries, tombstones, preferLocalOnTie)
        val maxRevision = maxOf(
            local.contentVersion(),
            remote.contentVersion(),
            folders.maxOfOrNull { it.mutation.revision } ?: 0L,
            entries.maxOfOrNull { it.mutation.revision } ?: 0L,
            tombstones.maxOfOrNull { it.revision } ?: 0L,
        )
        return local.copy(
            folders = folders,
            entries = entries,
            tombstones = tombstones.filter { stone ->
                val folderAlive = folders.any { it.id == stone.entityId }
                val entryAlive = entries.any { it.id == stone.entityId }
                !folderAlive && !entryAlive
            },
            conflicts = (local.conflicts + remote.conflicts).distinctBy { it.id },
            syncTargets = mergeSyncTargets(local.syncTargets, remote.syncTargets),
            deviceSequence = maxRevision,
        )
    }

    private fun mergeTombstones(
        local: List<Tombstone>,
        remote: List<Tombstone>,
        preferLocalOnTie: Boolean,
    ): List<Tombstone> {
        val byId = LinkedHashMap<EntityId, Tombstone>()
        fun consider(stone: Tombstone, preferOnTie: Boolean) {
            val existing = byId[stone.entityId]
            byId[stone.entityId] = when {
                existing == null -> stone
                stone.mutation > existing.mutation -> stone
                stone.mutation < existing.mutation -> existing
                preferOnTie -> stone
                else -> existing
            }
        }
        if (preferLocalOnTie) {
            remote.forEach { consider(it, preferOnTie = false) }
            local.forEach { consider(it, preferOnTie = true) }
        } else {
            local.forEach { consider(it, preferOnTie = false) }
            remote.forEach { consider(it, preferOnTie = true) }
        }
        return byId.values.toList()
    }

    private fun mergeFolders(
        local: List<Folder>,
        remote: List<Folder>,
        tombstones: List<Tombstone>,
        preferLocalOnTie: Boolean,
    ): List<Folder> {
        val tombstoneById = tombstones.associateBy { it.entityId }
        val byId = LinkedHashMap<EntityId, Folder>()
        fun consider(folder: Folder, preferOnTie: Boolean) {
            val stone = tombstoneById[folder.id]
            if (stone != null && stone.mutation >= folder.mutation) return
            val existing = byId[folder.id]
            byId[folder.id] = when {
                existing == null -> folder
                folder.mutation > existing.mutation -> folder
                folder.mutation < existing.mutation -> existing
                preferOnTie -> folder
                else -> existing
            }
        }
        if (preferLocalOnTie) {
            remote.forEach { consider(it, preferOnTie = false) }
            local.forEach { consider(it, preferOnTie = true) }
        } else {
            local.forEach { consider(it, preferOnTie = false) }
            remote.forEach { consider(it, preferOnTie = true) }
        }
        return byId.values.toList()
    }

    private fun mergeEntries(
        local: List<PasswordEntry>,
        remote: List<PasswordEntry>,
        tombstones: List<Tombstone>,
        preferLocalOnTie: Boolean,
    ): List<PasswordEntry> {
        val tombstoneById = tombstones.associateBy { it.entityId }
        val byId = LinkedHashMap<EntityId, PasswordEntry>()
        fun consider(entry: PasswordEntry, preferOnTie: Boolean) {
            val stone = tombstoneById[entry.id]
            if (stone != null && stone.mutation >= entry.mutation) return
            val existing = byId[entry.id]
            byId[entry.id] = when {
                existing == null -> entry
                entry.mutation > existing.mutation -> entry
                entry.mutation < existing.mutation -> existing
                preferOnTie -> entry
                else -> existing
            }
        }
        if (preferLocalOnTie) {
            remote.forEach { consider(it, preferOnTie = false) }
            local.forEach { consider(it, preferOnTie = true) }
        } else {
            local.forEach { consider(it, preferOnTie = false) }
            remote.forEach { consider(it, preferOnTie = true) }
        }
        // 同 key 冲突：保留修改戳更新的一侧；平局时与整库偏好一致
        val byKey = LinkedHashMap<String, PasswordEntry>()
        val ordered = if (preferLocalOnTie) {
            byId.values.sortedBy { it.id.value }
        } else {
            byId.values.sortedByDescending { it.id.value }
        }
        ordered.forEach { entry ->
            val existing = byKey[entry.key]
            byKey[entry.key] = when {
                existing == null -> entry
                entry.mutation > existing.mutation -> entry
                entry.mutation < existing.mutation -> existing
                preferLocalOnTie -> entry
                else -> existing
            }
        }
        return byKey.values.toList()
    }

    private fun mergeSyncTargets(local: List<SyncTarget>, remote: List<SyncTarget>): List<SyncTarget> {
        val byEndpoint = LinkedHashMap<String, SyncTarget>()
        (remote + local).forEach { target ->
            val key = listOf(
                target.endpoint.trimEnd('/'),
                target.bucket,
                target.objectPrefix.trim('/'),
                target.accessKeyId,
            ).joinToString("|")
            val existing = byEndpoint[key]
            byEndpoint[key] = when {
                existing == null -> target
                target.confirmed || target.enabled -> target.copy(
                    confirmed = target.confirmed || existing.confirmed,
                    enabled = target.enabled || existing.enabled,
                    encryptedCredentialsHex = target.encryptedCredentialsHex.ifBlank {
                        existing.encryptedCredentialsHex
                    },
                    credentialsSaltHex = target.credentialsSaltHex.ifBlank {
                        existing.credentialsSaltHex
                    },
                    accessKeyId = target.accessKeyId.ifBlank { existing.accessKeyId },
                    lastSyncAt = target.lastSyncAt ?: existing.lastSyncAt,
                    lastSyncRevision = target.lastSyncRevision ?: existing.lastSyncRevision,
                )
                else -> existing
            }
        }
        return byEndpoint.values.toList()
    }

    private fun toSharedVault(vault: VaultState): VaultState =
        vault.copy(vaultId = S3ObjectPaths.SHARED_VAULT_ID)

    private fun SyncTarget.markSyncSuccess(revision: Long): SyncTarget =
        copy(
            status = SyncStatus.SUCCESS,
            lastErrorCode = null,
            lastSyncAt = nowMillis(),
            lastSyncRevision = revision,
        )
}

/**
 * 远端共享快照加载结果：区分「不存在」与「读取/解密失败」。
 */
private sealed class RemoteVaultLoad {
    data class Found(val vault: VaultState) : RemoteVaultLoad()
    data object Missing : RemoteVaultLoad()
}

/**
 * 单次 S3 同步结果。
 *
 * @param changed 业务内容是否有变化；无变化时 UI 不应提示“同步成功”。
 */
data class VaultSyncResult(
    val target: SyncTarget,
    val vault: VaultState,
    val changed: Boolean = false,
)

/**
 * 生成 AWS Signature V4 所需的 x-amz-date。
 */
fun awsAmzDate(clock: Clock = Clock.System): String {
    val dateTime = clock.now().toLocalDateTime(TimeZone.UTC)
    fun Int.pad(width: Int = 2) = toString().padStart(width, '0')
    return buildString {
        append(dateTime.year.pad(4))
        append(dateTime.monthNumber.pad())
        append(dateTime.dayOfMonth.pad())
        append('T')
        append(dateTime.hour.pad())
        append(dateTime.minute.pad())
        append(dateTime.second.pad())
        append('Z')
    }
}

private fun platformCryptoProviderOrDefault(): CryptoProvider =
    com.hdpwd.shared.crypto.platformCryptoProvider()
