package com.hdpwd.shared.security

import com.hdpwd.shared.crypto.CryptoDomains
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.EncryptedContainerCodec
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.crypto.openWithArgon2Compat

/**
 * 设备级 DeviceLEK 包装，以及按用户封装本机缓存的恢复密码。
 *
 * DeviceLEK 由主密码（及可选生物识别）保护；恢复密码密文用 DeviceLEK 加密，
 * 附加认证数据绑定用户 id 与设备锁世代，防止串用或用新钥匙去解旧密文。
 */
class LocalEnvelopeService(
    private val crypto: CryptoProvider,
    private val kdfParameters: KdfParameters,
) {
    private val container = EncryptedContainerCodec(crypto)
    private val deviceLockAad = CryptoDomains.DEVICE_LOCK.encodeToByteArray()
    private val legacyEnvelopeAad = CryptoDomains.LOCAL_ENVELOPE.encodeToByteArray()

    /**
     * 生成新的 DeviceLEK 与世代，并用主密码包装。
     */
    suspend fun createDeviceLock(masterPassword: CharSequence): DeviceLockCreation {
        val deviceLek = crypto.randomBytes(32)
        val generation = crypto.randomBytes(16).toHex()
        val salt = crypto.randomBytes(16)
        val wrappingKey = deriveLocalKey(masterPassword, salt)
        try {
            val wrapped = container.seal(
                key = wrappingKey,
                plaintext = deviceLek,
                kdfParameters = kdfParameters,
                associatedData = deviceLockAad,
                salt = salt,
            )
            return DeviceLockCreation(
                record = DeviceLockRecord(
                    generation = generation,
                    wrappedDeviceLek = wrapped,
                    preferBiometric = false,
                ),
                deviceKey = LocalEnvelopeKey(deviceLek.copyOf()),
            )
        } finally {
            deviceLek.fill(0)
            wrappingKey.fill(0)
        }
    }

    /**
     * 使用本机主密码解封装 DeviceLEK；错误密码由 AEAD 拒绝。
     */
    suspend fun unlockWithMasterPassword(
        record: DeviceLockRecord,
        masterPassword: CharSequence,
    ): LocalEnvelopeKey {
        val header = container.readHeader(record.wrappedDeviceLek)
        val salt = header.saltHex.hexToByteArray()
        val password = masterPassword.toString().encodeToByteArray()
        return try {
            openWithArgon2Compat(
                crypto = crypto,
                password = password,
                salt = salt,
                parameters = header.kdfParameters,
                deriveKey = { rootKey -> rootKey.copyOf() },
                open = { wrappingKey ->
                    try {
                        LocalEnvelopeKey(container.open(wrappingKey, record.wrappedDeviceLek))
                    } catch (failure: Throwable) {
                        throw IllegalArgumentException("本机主密码错误", failure)
                    }
                },
            )
        } finally {
            password.fill(0)
            salt.fill(0)
        }
    }

    /**
     * 用新主密码重新包装同一把 DeviceLEK，不更换世代。
     */
    suspend fun rewrapMasterPassword(
        record: DeviceLockRecord,
        deviceKey: LocalEnvelopeKey,
        newPassword: CharSequence,
    ): DeviceLockRecord {
        val salt = crypto.randomBytes(16)
        val wrappingKey = deriveLocalKey(newPassword, salt)
        val plaintext = deviceKey.use { it.copyOf() }
        return try {
            val wrapped = container.seal(
                key = wrappingKey,
                plaintext = plaintext,
                kdfParameters = kdfParameters,
                associatedData = deviceLockAad,
                salt = salt,
            )
            record.copy(wrappedDeviceLek = wrapped)
        } finally {
            wrappingKey.fill(0)
            plaintext.fill(0)
        }
    }

    /**
     * 忘记主密码时轮换 DeviceLEK 与世代；调用方只应重绑当前验证成功的用户。
     */
    suspend fun rotateDeviceLock(
        newPassword: CharSequence,
        preferBiometric: Boolean = false,
    ): DeviceLockCreation {
        val created = createDeviceLock(newPassword)
        return created.copy(record = created.record.copy(preferBiometric = preferBiometric))
    }

    /**
     * 用当前 DeviceLEK 封装某用户的恢复密码，AAD 绑定用户 id 与世代。
     */
    suspend fun sealRecoveryPassword(
        deviceKey: LocalEnvelopeKey,
        generation: String,
        userId: String,
        recoveryPassword: CharSequence,
    ): UserRecoveryEnvelope {
        val keyBytes = deviceKey.use { it.copyOf() }
        return try {
            val encrypted = container.seal(
                key = keyBytes,
                plaintext = recoveryPassword.toString().encodeToByteArray(),
                kdfParameters = kdfParameters,
                associatedData = userRecoveryAad(userId, generation),
            )
            UserRecoveryEnvelope(
                formatVersion = 2,
                encryptedRecoveryPassword = encrypted,
                deviceGeneration = generation,
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    /**
     * 世代不匹配时拒绝解密，避免用新 DeviceLEK 去碰旧密文。
     */
    fun requireBound(envelope: UserRecoveryEnvelope, currentGeneration: String) {
        require(!envelope.needsRebind(currentGeneration)) {
            "该用户需要用恢复密码重新绑定本机解锁"
        }
    }

    /**
     * 在恢复密码的最短必要生命周期内执行回调并清理明文字节。
     */
    suspend fun <T> withRecoveryPassword(
        envelope: UserRecoveryEnvelope,
        deviceKey: LocalEnvelopeKey,
        userId: String,
        currentGeneration: String,
        block: suspend (CharSequence) -> T,
    ): T {
        requireBound(envelope, currentGeneration)
        val header = container.readHeader(envelope.encryptedRecoveryPassword)
        val expectedAad = userRecoveryAad(userId, currentGeneration)
        require(header.associatedDataHex == expectedAad.toHex()) {
            "恢复密码封装与用户或设备锁世代不匹配"
        }
        val key = deviceKey.use { it.copyOf() }
        val recoveryBytes = try {
            container.open(key, envelope.encryptedRecoveryPassword)
        } finally {
            key.fill(0)
        }
        return try {
            block(recoveryBytes.decodeToString())
        } finally {
            recoveryBytes.fill(0)
        }
    }

    /**
     * 解开旧版每用户主密码包装的 LEK，仅用于迁移。
     */
    suspend fun unlockLegacyLocalKey(
        envelope: UserRecoveryEnvelope,
        localPassword: CharSequence,
    ): LocalEnvelopeKey {
        val wrapped = envelope.wrappedLocalEnvelopeKey
            ?: error("不是旧版本地封装")
        val header = container.readHeader(wrapped)
        val salt = header.saltHex.hexToByteArray()
        val password = localPassword.toString().encodeToByteArray()
        return try {
            openWithArgon2Compat(
                crypto = crypto,
                password = password,
                salt = salt,
                parameters = header.kdfParameters,
                deriveKey = { rootKey -> rootKey.copyOf() },
                open = { wrappingKey ->
                    try {
                        LocalEnvelopeKey(container.open(wrappingKey, wrapped))
                    } catch (failure: Throwable) {
                        throw IllegalArgumentException("本机主密码错误", failure)
                    }
                },
            )
        } finally {
            password.fill(0)
            salt.fill(0)
        }
    }

    /**
     * 使用旧版每用户 LEK 临时读取恢复密码，仅用于迁移重绑。
     */
    suspend fun <T> withLegacyRecoveryPassword(
        envelope: UserRecoveryEnvelope,
        legacyKey: LocalEnvelopeKey,
        block: suspend (CharSequence) -> T,
    ): T {
        val key = legacyKey.use { it.copyOf() }
        val recoveryBytes = try {
            container.open(key, envelope.encryptedRecoveryPassword)
        } finally {
            key.fill(0)
        }
        return try {
            block(recoveryBytes.decodeToString())
        } finally {
            recoveryBytes.fill(0)
        }
    }

    /**
     * 构造旧格式封装，供迁移测试与兼容读取。
     */
    suspend fun createLegacyEnvelope(
        recoveryPassword: CharSequence,
        localPassword: CharSequence,
    ): UserRecoveryEnvelope {
        val envelopeKey = crypto.randomBytes(32)
        val localSalt = crypto.randomBytes(16)
        val localKey = deriveLocalKey(localPassword, localSalt)
        return try {
            val wrappedKey = container.seal(
                key = localKey,
                plaintext = envelopeKey,
                kdfParameters = kdfParameters,
                associatedData = legacyEnvelopeAad,
                salt = localSalt,
            )
            val encryptedRecovery = container.seal(
                key = envelopeKey,
                plaintext = recoveryPassword.toString().encodeToByteArray(),
                kdfParameters = kdfParameters,
                associatedData = legacyEnvelopeAad,
            )
            UserRecoveryEnvelope(
                formatVersion = 1,
                encryptedRecoveryPassword = encryptedRecovery,
                wrappedLocalEnvelopeKey = wrappedKey,
            )
        } finally {
            envelopeKey.fill(0)
            localKey.fill(0)
        }
    }

    private fun userRecoveryAad(userId: String, generation: String): ByteArray =
        "${CryptoDomains.USER_RECOVERY}|$userId|$generation".encodeToByteArray()

    private suspend fun deriveLocalKey(password: CharSequence, salt: ByteArray): ByteArray =
        crypto.argon2id(password.toString().encodeToByteArray(), salt, kdfParameters)
}

/**
 * 将随机字节编码为路径安全的小写十六进制。
 */
private fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/**
 * 解码本机封装头中的十六进制 salt。
 */
private fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "本机封装 salt 无效" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
