package com.hdpwd.shared.crypto

/**
 * 先按一种 Argon2 变体打开密文，失败后再试另一种。
 *
 * diglol-crypto 0.2.0 的 Android 实现把 `Argon2.Type.ID` 映射成了 `Argon2d`，
 * 因此导入必须兼容 Argon2d 与真正的 Argon2id。
 *
 * Web 导入以 Argon2d 为主；某一种派生抛错时也要改试另一种，避免把
 * 「hash-wasm 绑定失败」直接显示成密钥派生失败。
 */
internal suspend fun <T> openWithArgon2Compat(
    crypto: CryptoProvider,
    password: ByteArray,
    salt: ByteArray,
    parameters: KdfParameters,
    deriveKey: (rootKey: ByteArray) -> ByteArray,
    open: suspend (key: ByteArray) -> T,
): T {
    val preferD = preferArgon2dCompatFirst()
    val order = if (preferD) booleanArrayOf(true, false) else booleanArrayOf(false, true)
    var lastFailure: Throwable? = null
    var previousRoot: ByteArray? = null
    try {
        for (useD in order) {
            val root = try {
                if (useD) {
                    crypto.argon2d(password, salt, parameters)
                } else {
                    crypto.argon2id(password, salt, parameters)
                }
            } catch (failure: Throwable) {
                lastFailure = failure
                continue
            }
            if (previousRoot != null && previousRoot.contentEquals(root)) {
                root.fill(0)
                throw lastFailure ?: failureFromDuplicateRoot()
            }
            previousRoot?.fill(0)
            previousRoot = root
            val key = try {
                deriveKey(root)
            } catch (failure: Throwable) {
                throw failure
            }
            try {
                return open(key)
            } catch (failure: Throwable) {
                lastFailure = failure
            } finally {
                key.fill(0)
            }
        }
        throw lastFailure ?: IllegalStateException("密钥派生失败")
    } finally {
        previousRoot?.fill(0)
    }
}

private fun failureFromDuplicateRoot(): Throwable =
    IllegalStateException("密钥派生失败")
