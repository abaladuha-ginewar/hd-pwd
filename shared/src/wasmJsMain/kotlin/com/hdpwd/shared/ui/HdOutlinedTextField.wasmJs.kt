package com.hdpwd.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    val inputId = remember { "hdpwd-in-${Random.nextInt(1_000_000_000)}" }
    val density = LocalDensity.current
    var left by remember { mutableStateOf(0.0) }
    var top by remember { mutableStateOf(0.0) }
    var width by remember { mutableStateOf(0.0) }
    var heightPx by remember { mutableStateOf(0.0) }
    val fieldHeight = if (singleLine) 56.dp else (24 * minLines.coerceAtLeast(3) + 32).dp
    val iconReserve = if (trailingIcon != null) with(density) { 48.dp.toPx() } else 0f
    val hint = label ?: placeholder ?: ""

    DisposableEffect(inputId) {
        onDispose { htmlInputRemove(inputId) }
    }
    LaunchedEffect(inputId, left, top, width, heightPx, value, hint, enabled, isPassword, passwordVisible, singleLine, isError) {
        htmlInputUpsert(
            id = inputId,
            left = left,
            top = top,
            width = (width - iconReserve).toDouble().coerceAtLeast(0.0),
            height = heightPx,
            value = value,
            placeholder = hint,
            enabled = enabled,
            password = isPassword && !passwordVisible,
            multiline = !singleLine,
            isError = isError,
            visible = width > 2 && heightPx > 2 &&
                top + heightPx > 0 && top < htmlWindowInnerHeight(),
        )
        if (!htmlInputFocused(inputId) && htmlInputValue(inputId) != value) {
            htmlInputSetValue(inputId, value)
        }
    }
    LaunchedEffect(inputId) {
        while (isActive) {
            val latest = htmlInputValue(inputId)
            if (latest != null && latest != value) {
                onValueChange(latest)
            }
            delay(32)
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(fieldHeight)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        left = bounds.left.toDouble()
                        top = bounds.top.toDouble()
                        width = bounds.width.toDouble()
                        heightPx = bounds.height.toDouble()
                    },
            )
            trailingIcon?.invoke()
        }
        if (!supportingText.isNullOrBlank()) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}
