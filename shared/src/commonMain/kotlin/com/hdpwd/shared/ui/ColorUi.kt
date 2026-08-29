package com.hdpwd.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hdpwd.shared.domain.ColorContrast
import com.hdpwd.shared.domain.ColorRules
import kotlin.math.roundToInt

/**
 * 将不透明 RGB HEX 解析为 Compose Color；非法值回退为中性灰。
 */
fun parseHexColor(hex: String): Color {
    val rgb = ColorRules.parseRgb(hex) ?: return Color(0xFF94A3B8)
    return Color(rgb.first, rgb.second, rgb.third)
}

/**
 * 根据背景相对亮度选择黑或白前景，供满色卡片使用。
 */
fun contrastContentColor(hex: String): Color {
    val rgb = ColorRules.parseRgb(hex) ?: ColorRules.parseRgb("#94A3B8")!!
    return if (ColorContrast.prefersDarkForeground(rgb.first, rgb.second, rgb.third)) {
        Color.Black
    } else {
        Color.White
    }
}

/**
 * 将 RGB 通道格式化为标准 HEX。
 */
fun rgbToHex(red: Int, green: Int, blue: Int): String =
    "#" + listOf(red, green, blue).joinToString("") {
        it.coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
    }

/**
 * 固定色板与自定义 RGB 调色器。
 */
@Composable
fun ColorPickerSection(
    selectedHex: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customExpanded by remember { mutableStateOf(!ColorRules.fixedColors.contains(selectedHex)) }
    val selected = parseHexColor(selectedHex)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("背景颜色", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(selected)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            )
            Text(selectedHex, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorRules.fixedColors.forEach { hex ->
                val color = parseHexColor(hex)
                val isSelected = selectedHex.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            },
                            shape = CircleShape,
                        )
                        .clickable {
                            customExpanded = false
                            onSelected(hex)
                        },
                )
            }
        }
        TextButton(onClick = { customExpanded = !customExpanded }) {
            Text(if (customExpanded) "收起自定义调色" else "自定义调色")
        }
        if (customExpanded) {
            CustomRgbPicker(selectedHex = selectedHex, onSelected = onSelected)
        }
    }
}

@Composable
private fun CustomRgbPicker(
    selectedHex: String,
    onSelected: (String) -> Unit,
) {
    val initial = parseHexColor(selectedHex)
    var red by remember(selectedHex) { mutableStateOf((initial.red * 255).roundToInt()) }
    var green by remember(selectedHex) { mutableStateOf((initial.green * 255).roundToInt()) }
    var blue by remember(selectedHex) { mutableStateOf((initial.blue * 255).roundToInt()) }
    var hexInput by remember(selectedHex) { mutableStateOf(selectedHex) }

    fun publish(r: Int, g: Int, b: Int) {
        val hex = rgbToHex(r, g, b)
        hexInput = hex
        onSelected(hex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChannelSlider("R", red) {
            red = it
            publish(red, green, blue)
        }
        ChannelSlider("G", green) {
            green = it
            publish(red, green, blue)
        }
        ChannelSlider("B", blue) {
            blue = it
            publish(red, green, blue)
        }
        HdOutlinedTextField(
            value = hexInput,
            onValueChange = { value ->
                hexInput = value.uppercase()
                if (ColorRules.isValidHex(hexInput)) {
                    val color = parseHexColor(hexInput)
                    red = (color.red * 255).roundToInt()
                    green = (color.green * 255).roundToInt()
                    blue = (color.blue * 255).roundToInt()
                    onSelected(hexInput)
                }
            },
            label = "自定义 HEX",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = !ColorRules.isValidHex(hexInput),
        )
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(value.toString().padStart(3, ' '), modifier = Modifier.width(36.dp))
    }
}
