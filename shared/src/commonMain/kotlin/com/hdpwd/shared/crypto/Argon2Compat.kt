package com.hdpwd.shared.crypto

/**
 * 先按 RFC 9106 Argon2id 打开密文；失败后再用 Argon2d 重试。
 *
 * diglol-crypto 0.2.0 的 Android 实现把 `Argon2.Type.ID` 映射成了 `Argon2d`，
 * 因此旧版安卓导出的备份在 Windows / 正确 Argon2id 上会 AEAD 失败并被提示「文件损坏」。
 */
internal suspend fun <T> openWithArgon2Compat(
    crypto: CryptoProvider,
    password: ByteArray,
    salt: ByteArray,
    parameters: KdfParameters,
    deriveKey: (rootKey: ByteArray) -> ByteArray,
    open: suspend (key: ByteArray) -> T,
): T {
    val idRoot = crypto.argon2id(password, salt, parameters)
    val idKey = try {
        deriveKey(idRoot)
    } catch (failure: Throwable) {
        idRoot.fill(0)
        throw failure
    }
    try {
        return open(idKey)
    } catch (idFailure: Throwable) {
        val dRoot = try {
            crypto.argon2d(password, salt, parameters)
        } catch (_: Throwable) {
            throw idFailure
        }
        try {
            // 测试替身或未实现 Argon2d 的平台会得到相同根密钥，不必再开一次容器。
            if (idRoot.contentEquals(dRoot)) throw idFailure
            val dKey = deriveKey(dRoot)
            try {
                return open(dKey)
            } finally {
                dKey.fill(0)
            }
        } finally {
            dRoot.fill(0)
        }
    } finally {
        idRoot.fill(0)
        idKey.fill(0)
    }
}
