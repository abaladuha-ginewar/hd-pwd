package com.hdpwd.shared.security

/**
 * 平台生物识别能力状态。
 */
enum class BiometricAvailability {
    AVAILABLE,
    UNAVAILABLE,
    NOT_ENROLLED,
    PERMISSION_DENIED,
}

/**
 * 生物识别硬件封装 DeviceLEK 的平台接口。
 */
interface BiometricProvider {
    /**
     * 查询当前设备是否能稳定提供生物识别密钥封装。
     */
    fun availability(): BiometricAvailability

    /**
     * 在用户通过验证后封装 DeviceLEK。
     */
    suspend fun seal(label: String, envelopeKey: ByteArray): ByteArray

    /**
     * 弹出生物识别验证并解封装 DeviceLEK。
     */
    suspend fun open(label: String, sealedKey: ByteArray): ByteArray

    /**
     * 删除本机生物识别封装。
     */
    suspend fun delete(label: String)
}

/**
 * 无生物识别能力平台的安全回退实现。
 */
object UnavailableBiometricProvider : BiometricProvider {
    /**
     * 表示当前平台不可用。
     */
    override fun availability(): BiometricAvailability = BiometricAvailability.UNAVAILABLE

    /**
     * 拒绝封装请求。
     */
    override suspend fun seal(label: String, envelopeKey: ByteArray): ByteArray =
        error("当前平台不支持生物识别封装")

    /**
     * 拒绝解封装请求。
     */
    override suspend fun open(label: String, sealedKey: ByteArray): ByteArray =
        error("当前平台不支持生物识别解封装")

    /**
     * 无操作删除。
     */
    override suspend fun delete(label: String) = Unit
}
