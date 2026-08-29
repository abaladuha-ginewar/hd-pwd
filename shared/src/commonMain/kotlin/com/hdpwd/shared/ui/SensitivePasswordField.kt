package com.hdpwd.shared.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 默认掩码的敏感密码输入框；明文仅显示 15 秒。
 */
@Composable
fun SensitivePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(visible, value) {
        if (visible) {
            delay(15_000)
            visible = false
        }
    }
    HdOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        enabled = enabled,
        isPassword = true,
        passwordVisible = visible,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "隐藏明文" else "显示明文",
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        modifier = modifier,
    )
}
