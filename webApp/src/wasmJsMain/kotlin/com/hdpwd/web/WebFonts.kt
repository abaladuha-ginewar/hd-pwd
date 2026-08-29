package com.hdpwd.web

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.hdpwd.web.resources.Res
import com.hdpwd.web.resources.noto_sans_sc_regular
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

/**
 * Skiko/Wasm 不会回退到系统中文字体，未打包覆盖汉字的字体时会显示方框。
 * 通过 Compose Resources 加载 Noto Sans SC，并套到 Material3 全部文本样式。
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun webFontFamily(): FontFamily = FontFamily(
    Font(resource = Res.font.noto_sans_sc_regular),
)

@Composable
fun WithWebFont(content: @Composable () -> Unit) {
    val family = webFontFamily()
    val base = MaterialTheme.typography
    MaterialTheme(
        typography = Typography(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family),
            headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family),
            titleMedium = base.titleMedium.copy(fontFamily = family),
            titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family),
            bodyMedium = base.bodyMedium.copy(fontFamily = family),
            bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family),
            labelMedium = base.labelMedium.copy(fontFamily = family),
            labelSmall = base.labelSmall.copy(fontFamily = family),
        ),
        content = content,
    )
}
