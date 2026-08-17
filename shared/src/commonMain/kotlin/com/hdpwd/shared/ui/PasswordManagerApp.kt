package com.hdpwd.shared.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.hdpwd.shared.sync.S3ProviderPreset
import com.hdpwd.shared.sync.normalizeObjectPrefix
import com.hdpwd.shared.platform.platformHttpClient
import com.hdpwd.shared.sync.KtorS3ObjectStore
import com.hdpwd.shared.sync.awsAmzDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.BrowseItem
import com.hdpwd.shared.domain.ColorRules
import com.hdpwd.shared.domain.Folder
import com.hdpwd.shared.domain.KeyRules
import com.hdpwd.shared.domain.Label
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.PasswordPolicy
import com.hdpwd.shared.domain.SyncStatus
import com.hdpwd.shared.domain.SyncTarget
import com.hdpwd.shared.domain.VaultEditor
import com.hdpwd.shared.domain.VaultQueries
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.crypto.platformCryptoProvider
import com.hdpwd.shared.crypto.PasswordGenerator
import com.hdpwd.shared.application.PasswordRecoveryService
import com.hdpwd.shared.security.ClipboardPort
import com.hdpwd.shared.security.SensitiveClipboardController
import com.hdpwd.shared.security.AuthorizationSession
import com.hdpwd.shared.security.DeviceBiometric
import com.hdpwd.shared.security.DeviceLockRecord
import com.hdpwd.shared.security.DeviceUnlockPreference
import com.hdpwd.shared.security.LocalEnvelopeService
import com.hdpwd.shared.security.OperationPurpose
import com.hdpwd.shared.security.UserRecoveryEnvelope
import com.hdpwd.shared.storage.BackupNaming
import com.hdpwd.shared.storage.BackupService
import com.hdpwd.shared.storage.DefaultKdfParameters
import com.hdpwd.shared.storage.LocalAppRepository
import com.hdpwd.shared.storage.PersistedUserMeta
import com.hdpwd.shared.platform.BackupFilePort
import com.hdpwd.shared.platform.UnsupportedBackupFilePort
import com.hdpwd.shared.security.BiometricAvailability
import com.hdpwd.shared.security.BiometricProvider
import com.hdpwd.shared.security.LocalEnvelopeKey
import com.hdpwd.shared.security.UnavailableBiometricProvider
import androidx.compose.material3.Switch
import com.hdpwd.shared.sync.S3CredentialVault
import com.hdpwd.shared.sync.S3Credentials
import com.hdpwd.shared.sync.S3TargetService
import com.hdpwd.shared.sync.SealedS3CredentialPayload
import com.hdpwd.shared.sync.SyncScheduler
import com.hdpwd.shared.sync.SyncTargetApprovalService
import com.hdpwd.shared.sync.VaultS3SyncService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import androidx.compose.runtime.DisposableEffect

/**
 * 跨平台密码管理器根 UI。
 */
@Composable
fun PasswordManagerApp(
    clipboard: ClipboardPort? = null,
    repository: LocalAppRepository? = null,
    biometric: BiometricProvider = UnavailableBiometricProvider,
    backupFiles: BackupFilePort = UnsupportedBackupFilePort,
    onPendingChangesChanged: (Boolean) -> Unit = {},
) {
    MaterialTheme {
        var screen by remember { mutableStateOf(AppScreen.USERS) }
        var selectedUser by remember { mutableStateOf<UserSummary?>(null) }
        val users = remember { mutableStateListOf<UserSummary>() }
        val crypto = remember { platformCryptoProvider() }
        val envelopeService = remember { LocalEnvelopeService(crypto, DefaultKdfParameters) }
        val session = remember {
            AuthorizationSession({ Clock.System.now().toEpochMilliseconds() })
        }
        val clipboardScope = rememberCoroutineScope()
        val clipboardController = remember(clipboard) {
            clipboard?.let { SensitiveClipboardController(clipboardScope, it) }
        }
        var bootstrapped by remember { mutableStateOf(repository == null) }
        var deviceLock by remember { mutableStateOf<DeviceLockRecord?>(null) }
        var deviceBiometricSealed by remember { mutableStateOf<ByteArray?>(null) }
        var pendingAuth by remember { mutableStateOf<PendingAuth?>(null) }

        LaunchedEffect(repository) {
            val repo = repository ?: return@LaunchedEffect
            if (users.isNotEmpty() || deviceLock != null) {
                bootstrapped = true
                return@LaunchedEffect
            }
            runCatching {
                deviceLock = repo.readDeviceLock()
                deviceBiometricSealed = repo.readDeviceBiometricSealed()
                repo.listUsers()
                    .distinctBy { it.id }
                    .map { meta ->
                        val envelope = repo.readEnvelope(meta.id) ?: UserRecoveryEnvelope(
                            encryptedRecoveryPassword = byteArrayOf(),
                        )
                        UserSummary(
                            id = EntityId(meta.id),
                            name = meta.username,
                            vault = VaultState(EntityId(meta.id)),
                            recoveryEnvelope = envelope,
                        )
                    }
            }.onSuccess { loaded ->
                users.clear()
                users.addAll(loaded)
                if (repo.listUsers().size != loaded.size) {
                    repo.saveUsers(
                        loaded.map {
                            PersistedUserMeta(id = it.id.value, username = it.name)
                        },
                    )
                }
            }
            bootstrapped = true
            if (deviceLock == null) {
                screen = AppScreen.SETUP_DEVICE
            }
        }

        suspend fun persistIndex() {
            val repo = repository ?: return
            repo.saveUsers(
                users.map { PersistedUserMeta(id = it.id.value, username = it.name) },
            )
        }

        suspend fun openVault(user: UserSummary): Boolean {
            val lock = deviceLock ?: return false
            val permit = session.acquire(OperationPurpose.GENERATE_PASSWORD)
                ?: return false
            return try {
                session.withEnvelopeKeySuspending(permit) { deviceKey ->
                    val vault = envelopeService.withRecoveryPassword(
                        user.recoveryEnvelope,
                        deviceKey,
                        user.id.value,
                        lock.generation,
                    ) { recoveryPassword ->
                        repository?.readVault(user.id.value, recoveryPassword) ?: user.vault
                    }
                    val index = users.indexOfFirst { it.id == user.id }
                    val updated = user.copy(vault = vault)
                    if (index >= 0) users[index] = updated
                    selectedUser = updated
                    screen = AppScreen.VAULT
                }
                true
            } catch (_: Throwable) {
                false
            } finally {
                permit.close()
            }
        }

        fun requestCreateUser() {
            if (session.canStart()) {
                screen = AppScreen.CREATE_USER
            } else {
                pendingAuth = PendingAuth.CREATE_USER
            }
        }

        fun requestOpenUser(user: UserSummary) {
            val lock = deviceLock
            if (lock == null) {
                screen = AppScreen.SETUP_DEVICE
                return
            }
            if (user.recoveryEnvelope.needsRebind(lock.generation)) {
                selectedUser = user
                screen = AppScreen.REBIND_USER
                return
            }
            clipboardScope.launch {
                if (session.canStart()) {
                    if (!openVault(user)) {
                        pendingAuth = PendingAuth.OPEN_USER
                        selectedUser = user
                    }
                } else {
                    selectedUser = user
                    pendingAuth = PendingAuth.OPEN_USER
                }
            }
        }

        if (!bootstrapped) {
            SafeContent {
                Text("正在加载本地用户…")
            }
            return@MaterialTheme
        }

        pendingAuth?.let { auth ->
            DeviceAuthDialog(
                deviceLock = deviceLock,
                users = users,
                biometric = biometric,
                biometricSealed = deviceBiometricSealed,
                envelopeService = envelopeService,
                repository = repository,
                session = session,
                startForgot = auth == PendingAuth.FORGOT_RESET,
                onDismiss = { pendingAuth = null },
                onUnlocked = {
                    val action = pendingAuth
                    pendingAuth = null
                    clipboardScope.launch {
                        when (action) {
                            PendingAuth.CREATE_USER -> screen = AppScreen.CREATE_USER
                            PendingAuth.OPEN_USER -> selectedUser?.let { openVault(it) }
                            PendingAuth.RESUME_SETTINGS -> screen = AppScreen.DEVICE_SETTINGS
                            PendingAuth.FORGOT_RESET -> screen = AppScreen.USERS
                            null -> Unit
                        }
                    }
                },
                onDeviceLockChanged = { record, sealed ->
                    deviceLock = record
                    deviceBiometricSealed = sealed
                    clipboardScope.launch {
                        users.forEachIndexed { index, user ->
                            val env = repository?.readEnvelope(user.id.value)
                            if (env != null) users[index] = user.copy(recoveryEnvelope = env)
                        }
                    }
                },
            )
        }

        when (screen) {
            AppScreen.SETUP_DEVICE -> SetupDeviceScreen(
                envelopeService = envelopeService,
                biometric = biometric,
                repository = repository,
                session = session,
                onReady = { record, sealed ->
                    deviceLock = record
                    deviceBiometricSealed = sealed
                    screen = AppScreen.USERS
                },
            )
            AppScreen.USERS -> UserListScreen(
                users = users,
                deviceGeneration = deviceLock?.generation,
                onCreate = { requestCreateUser() },
                onSettings = { screen = AppScreen.DEVICE_SETTINGS },
                onSelect = { requestOpenUser(it) },
            )
            AppScreen.DEVICE_SETTINGS -> DeviceSettingsScreen(
                deviceLock = deviceLock,
                biometric = biometric,
                biometricSealed = deviceBiometricSealed,
                envelopeService = envelopeService,
                repository = repository,
                session = session,
                onBack = { screen = AppScreen.USERS },
                onDeviceLockChanged = { record, sealed ->
                    deviceLock = record
                    deviceBiometricSealed = sealed
                },
                onNeedAuth = { pendingAuth = PendingAuth.RESUME_SETTINGS },
                onForgotPassword = { pendingAuth = PendingAuth.FORGOT_RESET },
            )
            AppScreen.CREATE_USER -> CreateUserScreen(
                existingNames = users.map { it.name }.toSet(),
                envelopeService = envelopeService,
                session = session,
                repository = repository,
                backupFiles = backupFiles,
                deviceGeneration = deviceLock?.generation ?: "",
                onCancel = { screen = AppScreen.USERS },
                onCreated = { summary ->
                    users += summary
                    selectedUser = summary
                    screen = AppScreen.VAULT
                },
            )
            AppScreen.REBIND_USER -> selectedUser?.let { user ->
                RebindUserScreen(
                    user = user,
                    deviceLock = deviceLock,
                    envelopeService = envelopeService,
                    session = session,
                    repository = repository,
                    onCancel = {
                        selectedUser = null
                        screen = AppScreen.USERS
                    },
                    onRebound = { updated ->
                        val index = users.indexOfFirst { it.id == user.id }
                        if (index >= 0) users[index] = updated
                        selectedUser = updated
                        screen = AppScreen.VAULT
                    },
                )
            } ?: run { screen = AppScreen.USERS }
            AppScreen.VAULT -> selectedUser?.let { user ->
                VaultScreen(
                    user = user,
                    envelopeService = envelopeService,
                    session = session,
                    clipboardController = clipboardController,
                    repository = repository,
                    backupFiles = backupFiles,
                    deviceGeneration = deviceLock?.generation ?: "",
                    onVaultChanged = { updated ->
                        val index = users.indexOfFirst { it.id == user.id }
                        if (index >= 0) {
                            users[index] = users[index].copy(vault = updated)
                            selectedUser = users[index]
                        }
                    },
                    onUserDeleted = {
                        clipboardScope.launch {
                            repository?.deleteUser(user.id.value)
                            users.removeAll { it.id == user.id }
                            persistIndex()
                            selectedUser = null
                            screen = AppScreen.USERS
                        }
                    },
                    onAuthExpired = {
                        session.clear()
                        selectedUser = null
                        onPendingChangesChanged(false)
                        screen = AppScreen.USERS
                    },
                    onPendingChangesChanged = onPendingChangesChanged,
                    onBack = {
                        selectedUser = null
                        onPendingChangesChanged(false)
                        screen = AppScreen.USERS
                    },
                )
            } ?: run { screen = AppScreen.USERS }
        }
    }
}

