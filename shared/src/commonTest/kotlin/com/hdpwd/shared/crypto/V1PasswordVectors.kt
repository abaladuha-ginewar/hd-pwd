package com.hdpwd.shared.crypto

/**
 * V1 密码生成固定测试向量。
 *
 * 生成器仅依赖 [PortableHmacSha256] / [PortableSha256]，三端共用同一实现；
 * 任一平台跑通这些向量即证明跨平台逐字节一致，且升级不得改动本文件期望值。
 */
object V1PasswordVectors {
    const val RECOVERY = "correct horse battery staple"
    const val KEY = "GitHub.Work"
    const val EXPECTED_PASSWORD = "8ff-m+yMY^Ib_Cnt@7X_"
}
