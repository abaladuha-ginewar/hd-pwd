package com.hdpwd.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.hdpwd.shared.security.DesktopClipboardPort
import com.hdpwd.shared.ui.PasswordManagerApp

/**
 * Windows/Desktop 应用入口。
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "HD Password",
    ) {
        PasswordManagerApp(DesktopClipboardPort())
    }
}
