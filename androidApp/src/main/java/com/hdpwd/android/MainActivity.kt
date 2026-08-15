package com.hdpwd.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hdpwd.shared.security.AndroidClipboardPort
import com.hdpwd.shared.ui.PasswordManagerApp

/**
 * Android 应用入口，将平台生命周期交给共享 Compose UI。
 */
class MainActivity : ComponentActivity() {
    /**
     * 创建 Android 窗口并显示共享应用界面。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PasswordManagerApp(AndroidClipboardPort(this@MainActivity))
        }
    }
}
