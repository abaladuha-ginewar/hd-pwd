package com.hdpwd.shared

import com.hdpwd.shared.security.AuthorizationSession
import com.hdpwd.shared.security.LocalEnvelopeKey
import com.hdpwd.shared.security.OperationPurpose
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 验证绝对五分钟会话和操作许可边界。
 */
class AuthorizationSessionTest {
    /**
     * 到期后禁止新操作，但已有许可仍可关闭。
     */
    @Test
    fun expirationBlocksNewOperations() {
        var now = 0L
        val session = AuthorizationSession({ now }, lifetimeMillis = 100)
        val expected = ByteArray(32) { 1 }
        session.open(LocalEnvelopeKey(expected))
        val permit = session.acquire(OperationPurpose.GENERATE_PASSWORD) ?: error("应获取操作许可")
        now = 101
        assertFalse(session.canStart())
        assertNull(session.acquire(OperationPurpose.EXPORT_BACKUP))
        session.withEnvelopeKey(permit) { key ->
            key.use { bytes -> assertTrue(bytes.contentEquals(expected)) }
        }
        permit.close()
        assertFalse(session.canStart())
    }

    /**
     * 有效会话允许限定用途操作。
     */
    @Test
    fun activeSessionAllowsPermit() {
        val session = AuthorizationSession({ 0L })
        session.open(LocalEnvelopeKey(ByteArray(32)))
        assertTrue(session.acquire(OperationPurpose.SYNC) != null)
        assertTrue(session.acquire(OperationPurpose.CREATE_USER) != null)
        assertTrue(session.acquire(OperationPurpose.DEVICE_SETTINGS) != null)
        session.clear()
        assertFalse(session.canStart())
    }
}
