package com.hdpwd.shared

import com.hdpwd.shared.crypto.PasswordGenerator
import com.hdpwd.shared.crypto.V1PasswordVectors
import com.hdpwd.shared.domain.PasswordPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop 目标再次锁定 V1 向量，证明 JVM 运行时与 commonTest 期望一致。
 */
class DesktopPasswordGeneratorParityTest {
    @Test
    fun desktopMatchesFrozenV1Vector() {
        assertEquals(
            V1PasswordVectors.EXPECTED_PASSWORD,
            PasswordGenerator.generate(
                V1PasswordVectors.RECOVERY,
                V1PasswordVectors.KEY,
                PasswordPolicy(),
            ),
        )
    }
}
