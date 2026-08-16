package com.hdpwd.shared.sync

import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.platform.platformHttpClient
import com.hdpwd.shared.storage.AuthenticatedVaultCipher
import com.hdpwd.shared.storage.DefaultKdfParameters
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 将本地 Vault 快照推送到已确认的 S3 目标。
 */
class VaultS3SyncService(
    private val crypto: CryptoProvider = platformCryptoProviderOrDefault(),
    private val httpClient: HttpClient = platformHttpClient(),
    private val now: () -> String = ::awsAmzDate,
) {
    private val credentialVault = S3CredentialVault(crypto, DefaultKdfParameters)
    private val vaultCipher = AuthenticatedVaultCipher(
        crypto = crypto,
        kdfParameters = DefaultKdfParameters,
        keyDomain = CryptoDomains.BACKUP,
    )

    /**
     * 同步单个目标：上传加密 Vault 快照并更新状态。
     */
    suspend fun syncTarget(
        target: SyncTarget,
        vault: VaultState,
        recoveryPassword: CharSequence,
    ): SyncTarget {
        if (!target.enabled || !target.confirmed) {
            return target.copy(status = SyncStatus.IDLE, lastErrorCode = null)
        }
        val credentials = try {
            credentialVault.openWithRecoveryPassword(
                recoveryPassword = recoveryPassword,
                encryptedCredentialsHex = target.encryptedCredentialsHex,
                credentialsSaltHex = target.credentialsSaltHex,
            )
        } catch (failure: Throwable) {
            return target.copy(
                status = SyncStatus.FAILED,
                lastErrorCode = (failure.message ?: "凭据无效").take(64),
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
            )
            val payload = vaultCipher.encrypt(recoveryPassword, vault)
            val path = S3ObjectPaths.snapshot(
                vaultId = vault.vaultId,
                snapshotId = EntityId("latest"),
                objectPrefix = target.objectPrefix,
            )
            store.put(path, payload)
            target.copy(status = SyncStatus.SUCCESS, lastErrorCode = null)
        } catch (failure: Throwable) {
            target.copy(
                status = SyncStatus.FAILED,
                lastErrorCode = (failure.message ?: failure::class.simpleName ?: "sync-error").take(64),
            )
        } finally {
            credentials.clear()
        }
    }
}

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
