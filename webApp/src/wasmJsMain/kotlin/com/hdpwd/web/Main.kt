package com.hdpwd.web

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.hdpwd.shared.application.LifecycleSyncCoordinator
import com.hdpwd.shared.application.WebLifecycleSyncAdapter
import com.hdpwd.shared.crypto.platformCryptoProvider
import com.hdpwd.shared.security.UnavailableBiometricProvider
import com.hdpwd.shared.storage.AtomicDirtyStateStore
import com.hdpwd.shared.storage.LocalAppRepository
import com.hdpwd.shared.storage.WasmLocalStorageByteStore
import com.hdpwd.shared.ui.PasswordManagerApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * WebAssembly 应用入口：localStorage 持久化 + Page Visibility / beforeunload。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val bytes = WasmLocalStorageByteStore()
    val repository = LocalAppRepository(
        bytes = bytes,
        crypto = platformCryptoProvider(),
    )
    ComposeViewport("ComposeTarget") {
        var pendingChanges by remember { mutableStateOf(false) }
        val dirtyStore = remember { AtomicDirtyStateStore(bytes) }
        val coordinator = remember {
            LifecycleSyncCoordinator(
                dirtyStore = dirtyStore,
                userId = "web-session",
                hasPendingChanges = { pendingChanges },
                continueSync = { },
            )
        }
        val lifecycle = remember {
            WebLifecycleSyncAdapter(
                coordinator = coordinator,
                hasPendingChanges = { pendingChanges },
            )
        }
        LaunchedEffect(lifecycle) {
            while (isActive) {
                lifecycle.tick()
                delay(400)
            }
        }
        PasswordManagerApp(
            repository = repository,
            biometric = UnavailableBiometricProvider,
            onPendingChangesChanged = { pendingChanges = it },
        )
    }
}
