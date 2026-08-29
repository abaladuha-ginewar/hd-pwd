package com.hdpwd.shared.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
actual fun HdOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    label: String?,
    placeholder: String?,
    supportingText: String?,
    singleLine: Boolean,
    minLines: Int,
    isError: Boolean,
    isPassword: Boolean,
    passwordVisible: Boolean,
    trailingIcon: (@Composable () -> Unit)?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        isError = isError,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = trailingIcon,
    )
}
