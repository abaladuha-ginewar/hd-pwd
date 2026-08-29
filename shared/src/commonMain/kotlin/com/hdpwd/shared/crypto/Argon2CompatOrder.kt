package com.hdpwd.shared.crypto

/**
 * 导入时是否先试 Argon2d。Web 为 true；PC/安卓本机解密仍先试 Argon2id。
 */
internal expect fun preferArgon2dCompatFirst(): Boolean
