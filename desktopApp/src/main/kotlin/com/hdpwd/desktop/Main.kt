package com.hdpwd.desktop

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.hdpwd.shared.crypto.platformCryptoProvider
import com.hdpwd.shared.security.DesktopBackupFilePort
import com.hdpwd.shared.security.DesktopClipboardPort
import com.hdpwd.shared.security.DesktopWindowsDpapiProvider
import com.hdpwd.shared.storage.DesktopAtomicByteStore
import com.hdpwd.shared.storage.LocalAppRepository
import com.hdpwd.shared.ui.PasswordManagerApp
import java.nio.file.Paths

/**
 * Windows/Desktop 应用入口。
 */
fun main() = application {
    val storeRoot = Paths.get(System.getProperty("user.home"), ".hd-pwd", "vaults")
    val repository = LocalAppRepository(
        bytes = DesktopAtomicByteStore(storeRoot),
        crypto = platformCryptoProvider(),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "hd-pwd",
        icon = painterResource("icon.png"),
    ) {
        PasswordManagerApp(
            clipboard = DesktopClipboardPort(),
            repository = repository,
            biometric = DesktopWindowsDpapiProvider(),
            backupFiles = DesktopBackupFilePort(),
        )
    }
}
