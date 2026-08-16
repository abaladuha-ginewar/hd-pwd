package com.hdpwd.shared

import com.hdpwd.shared.crypto.PasswordGenerator
import com.hdpwd.shared.domain.KeyRules
import com.hdpwd.shared.domain.PasswordPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 验证 V1 生成协议和 key 约束的跨平台行为。
 */
class PasswordGeneratorTest {
    /**
     * 相同输入必须生成相同结果。
     */
    @Test
    fun sameInputProducesSamePassword() {
        val policy = PasswordPolicy()
        val first = PasswordGenerator.generate("correct horse battery staple", "GitHub.Work", policy)
        val second = PasswordGenerator.generate("correct horse battery staple", "GitHub.Work", policy)
        assertEquals(first, second)
        assertEquals(policy.length, first.length)
    }

    /**
     * key 大小写变化必须参与生成。
     */
    @Test
    fun keyIsCaseSensitive() {
        val policy = PasswordPolicy()
        val upper = PasswordGenerator.generate("recovery", "Example", policy)
        val lower = PasswordGenerator.generate("recovery", "example", policy)
        assertNotEquals(upper, lower)
    }

    /**
     * 默认规则必须满足所有必选字符类别。
     */
    @Test
    fun defaultPolicyContainsRequiredClasses() {
        val password = PasswordGenerator.generate("recovery", "example", PasswordPolicy())
        assertTrue(password.any(Char::isUpperCase))
        assertTrue(password.any(Char::isLowerCase))
        assertTrue(password.any(Char::isDigit))
        assertTrue(password.any { it in "!@#$%^&*_-+=.?" })
    }

    /**
     * 恢复配方编码后仍能恢复完整规则。
     */
    @Test
    fun recipeRoundTrip() {
        val policy = PasswordPolicy(symbols = "!@#", minimumSymbols = 2)
        val encoded = PasswordGenerator.recipe("Example", policy).encode()
        val parsed = PasswordGenerator.parseRecipe(encoded)
        assertEquals("Example", parsed.key)
        assertEquals(policy.canonical(), parsed.policy.canonical())
    }

    /**
     * 自定义规则往返时必须保留全部字段。
     */
    @Test
    fun recipeRoundTripPreservesCustomPolicy() {
        val custom = PasswordPolicy(length = 32, symbols = "!@", minimumSymbols = 2)
        val encoded = PasswordGenerator.recipe("mail-prod", custom).encode()
        val decoded = PasswordGenerator.parseRecipe(encoded)
        assertEquals(custom, decoded.policy)
    }

    /**
     * key 规则必须严格区分大小写并拒绝其他字符。
     */
    @Test
    fun keyValidation() {
        assertTrue(KeyRules.isValid("A_.-z"))
        assertTrue(KeyRules.isValid("Site1"))
        assertTrue(KeyRules.isValid("user_2024"))
        assertTrue(KeyRules.validate("with space") != null)
        assertTrue(KeyRules.validate("中文") != null)
    }

    /**
     * 非法 key 不能进入生成协议。
     */
    @Test
    fun invalidKeyIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            PasswordGenerator.generate("recovery", "bad/key", PasswordPolicy())
        }
    }

    /**
     * V1 固定向量锁定协议输入、HMAC 流和最终输出。
     */
    @Test
    fun v1KnownAnswerVector() {
        assertEquals(
            "8ff-m+yMY^Ib_Cnt@7X_",
            PasswordGenerator.generate(
                "correct horse battery staple",
                "GitHub.Work",
                PasswordPolicy(),
            ),
        )
    }
}
