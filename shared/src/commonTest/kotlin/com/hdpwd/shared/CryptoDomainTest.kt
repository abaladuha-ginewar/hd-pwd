package com.hdpwd.shared

import com.hdpwd.shared.crypto.CryptoDomains
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证用途域标识不会意外复用。
 */
class CryptoDomainTest {
    /**
     * 数据、同步、备份、生成和本机封装必须使用不同域。
     */
    @Test
    fun allCryptoDomainsAreDistinct() {
        val domains = setOf(
            CryptoDomains.DATA,
            CryptoDomains.SYNC,
            CryptoDomains.BACKUP,
            CryptoDomains.GENERATOR,
            CryptoDomains.LOCAL_ENVELOPE,
        )
        assertEquals(5, domains.size)
    }
}
