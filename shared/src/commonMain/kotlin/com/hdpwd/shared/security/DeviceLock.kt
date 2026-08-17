package com.hdpwd.shared.security

import kotlinx.serialization.Serializable

/**
 * 本机设备锁记录，与用户索引分离，不得写入备份或 S3。
 */
@Serializable
data class DeviceLockRecord(
    val formatVersion: Int = 1,
    val generation: String,
    val wrappedDeviceLek: ByteArray,
    val preferBiometric: Boolean = false,
)

/**
 * 首次设置或轮换设备锁后同时交出记录与内存中的 DeviceLEK。
 */
data class DeviceLockCreation(
    val record: DeviceLockRecord,
    val deviceKey: LocalEnvelopeKey,
)

/**
 * 每用户缓存在本机的恢复密码封装。
 *
 * 新格式带 [deviceGeneration]；旧每用户主密码格式仍可能带 [wrappedLocalEnvelopeKey]，用于启动迁移。
 */
@Serializable
data class UserRecoveryEnvelope(
    val formatVersion: Int = 1,
    val encryptedRecoveryPassword: ByteArray,
    val deviceGeneration: String? = null,
    val wrappedLocalEnvelopeKey: ByteArray? = null,
) {
    /**
     * 旧版每用户 LEK 封装，尚不能用当前 DeviceLEK 解开。
     */
    fun isLegacy(): Boolean = wrappedLocalEnvelopeKey != null && deviceGeneration == null

    /**
     * 与当前设备锁世代不一致，或仍是旧格式时需要重绑。
     */
    fun needsRebind(currentGeneration: String): Boolean {
        val generation = deviceGeneration
        return generation == null || generation != currentGeneration
    }
}

/**
 * 设备级生物识别封装使用的稳定 label，不得再按用户 id 分钥匙。
 */
object DeviceBiometric {
    const val LABEL = "hdpwd.device-lock"
}

/**
 * 根据本机偏好与当前能力决定验证 UI 是否自动拉起生物识别。
 */
object DeviceUnlockPreference {
    /**
     * 仅当用户选择过生物识别、硬件可用且本机仍有有效封装时自动发起。
     */
    fun shouldAutoPromptBiometric(
        preferBiometric: Boolean,
        availability: BiometricAvailability,
        hasSealedBlob: Boolean,
    ): Boolean =
        preferBiometric &&
            hasSealedBlob &&
            availability == BiometricAvailability.AVAILABLE
}
