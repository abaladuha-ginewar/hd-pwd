package com.hdpwd.shared.crypto

/**
 * 返回当前平台的生产密码学提供者。
 */
expect fun platformCryptoProvider(): CryptoProvider
