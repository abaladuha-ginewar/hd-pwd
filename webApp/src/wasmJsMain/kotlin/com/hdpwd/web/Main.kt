package com.hdpwd.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.hdpwd.shared.ui.PasswordManagerApp

/**
 * WebAssembly 应用入口。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("ComposeTarget") {
        PasswordManagerApp()
    }
}
