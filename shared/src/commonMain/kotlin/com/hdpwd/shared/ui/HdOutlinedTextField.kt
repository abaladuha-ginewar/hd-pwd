package com.hdpwd.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 跨平台文本框。Web 使用真实 DOM input，以便切换中文输入法。
 */
@Composable
expect fun HdOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
)
