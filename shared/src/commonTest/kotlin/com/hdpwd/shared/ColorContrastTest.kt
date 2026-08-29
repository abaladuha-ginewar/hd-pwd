package com.hdpwd.shared

import com.hdpwd.shared.domain.ColorContrast
import com.hdpwd.shared.domain.ColorRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 满色卡片的相对亮度前景与非法 HEX 回退。
 */
class ColorContrastTest {
    /**
     * 白底和色板浅色应使用深色字。
     */
    @Test
    fun lightBackgroundsUseDarkForeground() {
        assertTrue(ColorContrast.prefersDarkForeground(255, 255, 255))
        val amber = ColorRules.parseRgb("#FBBF24")
        assertNotNull(amber)
        assertTrue(ColorContrast.prefersDarkForeground(amber.first, amber.second, amber.third))
        val slate = ColorRules.parseRgb("#94A3B8")
        assertNotNull(slate)
        assertTrue(ColorContrast.prefersDarkForeground(slate.first, slate.second, slate.third))
    }

    /**
     * 黑底和深靛蓝应使用浅色字。
     */
    @Test
    fun darkBackgroundsUseLightForeground() {
        assertFalse(ColorContrast.prefersDarkForeground(0, 0, 0))
        val navy = ColorRules.parseRgb("#1E3A8A")
        assertNotNull(navy)
        assertFalse(ColorContrast.prefersDarkForeground(navy.first, navy.second, navy.third))
    }

    /**
     * 相对亮度在 0–1 之间，白色为 1。
     */
    @Test
    fun luminanceBounds() {
        assertEquals(0.0, ColorContrast.relativeLuminance(0, 0, 0))
        assertEquals(1.0, ColorContrast.relativeLuminance(255, 255, 255), 1e-6)
        assertTrue(ColorContrast.relativeLuminance(255, 255, 255) > ColorContrast.DARK_FOREGROUND_THRESHOLD)
    }

    /**
     * 非法 HEX 不得解析为 RGB。
     */
    @Test
    fun invalidHexFallsBack() {
        assertNull(ColorRules.parseRgb("red"))
        assertNull(ColorRules.parseRgb("#GGG"))
        assertNull(ColorRules.parseRgb("#94A3B8FF"))
        assertNotNull(ColorRules.parseRgb("#94A3B8"))
    }
}
