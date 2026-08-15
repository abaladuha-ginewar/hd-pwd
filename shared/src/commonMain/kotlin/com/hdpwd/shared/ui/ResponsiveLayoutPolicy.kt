package com.hdpwd.shared.ui

/**
 * 根据可用宽度计算密码库最小列数。
 */
object ResponsiveLayoutPolicy {
    /**
     * 窄屏至少一列，宽屏按卡片最小宽度自动增加列数。
     */
    fun columnsFor(widthDp: Int, minCardWidthDp: Int = 280): Int =
        (widthDp / minCardWidthDp).coerceAtLeast(1)
}
