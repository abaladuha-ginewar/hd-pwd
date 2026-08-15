package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.sync.S3ObjectPaths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证 S3 对象路径只使用随机身份和序号。
 */
class S3ProtocolTest {
    /**
     * 对象路径不得出现用户业务字段。
     */
    @Test
    fun objectPathContainsNoBusinessFields() {
        val path = S3ObjectPaths.delta(EntityId("vault-id"), EntityId("device-id"), 4)
        assertTrue(path.endsWith("/4.dat"))
        assertFalse(path.contains("GitHub"))
        assertFalse(path.contains("title"))
    }
}
