package com.hdpwd.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.hdpwd.shared.crypto.platformCryptoProvider
import com.hdpwd.shared.security.AndroidBiometricProvider
import com.hdpwd.shared.security.AndroidClipboardPort
import com.hdpwd.shared.storage.AndroidAtomicByteStore
import com.hdpwd.shared.storage.LocalAppRepository
import com.hdpwd.shared.ui.PasswordManagerApp
import java.io.File

/**
 * Android 应用入口，将平台生命周期交给共享 Compose UI。
 */
class MainActivity : FragmentActivity() {
    /**
     * 创建 Android 窗口并显示共享应用界面。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val storeRoot = File(filesDir, "vaults")
        val repository = LocalAppRepository(
            bytes = AndroidAtomicByteStore(storeRoot),
            crypto = platformCryptoProvider(),
        )
        val biometric = AndroidBiometricProvider(this)
        val backupFiles = AndroidBackupFilePort(this)
        setContent {
            PasswordManagerApp(
                clipboard = AndroidClipboardPort(this@MainActivity),
                repository = repository,
                biometric = biometric,
                backupFiles = backupFiles,
            )
        }
    }
}