private enum class AppScreen {
    SETUP_DEVICE,
    USERS,
    DEVICE_SETTINGS,
    CREATE_USER,
    REBIND_USER,
    VAULT,
}

private enum class PendingAuth {
    CREATE_USER,
    OPEN_USER,
    RESUME_SETTINGS,
    FORGOT_RESET,
}

private data class UserSummary(
    val id: EntityId,
    val name: String,
    val vault: VaultState,
    val recoveryEnvelope: UserRecoveryEnvelope,
)

@Composable
private fun UserListScreen(
    users: List<UserSummary>,
    deviceGeneration: String?,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    onSelect: (UserSummary) -> Unit,
) {
    SafeScreen(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "创建或导入用户")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("hd-pwd", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设备设置")
                }
            }
            Text("本地用户", style = MaterialTheme.typography.titleMedium)
            Text("用户列表无需验证。打开用户或创建用户时才会加解密。")
            if (users.isEmpty()) {
                Text("当前设备还没有用户，点击右下角创建或导入")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users, key = { "${it.id.value}:${it.name}" }) { user ->
                        val unbound = deviceGeneration == null ||
                            user.recoveryEnvelope.needsRebind(deviceGeneration)
                        Card(onClick = { onSelect(user) }, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(user.name)
                                if (unbound) {
                                    Text("待用恢复密码重新绑定", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupDeviceScreen(
    envelopeService: LocalEnvelopeService,
    biometric: BiometricProvider,
    repository: LocalAppRepository?,
    session: AuthorizationSession,
    onReady: (DeviceLockRecord, ByteArray?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var enableBiometric by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val biometricAvailable = biometric.availability() == BiometricAvailability.AVAILABLE

    SafeContent {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设置设备主密码", style = MaterialTheme.typography.headlineSmall)
            Text("这台设备只用一把主密码解开本地缓存的恢复密码。用户列表本身不会上锁。")
            SensitivePasswordField(password, { password = it }, "设备主密码")
            SensitivePasswordField(confirm, { confirm = it }, "确认主密码")
            if (biometricAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableBiometric, onCheckedChange = { enableBiometric = it })
                    Text("启用生物识别（后续验证默认使用）")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    error = when {
                        password.isBlank() -> "主密码不能为空"
                        password != confirm -> "两次输入的主密码不一致"
                        else -> null
                    }
                    if (error != null) return@Button
                    scope.launch {
                        try {
                            val created = envelopeService.createDeviceLock(password)
                            var record = created.record
                            var sealed: ByteArray? = null
                            if (enableBiometric && biometricAvailable) {
                                val keyBytes = created.deviceKey.use { it.copyOf() }
                                try {
                                    sealed = biometric.seal(DeviceBiometric.LABEL, keyBytes)
                                    record = record.copy(preferBiometric = true)
                                } catch (ex: Throwable) {
                                    error = "生物识别启用失败：${UserFacingText.fromThrowable(ex, "未知错误")}。可先跳过，稍后在设置中开启。"
                                    sealed = null
                                    record = record.copy(preferBiometric = false)
                                } finally {
                                    keyBytes.fill(0)
                                }
                            }
                            repository?.writeDeviceLock(record)
                            repository?.writeDeviceBiometricSealed(sealed)
                            session.open(created.deviceKey)
                            onReady(record, sealed)
                            password = ""
                            confirm = ""
                        } catch (ex: Throwable) {
                            error = UserFacingText.fromThrowable(ex, "设置设备锁失败")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun DeviceSettingsScreen(
    deviceLock: DeviceLockRecord?,
    biometric: BiometricProvider,
    biometricSealed: ByteArray?,
    envelopeService: LocalEnvelopeService,
    repository: LocalAppRepository?,
    session: AuthorizationSession,
    onBack: () -> Unit,
    onDeviceLockChanged: (DeviceLockRecord, ByteArray?) -> Unit,
    onNeedAuth: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val biometricAvailable = biometric.availability() == BiometricAvailability.AVAILABLE
    val preferBiometric = deviceLock?.preferBiometric == true && biometricSealed != null

    SafeContent {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTopBar(title = "设备设置", onClose = onBack)
            if (biometricAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("生物识别解锁")
                    Switch(
                        checked = preferBiometric,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                val lock = deviceLock ?: return@launch
                                if (!session.canStart()) {
                                    onNeedAuth()
                                    return@launch
                                }
                                try {
                                    if (enabled) {
                                        val permit = session.acquire(OperationPurpose.DEVICE_SETTINGS)
                                            ?: error("授权已失效")
                                        try {
                                            session.withEnvelopeKeySuspending(permit) { key ->
                                                val bytes = key.use { it.copyOf() }
                                                try {
                                                    val sealed = biometric.seal(DeviceBiometric.LABEL, bytes)
                                                    val next = lock.copy(preferBiometric = true)
                                                    repository?.writeDeviceLock(next)
                                                    repository?.writeDeviceBiometricSealed(sealed)
                                                    onDeviceLockChanged(next, sealed)
                                                } finally {
                                                    bytes.fill(0)
                                                }
                                            }
                                        } finally {
                                            permit.close()
                                        }
                                    } else {
                                        biometric.delete(DeviceBiometric.LABEL)
                                        val next = lock.copy(preferBiometric = false)
                                        repository?.writeDeviceLock(next)
                                        repository?.writeDeviceBiometricSealed(null)
                                        onDeviceLockChanged(next, null)
                                    }
                                    message = "已更新生物识别"
                                    error = null
                                } catch (ex: Throwable) {
                                    error = UserFacingText.fromThrowable(ex, "更新生物识别失败")
                                }
                            }
                        },
                    )
                }
            }
            Text("修改主密码", style = MaterialTheme.typography.titleMedium)
            Text("能通过生物识别或当前主密码解开即可设置新密码，不会让其他用户掉绑。")
            SensitivePasswordField(newPassword, { newPassword = it }, "新主密码")
            SensitivePasswordField(confirm, { confirm = it }, "确认新主密码")
            Button(onClick = {
                error = when {
                    newPassword.isBlank() -> "新主密码不能为空"
                    newPassword != confirm -> "两次输入的主密码不一致"
                    else -> null
                }
                if (error != null) return@Button
                if (!session.canStart()) {
                    onNeedAuth()
                    return@Button
                }
                val lock = deviceLock ?: return@Button
                scope.launch {
                    val permit = session.acquire(OperationPurpose.DEVICE_SETTINGS)
                    if (permit == null) {
                        onNeedAuth()
                        return@launch
                    }
                    try {
                        session.withEnvelopeKeySuspending(permit) { key ->
                            val rewrapped = envelopeService.rewrapMasterPassword(lock, key, newPassword)
                            var sealed = biometricSealed
                            if (rewrapped.preferBiometric && biometricAvailable) {
                                val bytes = key.use { it.copyOf() }
                                try {
                                    sealed = biometric.seal(DeviceBiometric.LABEL, bytes)
                                } finally {
                                    bytes.fill(0)
                                }
                            }
                            repository?.writeDeviceLock(rewrapped)
                            repository?.writeDeviceBiometricSealed(sealed)
                            onDeviceLockChanged(rewrapped, sealed)
                        }
                        message = "主密码已更新"
                        newPassword = ""
                        confirm = ""
                        error = null
                    } catch (ex: Throwable) {
                        error = UserFacingText.fromThrowable(ex, "修改主密码失败")
                    } finally {
                        permit.close()
                    }
                }
            }) { Text("保存新主密码") }
            Text("忘记主密码", style = MaterialTheme.typography.titleMedium)
            Text("将轮换设备钥匙，只有当时用恢复密码验证成功的用户保持绑定。")
            OutlinedButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth()) {
                Text("用恢复密码重置设备锁")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it) }
        }
    }
}

@Composable
private fun DeviceAuthDialog(
    deviceLock: DeviceLockRecord?,
    users: List<UserSummary>,
    biometric: BiometricProvider,
    biometricSealed: ByteArray?,
    envelopeService: LocalEnvelopeService,
    repository: LocalAppRepository?,
    session: AuthorizationSession,
    startForgot: Boolean = false,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    onDeviceLockChanged: (DeviceLockRecord, ByteArray?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var usePassword by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(startForgot) }
    var recoveryPassword by remember { mutableStateOf("") }
    var selectedResetUser by remember { mutableStateOf(users.firstOrNull()?.id) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val autoBiometric = DeviceUnlockPreference.shouldAutoPromptBiometric(
        preferBiometric = deviceLock?.preferBiometric == true,
        availability = biometric.availability(),
        hasSealedBlob = biometricSealed != null,
    )

    suspend fun finish(key: LocalEnvelopeKey) {
        session.open(key)
        password = ""
        recoveryPassword = ""
        onUnlocked()
    }

    suspend fun unlockByBiometric() {
        require(deviceLock != null) { "尚未设置设备锁" }
        val sealed = biometricSealed ?: error("没有生物识别封装")
        val opened = biometric.open(DeviceBiometric.LABEL, sealed)
        finish(LocalEnvelopeKey(opened))
    }

    LaunchedEffect(autoBiometric) {
        if (autoBiometric && !usePassword && !forgot) {
            working = true
            try {
                unlockByBiometric()
            } catch (_: Throwable) {
                usePassword = true
                error = "生物识别失败，请改用主密码"
            } finally {
                working = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forgot) "用恢复密码重置设备锁" else "设备验证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (forgot) {
                    Text("选择一个本地用户并输入其恢复密码。其他用户将变为待重绑。")
                    users.forEach { user ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedResetUser == user.id,
                                onCheckedChange = { selectedResetUser = user.id },
                            )
                            Text(user.name)
                        }
                    }
                    SensitivePasswordField(recoveryPassword, { recoveryPassword = it }, "该用户的恢复密码")
                    SensitivePasswordField(password, { password = it }, "新设备主密码")
                } else if (usePassword || !autoBiometric) {
                    SensitivePasswordField(password, { password = it }, "设备主密码")
                    TextButton(onClick = { forgot = true }) { Text("忘记主密码") }
                    if (autoBiometric) {
                        TextButton(onClick = { usePassword = false; error = null }) {
                            Text("改用生物识别")
                        }
                    }
                } else {
                    Text("正在请求生物识别…")
                    TextButton(onClick = { usePassword = true }) { Text("使用主密码") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        working = true
                        try {
                            if (forgot) {
                                val user = users.firstOrNull { it.id == selectedResetUser }
                                    ?: error("请选择用户")
                                require(password.isNotBlank()) { "新主密码不能为空" }
                                repository?.authenticateVault(user.id.value, recoveryPassword)
                                val rotated = envelopeService.rotateDeviceLock(password)
                                val rebound = envelopeService.sealRecoveryPassword(
                                    rotated.deviceKey,
                                    rotated.record.generation,
                                    user.id.value,
                                    recoveryPassword,
                                )
                                repository?.writeDeviceLock(rotated.record)
                                repository?.writeDeviceBiometricSealed(null)
                                repository?.writeEnvelope(user.id.value, rebound)
                                biometric.delete(DeviceBiometric.LABEL)
                                onDeviceLockChanged(rotated.record, null)
                                session.open(rotated.deviceKey)
                                onUnlocked()
                            } else if (usePassword || !autoBiometric) {
                                val lock = deviceLock ?: error("尚未设置设备锁")
                                val key = envelopeService.unlockWithMasterPassword(lock, password)
                                finish(key)
                            } else {
                                unlockByBiometric()
                            }
                            error = null
                        } catch (ex: Throwable) {
                            error = UserFacingText.fromThrowable(ex, "验证失败")
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !working,
            ) { Text(if (forgot) "重置" else "验证") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CreateUserScreen(
    existingNames: Set<String>,
    envelopeService: LocalEnvelopeService,
    session: AuthorizationSession,
    repository: LocalAppRepository?,
    backupFiles: BackupFilePort,
    deviceGeneration: String,
    onCancel: () -> Unit,
    onCreated: (UserSummary) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var recoveryPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importLabel by remember { mutableStateOf<String?>(null) }
    val backupService = remember { BackupService.production(platformCryptoProvider()) }

    SafeContent {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTopBar(title = "创建 / 导入用户", onClose = onCancel)
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SensitivePasswordField(
                value = recoveryPassword,
                onValueChange = { recoveryPassword = it },
                label = "恢复密码",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = {
                    scope.launch {
                        val bytes = backupFiles.openBackup()
                        if (bytes == null) {
                            error = "未选择备份文件"
                        } else {
                            importBytes = bytes
                            importLabel = "已选择备份（${bytes.size} 字节）"
                            error = null
                        }
                    }
                }) { Text("导入备份（可选）") }
                if (importLabel != null) {
                    TextButton(onClick = {
                        importBytes = null
                        importLabel = null
                    }) { Text("清除") }
                }
            }
            importLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(onClick = {
                    error = when {
                        name.isBlank() -> "用户名不能为空"
                        name in existingNames -> "用户名已存在"
                        recoveryPassword.isBlank() -> "恢复密码不能为空"
                        !session.canStart() -> "需要先通过设备验证"
                        else -> null
                    }
                    if (error == null) {
                        scope.launch {
                            val permit = session.acquire(OperationPurpose.CREATE_USER)
                            if (permit == null) {
                                error = "需要先通过设备验证"
                                return@launch
                            }
                            var createdId: String? = null
                            try {
                                val importedVault = importBytes?.let {
                                    backupService.import(recoveryPassword, it)
                                }
                                val id = EntityId(
                                    platformCryptoProvider().randomBytes(16)
                                        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') },
                                )
                                createdId = id.value
                                val vault = importedVault?.let { imported ->
                                    SyncTargetApprovalService()
                                        .activateLocalBackupTargets(imported.copy(vaultId = id))
                                } ?: VaultState(id)
                                session.withEnvelopeKeySuspending(permit) { deviceKey ->
                                    val envelope = envelopeService.sealRecoveryPassword(
                                        deviceKey,
                                        deviceGeneration,
                                        id.value,
                                        recoveryPassword,
                                    )
                                    repository?.writeEnvelope(id.value, envelope)
                                    repository?.writeVault(id.value, recoveryPassword, vault)
                                    repository?.let { repo ->
                                        val metas = (repo.listUsers() + PersistedUserMeta(
                                            id = id.value,
                                            username = name,
                                        )).distinctBy { it.id }
                                        repo.saveUsers(metas)
                                    }
                                    onCreated(
                                        UserSummary(
                                            id = id,
                                            name = name,
                                            vault = vault,
                                            recoveryEnvelope = envelope,
                                        ),
                                    )
                                }
                                recoveryPassword = ""
                            } catch (ex: Throwable) {
                                createdId?.let { runCatching { repository?.deleteUser(it) } }
                                error = UserFacingText.fromThrowable(ex, "创建失败：请检查恢复密码是否正确")
                            } finally {
                                permit.close()
                            }
                        }
                    }
                }) {
                    Text(if (importBytes != null) "创建并导入" else "创建")
                }
            }
        }
    }
}

@Composable
private fun RebindUserScreen(
    user: UserSummary,
    deviceLock: DeviceLockRecord?,
    envelopeService: LocalEnvelopeService,
    session: AuthorizationSession,
    repository: LocalAppRepository?,
    onCancel: () -> Unit,
    onRebound: (UserSummary) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var recoveryPassword by remember { mutableStateOf("") }
    var oldLocalPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lock = deviceLock
    val legacy = user.recoveryEnvelope.isLegacy()

    SafeContent {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTopBar(title = "重新绑定 ${user.name}", onClose = onCancel)
            Text("该用户尚未绑定当前设备锁。请输入恢复密码（旧数据也可用原来的本机主密码）。")
            SensitivePasswordField(recoveryPassword, { recoveryPassword = it }, "恢复密码")
            if (legacy) {
                SensitivePasswordField(oldLocalPassword, { oldLocalPassword = it }, "旧本机主密码（可选）")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(onClick = {
                    if (lock == null) {
                        error = "尚未设置设备锁"
                        return@Button
                    }
                    if (!session.canStart()) {
                        error = "需要先通过设备验证"
                        return@Button
                    }
                    scope.launch {
                        val permit = session.acquire(OperationPurpose.CREATE_USER)
                            ?: session.acquire(OperationPurpose.DEVICE_SETTINGS)
                        if (permit == null) {
                            error = "需要先通过设备验证"
                            return@launch
                        }
                        try {
                            val recovered = when {
                                recoveryPassword.isNotBlank() -> {
                                    repository?.authenticateVault(user.id.value, recoveryPassword)
                                    recoveryPassword
                                }
                                legacy && oldLocalPassword.isNotBlank() -> {
                                    val legacyKey = envelopeService.unlockLegacyLocalKey(
                                        user.recoveryEnvelope,
                                        oldLocalPassword,
                                    )
                                    try {
                                        envelopeService.withLegacyRecoveryPassword(
                                            user.recoveryEnvelope,
                                            legacyKey,
                                        ) { it.toString() }
                                    } finally {
                                        legacyKey.clear()
                                    }
                                }
                                else -> {
                                    error = "请输入恢复密码"
                                    return@launch
                                }
                            }
                            session.withEnvelopeKeySuspending(permit) { deviceKey ->
                                val envelope = envelopeService.sealRecoveryPassword(
                                    deviceKey,
                                    lock.generation,
                                    user.id.value,
                                    recovered,
                                )
                                val vault = repository?.readVault(user.id.value, recovered) ?: user.vault
                                repository?.writeEnvelope(user.id.value, envelope)
                                onRebound(user.copy(vault = vault, recoveryEnvelope = envelope))
                            }
                        } catch (ex: Throwable) {
                            error = UserFacingText.fromThrowable(ex, "绑定失败")
                        } finally {
                            permit.close()
                        }
                    }
                }) { Text("绑定并进入") }
            }
        }
    }
}

@Composable
private fun VaultScreen(
    user: UserSummary,
    envelopeService: LocalEnvelopeService,
    session: AuthorizationSession,
    clipboardController: SensitiveClipboardController?,
    repository: LocalAppRepository?,
    backupFiles: BackupFilePort,
    deviceGeneration: String,
    onVaultChanged: (VaultState) -> Unit,
    onUserDeleted: () -> Unit,
    onAuthExpired: () -> Unit,
    onPendingChangesChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val queries = remember { VaultQueries() }
    val editor = remember { VaultEditor() }
    val backupService = remember { BackupService.production(platformCryptoProvider()) }
    val s3Service = remember { S3TargetService() }
    val syncService = remember { VaultS3SyncService() }
    var vault by remember(user.id.value) { mutableStateOf(user.vault) }
    LaunchedEffect(user.vault) {
        // 父级回写不得覆盖本机更高版本（避免进行中的旧同步结果把新编辑冲掉）
        if (user.vault.contentVersion() >= vault.contentVersion()) {
            vault = user.vault
        }
    }
    var query by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    var addingFolder by remember { mutableStateOf(false) }
    var currentFolderId by remember { mutableStateOf<EntityId?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showS3Settings by remember { mutableStateOf(false) }
    var showExitSyncDialog by remember { mutableStateOf(false) }
    var exitSyncWaiting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var syncBanner by remember { mutableStateOf<String?>(null) }
    val syncBridge = remember(user.id.value) {
        object {
            var vaultSnapshot: VaultState = user.vault
            var localSaveReady: Boolean = true
            /** 添加/编辑密码项或文件夹时为 true，用于取消待执行静默同步。 */
            var editorOpen: Boolean = false
            /** 递增后使进行中的同步结果作废，避免旧快照覆盖新编辑。 */
            var syncGeneration: Long = 0L
            var unlock: (suspend (suspend (CharSequence) -> Unit) -> Boolean)? = null
            var publishVault: ((VaultState) -> Unit)? = null
            var publishBanner: ((String?) -> Unit)? = null
            var persistVault: (suspend (VaultState, CharSequence) -> Unit)? = null
        }
    }
    val syncScheduler = remember(user.id.value) {
        SyncScheduler(
            scope = scope,
            localSaveCompleted = { syncBridge.localSaveReady },
            quietPeriodMillis = 5_000L,
            sync = { target ->
                val generation = syncBridge.syncGeneration
                val unlock = syncBridge.unlock ?: return@SyncScheduler
                unlock { recoveryPassword ->
                    if (generation != syncBridge.syncGeneration) return@unlock
                    val snapshot = syncBridge.vaultSnapshot
                    val syncing = snapshot.copy(
                        syncTargets = snapshot.syncTargets.map {
                            if (it.id == target.id) it.copy(status = SyncStatus.SYNCING) else it
                        },
                    )
                    syncBridge.vaultSnapshot = syncing
                    if (generation == syncBridge.syncGeneration) {
                        syncBridge.publishVault?.invoke(syncing)
                    }
                    val result = syncService.syncTarget(target, syncBridge.vaultSnapshot, recoveryPassword)
                    if (generation != syncBridge.syncGeneration) return@unlock
                    val next = result.vault.copy(
                        syncTargets = result.vault.syncTargets.map {
                            if (it.id == result.target.id) result.target else it
                        },
                    )
                    syncBridge.vaultSnapshot = next
                    syncBridge.publishVault?.invoke(next)
                    if (result.target.status == SyncStatus.SUCCESS) {
                        syncBridge.persistVault?.invoke(next, recoveryPassword)
                    }
                    syncBridge.publishBanner?.invoke(
                        when {
                            result.target.status == SyncStatus.FAILED ->
                                "同步失败：${UserFacingText.fromErrorCode(result.target.lastErrorCode) ?: "请检查配置"}"
                            result.target.status == SyncStatus.SUCCESS && result.changed ->
                                "已同步到 ${UserFacingText.providerName(result.target.provider)}"
                            else -> null
                        },
                    )
                }
            },
        )
    }
    // 静默等待期间不要用 UI 状态覆盖快照，避免冲掉 commit 写入的最新内容
    if (!syncScheduler.hasPendingJobs()) {
        syncBridge.vaultSnapshot = vault
    }
    syncBridge.publishVault = { next ->
        vault = next
        onVaultChanged(next)
    }
    syncBridge.publishBanner = { syncBanner = it }
    syncBridge.persistVault = { next, recoveryPassword ->
        repository?.writeVault(user.id.value, recoveryPassword, next)
    }
    DisposableEffect(user.id.value) {
        onDispose { syncScheduler.cancel() }
    }
    val currentDepth = vault.folders.firstOrNull { it.id == currentFolderId }?.depth ?: 1
    val canAddFolder = currentDepth < 3

    suspend fun withRecoveryPassword(block: suspend (CharSequence) -> Unit): Boolean {
        val permit = session.acquire(OperationPurpose.SYNC)
            ?: session.acquire(OperationPurpose.GENERATE_PASSWORD)
        if (permit == null) {
            authMessage = "授权已失效，请返回后重新验证"
            return false
        }
        return try {
            session.withEnvelopeKeySuspending(permit) { envelopeKey ->
                envelopeService.withRecoveryPassword(
                    user.recoveryEnvelope,
                    envelopeKey,
                    user.id.value,
                    deviceGeneration,
                ) { recoveryPassword ->
                    block(recoveryPassword)
                }
            }
            true
        } catch (_: Throwable) {
            authMessage = "无法解锁恢复密码，请重新验证"
            false
        } finally {
            permit.close()
        }
    }

    syncBridge.unlock = { block -> withRecoveryPassword(block) }

    fun scheduleRemoteSync(targets: List<SyncTarget>, immediate: Boolean = false) {
        syncScheduler.schedule(
            targets = targets,
            quietPeriodMillis = if (immediate) 0L else 5_000L,
        )
    }

    fun hasReadySyncTargets(targets: List<SyncTarget> = vault.syncTargets): Boolean =
        targets.any { it.enabled && it.confirmed }

    fun invalidateInFlightSync() {
        syncBridge.syncGeneration++
    }

    fun pauseSyncForEditor() {
        syncBridge.editorOpen = true
        invalidateInFlightSync()
        syncScheduler.cancel()
    }

    fun resumeSyncAfterEditor(scheduleQuiet: Boolean) {
        syncBridge.editorOpen = false
        if (scheduleQuiet && hasReadySyncTargets()) {
            scheduleRemoteSync(vault.syncTargets, immediate = false)
        }
    }

    fun triggerManualSync() {
        if (!hasReadySyncTargets()) {
            syncBanner = "请先配置并启用至少一个 S3 同步目标"
            return
        }
        invalidateInFlightSync()
        syncBridge.editorOpen = false
        syncBridge.localSaveReady = true
        syncBridge.vaultSnapshot = vault
        scheduleRemoteSync(vault.syncTargets, immediate = true)
    }

    fun needsExitSyncPrompt(): Boolean {
        if (!hasReadySyncTargets()) return false
        if (syncScheduler.hasPendingJobs()) return true
        if (!syncBridge.localSaveReady) return true
        return vault.syncTargets.any {
            it.enabled && it.confirmed &&
                (it.status == SyncStatus.SYNCING ||
                    it.status == SyncStatus.PENDING ||
                    it.status == SyncStatus.FAILED)
        }
    }

    LaunchedEffect(vault, syncBridge.localSaveReady) {
        onPendingChangesChanged(needsExitSyncPrompt() || !syncBridge.localSaveReady)
    }

    fun requestLeaveVault() {
        if (needsExitSyncPrompt()) {
            showExitSyncDialog = true
        } else {
            onBack()
        }
    }

    // 每次进入密码库（含授权过期后重新验证进入）都做一次 S3 同步检测
    LaunchedEffect(Unit) {
        if (!hasReadySyncTargets()) return@LaunchedEffect
        syncBridge.localSaveReady = true
        scheduleRemoteSync(vault.syncTargets, immediate = true)
    }

    fun commitVault(next: VaultState, syncImmediately: Boolean = false) {
        invalidateInFlightSync()
        syncBridge.editorOpen = false
        vault = next
        syncBridge.vaultSnapshot = next
        onVaultChanged(next)
        syncBridge.localSaveReady = false
        // 先挂上静默/立即同步任务，等本地保存完成后再执行，避免“保存后忘记调度”
        if (hasReadySyncTargets(next.syncTargets)) {
            scheduleRemoteSync(next.syncTargets, immediate = syncImmediately)
        }
        scope.launch {
            val saved = withRecoveryPassword { recoveryPassword ->
                repository?.writeVault(user.id.value, recoveryPassword, next)
            }
            syncBridge.localSaveReady = saved
            if (!saved) {
                syncScheduler.cancel()
            }
        }
    }

    if (showS3Settings) {
        S3SettingsScreen(
            targets = vault.syncTargets,
            vault = vault,
            onBack = { showS3Settings = false },
            onChange = { targets ->
                val previous = vault.syncTargets.associateBy { it.id.value }
                val newlyReady = targets.any { target ->
                    target.enabled && target.confirmed &&
                        previous[target.id.value].let { old ->
                            old == null || !old.confirmed || !old.enabled
                        }
                }
                commitVault(vault.copy(syncTargets = targets), syncImmediately = newlyReady)
            },
            onSyncNow = { triggerManualSync() },
            s3Service = s3Service,
            sealCredentials = { accessKeyId, secretAccessKey ->
                var sealed: SealedS3CredentialPayload? = null
                val ok = withRecoveryPassword { recoveryPassword ->
                    val crypto = platformCryptoProvider()
                    val vaultCrypto = S3CredentialVault(crypto, DefaultKdfParameters)
                    val secretBytes = secretAccessKey.encodeToByteArray()
                    val credentials = S3Credentials(accessKeyId, secretBytes)
                    try {
                        sealed = vaultCrypto.sealWithRecoveryPassword(recoveryPassword, credentials)
                    } finally {
                        credentials.clear()
                        secretBytes.fill(0)
                    }
                }
                if (!ok) {
                    error("授权已失效，请返回后重新验证")
                }
                sealed ?: error("封装 S3 凭据失败")
            },
        )
        return
    }
    if (showSettings) {
        VaultSettingsScreen(
            onBack = { showSettings = false },
            onOpenS3 = {
                showSettings = false
                showS3Settings = true
            },
        )
        return
    }
    if (editingEntry != null) {
        EntryEditorScreen(
            entry = editingEntry!!,
            isNew = false,
            onCancel = {
                editingEntry = null
                resumeSyncAfterEditor(scheduleQuiet = true)
            },
            onSave = { updated ->
                syncBridge.editorOpen = false
                commitVault(editor.updateEntry(vault, updated))
                editingEntry = null
            },
        )
        return
    }
    if (editingFolder != null) {
        FolderEditorScreen(
            folder = editingFolder!!,
            isNew = false,
            onCancel = {
                editingFolder = null
                resumeSyncAfterEditor(scheduleQuiet = true)
            },
            onSave = { updated ->
                syncBridge.editorOpen = false
                commitVault(editor.updateFolder(vault, updated))
                editingFolder = null
            },
        )
        return
    }
    if (addingFolder) {
        FolderEditorScreen(
            folder = Folder(
                id = EntityId("new-folder-${vault.folders.size}-${Clock.System.now().toEpochMilliseconds()}"),
                parentId = currentFolderId,
                name = "",
                colorHex = "#94A3B8",
                depth = currentDepth + 1,
            ),
            isNew = true,
            onCancel = {
                addingFolder = false
                resumeSyncAfterEditor(scheduleQuiet = true)
            },
            onSave = { created ->
                syncBridge.editorOpen = false
                commitVault(
                    editor.addFolder(
                        vault,
                        created.id,
                        created.parentId,
                        created.name,
                        created.colorHex,
                    ),
                )
                addingFolder = false
            },
        )
        return
    }
    if (addingEntry) {
        EntryEditorScreen(
            entry = PasswordEntry(
                id = EntityId("new-entry-${vault.entries.size}-${Clock.System.now().toEpochMilliseconds()}"),
                parentId = currentFolderId,
                key = "",
                title = "",
            ),
            isNew = true,
            onCancel = {
                addingEntry = false
                resumeSyncAfterEditor(scheduleQuiet = true)
            },
            onSave = { created ->
                syncBridge.editorOpen = false
                commitVault(editor.addEntry(vault, created))
                addingEntry = false
            },
            onImportRecipe = { recipeText ->
                val parsed = PasswordGenerator.parseRecipe(recipeText)
                PasswordEntry(
                    id = EntityId("tmp"),
                    parentId = currentFolderId,
                    key = parsed.key,
                    title = parsed.key,
                    policy = parsed.policy,
                )
            },
        )
        return
    }
    val visibleItems = if (query.isBlank()) {
        queries.browse(vault, currentFolderId)
    } else {
        queries.search(vault, currentFolderId, query).map { BrowseItem.EntryItem(it.entry) }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("确认删除") },
            text = {
                Text(
                    if (currentFolderId == null) "删除用户仅清除本地数据，远端 S3 备份会保留"
                    else "删除当前文件夹及其全部子内容？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    if (currentFolderId == null) {
                        onUserDeleted()
                    } else {
                        commitVault(
                            editor.deleteFolder(
                                vault,
                                currentFolderId!!,
                                Clock.System.now().toEpochMilliseconds(),
                                EntityId("delete-${vault.deviceSequence + 1}"),
                            ),
                        )
                        currentFolderId = null
                    }
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") }
            },
        )
    }
    exportMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("导出备份") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { exportMessage = null }) { Text("确定") }
            },
        )
    }
    syncBanner?.let { message ->
        AlertDialog(
            onDismissRequest = { syncBanner = null },
            title = { Text("S3 同步") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { syncBanner = null }) { Text("确定") }
            },
        )
    }
    authMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { authMessage = null },
            title = { Text("需要重新验证") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    authMessage = null
                    onAuthExpired()
                }) { Text("重新验证") }
            },
            dismissButton = {
                TextButton(onClick = { authMessage = null }) { Text("关闭") }
            },
        )
    }
    if (showExitSyncDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!exitSyncWaiting) showExitSyncDialog = false
            },
            title = { Text("尚未完成同步") },
            text = {
                Text(
                    if (exitSyncWaiting) {
                        "正在同步到 S3，请稍候…"
                    } else {
                        "当前有未同步的修改，或最近一次自动同步失败。是否等待同步完成后再退出？"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !exitSyncWaiting,
                    onClick = {
                        exitSyncWaiting = true
                        scope.launch {
                            while (!syncBridge.localSaveReady) {
                                delay(50)
                            }
                            scheduleRemoteSync(syncBridge.vaultSnapshot.syncTargets, immediate = true)
                            syncScheduler.awaitIdle()
                            exitSyncWaiting = false
                            val failed = syncBridge.vaultSnapshot.syncTargets.any {
                                it.enabled && it.confirmed && it.status == SyncStatus.FAILED
                            }
                            if (failed) {
                                syncBanner = "同步失败，请检查网络或 S3 配置后再退出"
                            } else {
                                showExitSyncDialog = false
                                onBack()
                            }
                        }
                    },
                ) { Text(if (exitSyncWaiting) "同步中…" else "等待同步") }
            },
            dismissButton = {
                TextButton(
                    enabled = !exitSyncWaiting,
                    onClick = {
                        showExitSyncDialog = false
                        onBack()
                    },
                ) { Text("直接退出") }
            },
        )
    }
    SafeScreen(
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                ActionFab(
                    icon = Icons.Filled.Settings,
                    description = "设置",
                    onClick = {
                        showAddMenu = false
                        showSettings = true
                    },
                )
                ActionFab(
                    icon = Icons.Filled.Upload,
                    description = "导出备份",
                    onClick = {
                        showAddMenu = false
                        scope.launch {
                            try {
                                val bytes = backupService.exportWithAuthorization(
                                    session = session,
                                    localEnvelopeService = envelopeService,
                                    recoveryEnvelope = user.recoveryEnvelope,
                                    userId = user.id.value,
                                    deviceGeneration = deviceGeneration,
                                    vault = vault,
                                )
                                val name = BackupNaming.fileName(
                                    user.name,
                                    Clock.System.now().toEpochMilliseconds(),
                                )
                                val savedPath = backupFiles.saveBackup(name, bytes)
                                exportMessage = "已导出到下载目录：\n$savedPath"
                            } catch (ex: Throwable) {
                                authMessage = UserFacingText.fromThrowable(
                                    ex,
                                    "导出失败：授权已失效或无法写入下载目录，请重试",
                                )
                            }
                        }
                    },
                )
                if (showAddMenu) {
                    ActionFab(
                        icon = Icons.Filled.Key,
                        description = "添加密码项",
                        onClick = {
                            pauseSyncForEditor()
                            addingEntry = true
                            showAddMenu = false
                        },
                    )
                    if (canAddFolder) {
                        ActionFab(
                            icon = Icons.Filled.CreateNewFolder,
                            description = "添加文件夹",
                            onClick = {
                                pauseSyncForEditor()
                                addingFolder = true
                                showAddMenu = false
                            },
                        )
                    }
                    ActionFab(
                        icon = Icons.Filled.Close,
                        description = "取消",
                        onClick = { showAddMenu = false },
                        small = true,
                    )
                }
                FloatingActionButton(onClick = { showAddMenu = !showAddMenu }) {
                    Icon(
                        imageVector = if (showAddMenu) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (showAddMenu) "关闭添加菜单" else "添加",
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (currentFolderId == null) {
                        requestLeaveVault()
                    } else {
                        currentFolderId = vault.folders.firstOrNull { it.id == currentFolderId }?.parentId
                        query = ""
                    }
                }) {
                    Icon(
                        imageVector = if (currentFolderId == null) {
                            Icons.AutoMirrored.Filled.Logout
                        } else {
                            Icons.AutoMirrored.Filled.ArrowBack
                        },
                        contentDescription = if (currentFolderId == null) "返回用户列表" else "返回上级",
                    )
                }
                Text(user.name, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
            Text(
                if (currentFolderId == null) {
                    "位置：根目录"
                } else {
                    "位置：根目录 / " + queries.folderPath(vault, currentFolderId)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索 key、标题、标签") },
                singleLine = true,
            )
            if (visibleItems.isEmpty()) {
                Text("密码库为空，使用右下角“添加”创建文件夹或密码项")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visibleItems, key = {
                        when (it) {
                            is BrowseItem.FolderItem -> "folder:${it.folder.id.value}"
                            is BrowseItem.EntryItem -> "entry:${it.entry.id.value}"
                        }
                    }) { item ->
                        when (item) {
                            is BrowseItem.FolderItem -> FolderCard(
                                folder = item.folder,
                                onOpen = {
                                    currentFolderId = item.folder.id
                                    query = ""
                                },
                                onEdit = {
                                    pauseSyncForEditor()
                                    editingFolder = item.folder
                                },
                            )
                            is BrowseItem.EntryItem -> PasswordEntryCard(
                                entry = item.entry,
                                folderPath = if (query.isBlank()) {
                                    null
                                } else {
                                    queries.folderPath(vault, item.entry.parentId)
                                },
                                clipboardController = clipboardController,
                                onRevealPassword = {
                                    var result: String? = null
                                    val ok = withRecoveryPassword { recoveryPassword ->
                                        result = PasswordGenerator.generate(
                                            recoveryPassword,
                                            item.entry.key,
                                            item.entry.policy,
                                        )
                                    }
                                    if (ok) result else null
                                },
                                onCopyPassword = {
                                    scope.launch {
                                        withRecoveryPassword { recoveryPassword ->
                                            clipboardController?.copySensitive(
                                                PasswordGenerator.generate(
                                                    recoveryPassword,
                                                    item.entry.key,
                                                    item.entry.policy,
                                                ),
                                            )
                                        }
                                    }
                                },
                                onEdit = {
                                    pauseSyncForEditor()
                                    editingEntry = item.entry
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionFab(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    small: Boolean = false,
) {
    if (small) {
        SmallFloatingActionButton(onClick = onClick) {
            Icon(icon, contentDescription = description)
        }
    } else {
        FloatingActionButton(onClick = onClick) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FolderCard(
    folder: Folder,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onEdit),
    ) {
        TintedSurface(colorHex = folder.colorHex, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Text(folder.name, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PasswordEntryCard(
    entry: PasswordEntry,
    folderPath: String?,
    clipboardController: SensitiveClipboardController?,
    onRevealPassword: suspend () -> String?,
    onCopyPassword: () -> Unit,
    onEdit: () -> Unit,
) {
    var visible by remember(entry.id.value) { mutableStateOf(false) }
    var revealed by remember(entry.id.value) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible, entry.id.value) {
        if (visible) {
            delay(60_000)
            visible = false
            revealed = null
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        TintedSurface(colorHex = entry.colorHex, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onEdit,
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    Text(entry.key, style = MaterialTheme.typography.labelSmall)
                    folderPath?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(if (visible && revealed != null) revealed!! else "••••••••")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        if (visible) {
                            visible = false
                            revealed = null
                        } else {
                            scope.launch {
                                val password = onRevealPassword()
                                if (password != null) {
                                    revealed = password
                                    visible = true
                                }
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "隐藏密码" else "显示密码",
                        )
                    }
                    IconButton(onClick = onCopyPassword) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制密码")
                    }
                    IconButton(onClick = {
                        clipboardController?.copySensitive(
                            PasswordGenerator.recipe(entry.key, entry.policy).encode(),
                        )
                    }) {
                        Icon(Icons.Filled.Key, contentDescription = "复制恢复配方")
                    }
                }
                entry.labels.forEach { label ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${label.name}: ${label.value}", modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            clipboardController?.copySensitive(label.value)
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制标签值")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryEditorScreen(
    entry: PasswordEntry,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (PasswordEntry) -> Unit,
    onImportRecipe: ((String) -> PasswordEntry)? = null,
) {
    var key by remember(entry.id.value) { mutableStateOf(entry.key) }
    var title by remember(entry.id.value) { mutableStateOf(entry.title) }
    var color by remember(entry.id.value) { mutableStateOf(entry.colorHex) }
    val labels = remember(entry.id.value) {
        mutableStateListOf<Label>().also { it.addAll(entry.labels) }
    }
    var policy by remember(entry.id.value) { mutableStateOf(entry.policy) }
    var error by remember(entry.id.value) { mutableStateOf<String?>(null) }
    var newLabelName by remember { mutableStateOf("") }
    var newLabelValue by remember { mutableStateOf("") }
    var useRecipe by remember { mutableStateOf(false) }
    var recipeText by remember { mutableStateOf("") }
    var recipePreview by remember { mutableStateOf<String?>(null) }

    fun commitPendingLabel() {
        val name = newLabelName.trim()
        if (name.isNotEmpty()) {
            labels += Label(name, newLabelValue)
            newLabelName = ""
            newLabelValue = ""
        }
    }

    SafeContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTopBar(
                title = if (isNew) "添加密码项" else "编辑密码项",
                onClose = onCancel,
            )
            if (isNew && onImportRecipe != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useRecipe, onCheckedChange = { useRecipe = it })
                    Text("通过恢复配方快速填写")
                }
                if (useRecipe) {
                    OutlinedTextField(
                        value = recipeText,
                        onValueChange = { recipeText = it },
                        label = { Text("恢复配方") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    Button(onClick = {
                        try {
                            val imported = onImportRecipe(recipeText)
                            key = imported.key
                            policy = imported.policy
                            if (title.isBlank()) title = imported.key
                            recipePreview = "已导入 key 与密码规则，确认后保存为密码项"
                            error = null
                        } catch (ex: Throwable) {
                            error = UserFacingText.fromThrowable(ex, "配方无效")
                            recipePreview = null
                        }
                    }) { Text("解析配方") }
                    recipePreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            OutlinedTextField(
                key,
                { key = it },
                label = { Text("key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("支持字母、数字、下划线、点和连字符") },
            )
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ColorPickerSection(selectedHex = color, onSelected = { color = it })

            Text("自定义标签", style = MaterialTheme.typography.titleSmall)
            labels.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = label.name,
                        onValueChange = { value ->
                            labels[index] = label.copy(name = value)
                        },
                        label = { Text("标签名") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = label.value,
                        onValueChange = { value ->
                            labels[index] = label.copy(value = value)
                        },
                        label = { Text("标签值") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(onClick = { labels.removeAt(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除标签")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newLabelName,
                    onValueChange = { newLabelName = it },
                    label = { Text("新标签名") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newLabelValue,
                    onValueChange = { newLabelValue = it },
                    label = { Text("新标签值") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = { commitPendingLabel() }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加标签")
                }
            }

            PasswordPolicyEditor(policy = policy, onChange = { policy = it })

            if (key != entry.key && entry.key.isNotBlank()) {
                Text(
                    "修改 key 会改变生成密码，旧恢复配方将失效",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(onClick = {
                    commitPendingLabel()
                    val next = entry.copy(
                        key = key,
                        title = title,
                        colorHex = color,
                        labels = labels.filter { it.name.isNotBlank() },
                        policy = policy,
                    )
                    error = when {
                        KeyRules.validate(key) != null -> KeyRules.validate(key)
                        title.isBlank() -> "标题不能为空"
                        !ColorRules.isValidHex(color) -> "颜色格式无效"
                        policy.validationError() != null -> policy.validationError()
                        else -> null
                    }
                    if (error == null) {
                        try {
                            onSave(next)
                        } catch (ex: IllegalArgumentException) {
                            error = UserFacingText.fromThrowable(ex, "保存失败")
                        }
                    }
                }) {
                    Text("确认")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PasswordPolicyEditor(
    policy: PasswordPolicy,
    onChange: (PasswordPolicy) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("密码规则", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = policy.length.toString(),
            onValueChange = { value ->
                value.toIntOrNull()?.let { onChange(policy.copy(length = it)) }
            },
            label = { Text("长度") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PolicyToggle("必须包含大写", policy.requireUppercase) {
            onChange(policy.copy(requireUppercase = it))
        }
        PolicyToggle("必须包含小写", policy.requireLowercase) {
            onChange(policy.copy(requireLowercase = it))
        }
        PolicyToggle("必须包含数字", policy.requireDigits) {
            onChange(policy.copy(requireDigits = it))
        }
        PolicyToggle("必须包含符号", policy.requireSymbols) {
            onChange(policy.copy(requireSymbols = it))
        }
        OutlinedTextField(
            value = policy.symbols,
            onValueChange = { onChange(policy.copy(symbols = it)) },
            label = { Text("符号集合") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = policy.excluded,
            onValueChange = { onChange(policy.copy(excluded = it)) },
            label = { Text("排除字符") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PolicyToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun FolderEditorScreen(
    folder: Folder,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (Folder) -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var color by remember(folder.id) { mutableStateOf(folder.colorHex) }
    var error by remember(folder.id) { mutableStateOf<String?>(null) }
    SafeContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTopBar(
                title = if (isNew) "添加文件夹" else "编辑文件夹",
                onClose = onCancel,
            )
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("文件夹名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ColorPickerSection(selectedHex = color, onSelected = { color = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(onClick = {
                    error = when {
                        name.isBlank() -> "文件夹名不能为空"
                        !ColorRules.isValidHex(color) -> "颜色格式无效"
                        folder.depth > 3 -> "根目录起算最多三级"
                        else -> null
                    }
                    if (error == null) {
                        try {
                            onSave(folder.copy(name = name, colorHex = color))
                        } catch (ex: IllegalArgumentException) {
                            error = UserFacingText.fromThrowable(ex, "保存失败")
                        }
                    }
                }) {
                    Text("确认")
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "关闭")
        }
    }
}

@Composable
private fun VaultSettingsScreen(
    onBack: () -> Unit,
    onOpenS3: () -> Unit,
) {
    SafeContent {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTopBar(title = "密码库设置", onClose = onBack)
            Card(
                onClick = onOpenS3,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("S3 备份同步", style = MaterialTheme.typography.titleMedium)
                        Text("配置兼容 S3 的对象存储目标", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.Settings, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun S3SettingsScreen(
    targets: List<SyncTarget>,
    vault: VaultState,
    onBack: () -> Unit,
    onChange: (List<SyncTarget>) -> Unit,
    onSyncNow: () -> Unit,
    s3Service: S3TargetService,
    sealCredentials: suspend (accessKeyId: String, secretAccessKey: String) -> SealedS3CredentialPayload,
) {
    val scope = rememberCoroutineScope()
    var preset by remember { mutableStateOf(S3ProviderPreset.ALIYUN) }
    var manualAll by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(S3ProviderPreset.ALIYUN.suggestEndpoint(S3ProviderPreset.ALIYUN.defaultRegion)) }
    var bucket by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(S3ProviderPreset.ALIYUN.defaultRegion) }
    var objectPrefix by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<String?>(null) }
    val contentMutation = vault.latestContentMutation()
    val hasReadyTarget = targets.any { it.enabled && it.confirmed }
    val syncing = targets.any { it.status == SyncStatus.SYNCING }

    fun applyPreset(next: S3ProviderPreset) {
        preset = next
        region = next.defaultRegion
        accountId = ""
        manualAll = next.requiresManualEndpoint
        endpoint = when {
            next.requiresManualEndpoint -> if (next == S3ProviderPreset.MINIO) "http://localhost:9000" else "https://"
            next.requiresAccountId -> ""
            else -> next.suggestEndpoint(next.defaultRegion)
        }
        error = null
        testSuccess = null
    }

    fun resolvedDraftEndpoint(): String {
        var value = endpoint.trim().ifBlank { preset.suggestEndpoint(region, accountId) }.trim()
        // 官网常写 s3.cstcloud.cn（无协议）；自动补 https://
        if (value.isNotEmpty() &&
            !value.startsWith("https://") &&
            !value.startsWith("http://")
        ) {
            value = "https://$value"
        }
        return value.trimEnd('/')
    }

    /**
     * 校验当前表单并返回可用于连接测试的临时目标（不含封装凭据）。
     */
    fun buildDraftTargetForTest(): SyncTarget {
        require(!(preset.requiresAccountId && accountId.isBlank() && !manualAll)) {
            "请填写 Cloudflare Account ID，或勾选手动编辑后自行填写端点"
        }
        require(accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()) {
            "请填写 Access Key 和 Secret Access Key"
        }
        val resolvedEndpoint = resolvedDraftEndpoint()
        require(
            resolvedEndpoint.startsWith("https://") ||
                resolvedEndpoint.startsWith("http://localhost") ||
                resolvedEndpoint.startsWith("http://127.0.0.1"),
        ) {
            "端点地址必须以 https:// 开头（本机调试可用 http://localhost）"
        }
        require(bucket.trim().matches(Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))) {
            "Bucket 名称无效：需为 3–63 位小写字母、数字、点或短横线"
        }
        val resolvedRegion = region.trim().ifBlank { preset.defaultRegion }
        require(resolvedRegion.isNotBlank()) { "区域不能为空" }
        return SyncTarget(
            id = EntityId("draft-test"),
            provider = preset.providerCode,
            endpoint = resolvedEndpoint.trim().trimEnd('/'),
            bucket = bucket.trim(),
            region = resolvedRegion,
            objectPrefix = normalizeObjectPrefix(objectPrefix),
            accessKeyId = accessKeyId.trim(),
            enabled = false,
            confirmed = false,
            status = SyncStatus.IDLE,
        )
    }

    suspend fun runConnectionTest(): Boolean {
        error = null
        testSuccess = null
        val draft = try {
            buildDraftTargetForTest()
        } catch (ex: Throwable) {
            error = UserFacingText.fromThrowable(ex, "请先完善配置")
            return false
        }
        val secretBytes = secretAccessKey.trim().encodeToByteArray()
        val credentials = S3Credentials(accessKeyId.trim(), secretBytes)
        return try {
            val store = KtorS3ObjectStore(
                client = platformHttpClient(),
                endpoint = draft.endpoint,
                bucket = draft.bucket,
                region = draft.region,
                credentials = credentials,
                clock = ::awsAmzDate,
                forcePathStyle = S3ProviderPreset.fromProviderCode(draft.provider).forcePathStyle,
            )
            val result = s3Service.testConnection(draft, store)
            if (result.status == SyncStatus.SUCCESS) {
                testSuccess = if (draft.objectPrefix.isNotBlank()) {
                    "连接成功：可访问存储桶目录「${draft.objectPrefix}」"
                } else {
                    "连接成功：可访问存储桶"
                }
                true
            } else {
                error = UserFacingText.fromErrorCode(result.lastErrorCode)
                    ?: UserFacingText.fromThrowable(null, "连接失败，请检查端点、桶名与密钥")
                false
            }
        } catch (ex: Throwable) {
            error = UserFacingText.fromThrowable(ex, "连接失败，请检查端点、桶名与密钥")
            false
        } finally {
            credentials.clear()
            secretBytes.fill(0)
        }
    }

    fun refreshSuggestedEndpoint() {
        if (manualAll || preset.requiresManualEndpoint) return
        endpoint = preset.suggestEndpoint(region, accountId)
    }

    fun maskAccessKey(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length <= 8) return "已配置"
        return trimmed.take(4) + "****" + trimmed.takeLast(4)
    }

    SafeContent {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorTopBar(title = "S3 同步配置", onClose = onBack)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("密码库同步状态", style = MaterialTheme.typography.titleMedium)
                    Text("版本号：${vault.contentVersion()}")
                    Text(
                        "内容最后修改：${UserFacingText.formatDateTime(contentMutation.updatedAt)}" +
                            if (contentMutation.revision > 0L) " · 修订 ${contentMutation.revision}" else "",
                    )
                    Button(
                        onClick = onSyncNow,
                        enabled = hasReadyTarget && !syncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (syncing) "同步中…" else "立即同步")
                    }
                    if (!hasReadyTarget) {
                        Text(
                            "请先添加并确认启用至少一个 S3 目标",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (targets.isEmpty()) {
                Text("尚未配置同步目标")
            } else {
                targets.forEach { target ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                UserFacingText.providerName(target.provider),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(target.endpoint, style = MaterialTheme.typography.bodySmall)
                            Text("存储桶：${target.bucket}")
                            if (target.objectPrefix.isNotBlank()) {
                                Text("存储目录：${target.objectPrefix}")
                            } else {
                                Text("存储目录：Bucket 根目录")
                            }
                            Text("区域：${target.region}")
                            Text(
                                "Access Key：" +
                                    if (target.accessKeyId.isBlank()) "未配置" else maskAccessKey(target.accessKeyId),
                            )
                            val errorText = UserFacingText.fromErrorCode(target.lastErrorCode)
                            Text(
                                buildString {
                                    append("状态：${UserFacingText.syncStatus(target.status)}")
                                    append(if (target.confirmed) " · 已确认" else " · 待确认")
                                    if (errorText != null) append(" · $errorText")
                                },
                            )
                            Text("上次同步：${UserFacingText.formatDateTime(target.lastSyncAt)}")
                            Text(
                                "同步时版本：" +
                                    (target.lastSyncRevision?.toString() ?: "—"),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!target.confirmed) {
                                    TextButton(onClick = {
                                        onChange(
                                            targets.map {
                                                if (it.id == target.id) {
                                                    it.copy(confirmed = true, enabled = true)
                                                } else {
                                                    it
                                                }
                                            },
                                        )
                                    }) { Text("确认启用") }
                                }
                                TextButton(onClick = {
                                    onChange(s3Service.remove(targets, target.id))
                                }) { Text("删除") }
                            }
                        }
                    }
                }
            }

            Text("添加目标", style = MaterialTheme.typography.titleSmall)
            Text("快速配置", style = MaterialTheme.typography.labelLarge)
            Text(
                "选择云厂商后自动填充端点与默认区域；仍可手动修改全部参数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                S3ProviderPreset.entries.forEach { item ->
                    FilterChip(
                        selected = preset == item,
                        onClick = { applyPreset(item) },
                        label = { Text(item.displayName) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = manualAll || preset.requiresManualEndpoint,
                    onCheckedChange = {
                        manualAll = it
                        if (!it) refreshSuggestedEndpoint()
                    },
                    enabled = !preset.requiresManualEndpoint,
                )
                Text("手动编辑全部参数（端点/区域等）")
            }
            if (preset.requiresAccountId) {
                OutlinedTextField(
                    accountId,
                    {
                        accountId = it
                        refreshSuggestedEndpoint()
                    },
                    label = { Text("Account ID（Cloudflare）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                region,
                {
                    region = it
                    refreshSuggestedEndpoint()
                },
                label = { Text("区域") },
                placeholder = { Text(preset.regionHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                endpoint,
                { endpoint = it },
                label = { Text("端点地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = manualAll || preset.requiresManualEndpoint || endpoint.isBlank(),
                supportingText = {
                    if (!manualAll && !preset.requiresManualEndpoint && endpoint.isNotBlank()) {
                        Text("已按厂商模板填充，可勾选上方手动编辑以修改")
                    }
                },
            )
            OutlinedTextField(
                bucket,
                { bucket = it },
                label = { Text("存储桶（Bucket）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                objectPrefix,
                { objectPrefix = it },
                label = { Text("存储目录") },
                placeholder = { Text("如 family-vault；留空表示 Bucket 根目录") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("备份写入该目录下的 vault.dat；不自动追加用户名或设备目录。相同目录 + 相同恢复密码即同一密码库。")
                },
            )
            OutlinedTextField(
                accessKeyId,
                { accessKeyId = it },
                label = { Text("Access Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SensitivePasswordField(
                value = secretAccessKey,
                onValueChange = {
                    secretAccessKey = it
                    testSuccess = null
                },
                label = "Secret Access Key",
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            testSuccess?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            testing = true
                            try {
                                runConnectionTest()
                            } finally {
                                testing = false
                            }
                        }
                    },
                    enabled = !saving && !testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            testSuccess = null
                            try {
                                if (preset.requiresAccountId && accountId.isBlank() && !manualAll) {
                                    error = "请填写 Cloudflare Account ID，或勾选手动编辑后自行填写端点"
                                    return@launch
                                }
                                if (accessKeyId.isBlank() || secretAccessKey.isBlank()) {
                                    error = "请填写 Access Key 和 Secret Access Key"
                                    return@launch
                                }
                                val resolvedEndpoint = resolvedDraftEndpoint()
                                val sealed = sealCredentials(accessKeyId.trim(), secretAccessKey.trim())
                                val target = SyncTarget(
                                    id = EntityId("s3-${Clock.System.now().toEpochMilliseconds()}"),
                                    provider = preset.providerCode,
                                    endpoint = resolvedEndpoint,
                                    bucket = bucket.trim(),
                                    region = region.trim().ifBlank { preset.defaultRegion },
                                    objectPrefix = normalizeObjectPrefix(objectPrefix),
                                    accessKeyId = sealed.accessKeyId,
                                    encryptedCredentialsHex = sealed.encryptedCredentialsHex,
                                    credentialsSaltHex = sealed.credentialsSaltHex,
                                    enabled = false,
                                    confirmed = false,
                                    status = SyncStatus.IDLE,
                                )
                                onChange(s3Service.add(targets, target))
                                bucket = ""
                                accessKeyId = ""
                                secretAccessKey = ""
                                error = null
                                testSuccess = null
                            } catch (ex: Throwable) {
                                error = UserFacingText.fromThrowable(ex, "添加失败")
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && !testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saving) "正在保存…" else "添加目标")
                }
            }
        }
    }
}
