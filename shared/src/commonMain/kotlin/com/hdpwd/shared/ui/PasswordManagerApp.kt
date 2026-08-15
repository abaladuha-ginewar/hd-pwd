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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.BrowseItem
import com.hdpwd.shared.domain.ColorRules
import com.hdpwd.shared.domain.Folder
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.VaultEditor
import com.hdpwd.shared.domain.VaultQueries
import com.hdpwd.shared.domain.VaultState
import com.hdpwd.shared.crypto.platformCryptoProvider
import com.hdpwd.shared.crypto.PasswordGenerator
import com.hdpwd.shared.application.PasswordRecoveryService
import com.hdpwd.shared.security.ClipboardPort
import com.hdpwd.shared.security.SensitiveClipboardController
import com.hdpwd.shared.security.AuthorizationSession
import com.hdpwd.shared.security.LocalEnvelopeRecord
import com.hdpwd.shared.security.LocalEnvelopeService
import com.hdpwd.shared.storage.DefaultKdfParameters
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * 跨平台密码管理器根 UI。
 *
 * 当前阶段提供用户列表、创建用户和空密码库骨架；密码值不在 UI 状态中缓存。
 */
@Composable
fun PasswordManagerApp(clipboard: ClipboardPort? = null) {
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

        when (screen) {
            AppScreen.USERS -> UserListScreen(
                users = users,
                onCreate = { screen = AppScreen.CREATE_USER },
                onRecover = { screen = AppScreen.RECOVERY },
                onSelect = {
                    selectedUser = it
                    screen = AppScreen.UNLOCK
                },
            )
            AppScreen.CREATE_USER -> CreateUserScreen(
                onCancel = { screen = AppScreen.USERS },
                envelopeService = envelopeService,
                onCreated = { summary ->
                    users += summary
                    selectedUser = summary
                    screen = AppScreen.VAULT
                },
            )
            AppScreen.RECOVERY -> RecoveryScreen(
                onCancel = { screen = AppScreen.USERS },
            )
            AppScreen.UNLOCK -> selectedUser?.let { user ->
                UnlockUserScreen(
                    user = user,
                    envelopeService = envelopeService,
                    session = session,
                    onCancel = {
                        selectedUser = null
                        screen = AppScreen.USERS
                    },
                    onUnlocked = { screen = AppScreen.VAULT },
                )
            } ?: run { screen = AppScreen.USERS }
            AppScreen.VAULT -> selectedUser?.let { user ->
                VaultScreen(
                    user = user,
                    envelopeService = envelopeService,
                    session = session,
                    clipboardController = clipboardController,
                    onBack = {
                        session.clear()
                        selectedUser = null
                        screen = AppScreen.USERS
                    },
                )
            } ?: run { screen = AppScreen.USERS }
        }
    }
}

/**
 * 根 UI 页面状态。
 */
private enum class AppScreen {
    USERS,
    CREATE_USER,
    UNLOCK,
    RECOVERY,
    VAULT,
}

/**
 * 解锁前可见的最小用户信息。
 */
private data class UserSummary(
    val id: EntityId,
    val name: String,
    val vault: VaultState,
    val localEnvelope: LocalEnvelopeRecord,
)

/**
 * 本地用户列表页面。
 */
@Composable
private fun UserListScreen(
    users: List<UserSummary>,
    onCreate: () -> Unit,
    onRecover: () -> Unit,
    onSelect: (UserSummary) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Text("+")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("HD Password", style = MaterialTheme.typography.headlineMedium)
            Text("选择用户以验证并进入密码库")
            if (users.isEmpty()) {
                Text("当前设备还没有用户")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users, key = { it.id.value }) { user ->
                        Card(onClick = { onSelect(user) }, modifier = Modifier.fillMaxWidth()) {
                            Text(user.name, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("创建用户")
            }
            TextButton(onClick = onRecover, modifier = Modifier.fillMaxWidth()) {
                Text("无密码库恢复子密码")
            }
        }
    }
}

/**
 * 无密码库恢复页面，只接收恢复密码和恢复配方。
 */
@Composable
private fun RecoveryScreen(
    onCancel: () -> Unit,
) {
    var recoveryPassword by remember { mutableStateOf("") }
    var recipe by remember { mutableStateOf("") }
    var generated by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val recoveryService = remember { PasswordRecoveryService() }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("无密码库恢复", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = recoveryPassword,
            onValueChange = { recoveryPassword = it },
            label = { Text("恢复密码") },
            singleLine = true,
        )
        OutlinedTextField(
            value = recipe,
            onValueChange = { recipe = it },
            label = { Text("恢复配方") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        generated?.let { Text("生成结果：$it") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = {
                scope.launch {
                    try {
                        generated = recoveryService.recover(recoveryPassword, recipe)
                        recoveryPassword = ""
                        error = null
                        kotlinx.coroutines.delay(60_000)
                        generated = null
                    } catch (_: Throwable) {
                        generated = null
                        recoveryPassword = ""
                        error = "恢复密码或恢复配方无效"
                    }
                }
            }) {
                Text("恢复")
            }
        }
    }
}

/**
 * 创建用户页面，恢复密码不写入长期 UI 状态。
 */
@Composable
private fun CreateUserScreen(
    onCancel: () -> Unit,
    envelopeService: LocalEnvelopeService,
    onCreated: (UserSummary) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var recoveryPassword by remember { mutableStateOf("") }
    var localPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("创建用户", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it }, label = { Text("用户名") }, singleLine = true)
        OutlinedTextField(
            recoveryPassword,
            { recoveryPassword = it },
            label = { Text("恢复密码") },
            singleLine = true,
        )
        OutlinedTextField(
            localPassword,
            { localPassword = it },
            label = { Text("本机主密码") },
            singleLine = true,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = {
                error = when {
                    name.isBlank() -> "用户名不能为空"
                    recoveryPassword.isBlank() -> "恢复密码不能为空"
                    localPassword.isBlank() -> "本机主密码不能为空"
                    else -> null
                }
                if (error == null) {
                    scope.launch {
                        val id = EntityId(
                            "local-" + platformCryptoProvider().randomBytes(16)
                                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') },
                        )
                        val envelope = envelopeService.create(recoveryPassword, localPassword)
                        onCreated(UserSummary(id, name, VaultState(id), envelope))
                        recoveryPassword = ""
                        localPassword = ""
                    }
                }
            }) {
                Text("创建")
            }
        }
    }
}

/**
 * 本机主密码验证页面；生物识别失败时保持同一回退入口。
 */
@Composable
private fun UnlockUserScreen(
    user: UserSummary,
    envelopeService: LocalEnvelopeService,
    session: AuthorizationSession,
    onCancel: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var localPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("验证 ${user.name}", style = MaterialTheme.typography.headlineMedium)
        Text("可使用生物识别验证；不可用时输入本机主密码")
        OutlinedTextField(
            value = localPassword,
            onValueChange = { localPassword = it },
            label = { Text("本机主密码") },
            singleLine = true,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = {
                scope.launch {
                    try {
                        val key = envelopeService.unlockLocalKey(user.localEnvelope, localPassword)
                        session.open(key)
                        localPassword = ""
                        error = null
                        onUnlocked()
                    } catch (_: Throwable) {
                        localPassword = ""
                        error = "验证失败，请重试"
                    }
                }
            }) {
                Text("验证")
            }
        }
    }
}

/**
 * 当前用户的密码库骨架页面。
 */
@Composable
private fun VaultScreen(
    user: UserSummary,
    envelopeService: LocalEnvelopeService,
    session: AuthorizationSession,
    clipboardController: SensitiveClipboardController?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val queries = remember { VaultQueries() }
    val editor = remember { VaultEditor() }
    var vault by remember { mutableStateOf(user.vault) }
    var query by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    var addingFolder by remember { mutableStateOf(false) }
    var currentFolderId by remember { mutableStateOf<EntityId?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    if (editingEntry != null) {
        EntryEditorScreen(
            entry = editingEntry!!,
            onCancel = { editingEntry = null },
            onSave = { updated ->
                vault = editor.updateEntry(vault, updated)
                editingEntry = null
            },
        )
        return
    }
    if (editingFolder != null) {
        FolderEditorScreen(
            folder = editingFolder!!,
            onCancel = { editingFolder = null },
            onSave = { updated ->
                vault = editor.updateFolder(vault, updated)
                editingFolder = null
            },
        )
        return
    }
    if (addingFolder) {
        FolderEditorScreen(
            folder = Folder(
                id = EntityId("new-folder-${vault.folders.size}"),
                parentId = currentFolderId,
                name = "",
                colorHex = "#94A3B8",
                depth = (vault.folders.firstOrNull { it.id == currentFolderId }?.depth ?: 1) + 1,
            ),
            onCancel = { addingFolder = false },
            onSave = { created ->
                vault = editor.addFolder(
                    vault,
                    created.id,
                    created.parentId,
                    created.name,
                    created.colorHex,
                )
                addingFolder = false
            },
        )
        return
    }
    if (addingEntry) {
        EntryEditorScreen(
            entry = PasswordEntry(
                id = EntityId("new-entry-${vault.entries.size}"),
                parentId = currentFolderId,
                key = "",
                title = "",
            ),
            onCancel = { addingEntry = false },
            onSave = { created ->
                vault = editor.addEntry(vault, created)
                addingEntry = false
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
                        onBack()
                    } else {
                        vault = editor.deleteFolder(
                            vault,
                            currentFolderId!!,
                            Clock.System.now().toEpochMilliseconds(),
                            EntityId("delete-${vault.deviceSequence + 1}"),
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
    Scaffold(
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(onClick = {}) { Text("设") }
                FloatingActionButton(onClick = {}) { Text("导") }
                if (showAddMenu) {
                    FloatingActionButton(onClick = { addingEntry = true; showAddMenu = false }) {
                        Text("密")
                    }
                    FloatingActionButton(onClick = { addingFolder = true; showAddMenu = false }) {
                        Text("夹")
                    }
                }
                FloatingActionButton(onClick = { showAddMenu = !showAddMenu }) { Text("+") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    if (currentFolderId == null) {
                        onBack()
                    } else {
                        currentFolderId = vault.folders.firstOrNull { it.id == currentFolderId }?.parentId
                        query = ""
                    }
                }) { Text("返回") }
                Text(user.name, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { showDeleteConfirmation = true }) { Text("删除") }
            }
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
                                onEdit = { editingFolder = item.folder },
                            )
                            is BrowseItem.EntryItem -> PasswordEntryCard(
                                entry = item.entry,
                                folderPath = if (query.isBlank()) {
                                    null
                                } else {
                                    queries.folderPath(vault, item.entry.parentId)
                                },
                                clipboardController = clipboardController,
                                onCopyPassword = {
                                    scope.launch {
                                        val permit = session.acquire(
                                            com.hdpwd.shared.security.OperationPurpose.GENERATE_PASSWORD,
                                        ) ?: return@launch
                                        try {
                                            session.withEnvelopeKeySuspending(permit) { envelopeKey ->
                                                envelopeService.withRecoveryPassword(
                                                    user.localEnvelope,
                                                    envelopeKey,
                                                ) { recoveryPassword ->
                                                    clipboardController?.copySensitive(
                                                        PasswordGenerator.generate(
                                                            recoveryPassword,
                                                            item.entry.key,
                                                            item.entry.policy,
                                                        ),
                                                    )
                                                }
                                            }
                                        } finally {
                                            permit.close()
                                        }
                                    }
                                },
                                onEdit = { editingEntry = item.entry },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 文件夹卡片，点击进入、长按编辑。
 */
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
        Text("📁 ${folder.name}", modifier = Modifier.padding(16.dp))
    }
}

/**
 * 默认掩码显示密码项的外部卡片。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PasswordEntryCard(
    entry: PasswordEntry,
    folderPath: String?,
    clipboardController: SensitiveClipboardController?,
    onCopyPassword: () -> Unit,
    onEdit: () -> Unit,
) {
    var visible by remember(entry.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onEdit,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            Text(entry.key, style = MaterialTheme.typography.labelSmall)
            folderPath?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            Text(if (visible) "********" else "••••••••")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "隐藏" else "显示")
                }
                TextButton(onClick = onCopyPassword) { Text("复制密码") }
                TextButton(onClick = {
                    clipboardController?.copySensitive(PasswordGenerator.recipe(entry.key, entry.policy).encode())
                }) { Text("复制配方") }
            }
            entry.labels.forEach { label ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${label.name}: ${label.value}")
                    TextButton(onClick = {
                        clipboardController?.copySensitive(label.value)
                    }) { Text("复制") }
                }
            }
        }
    }
}

/**
 * 密码项编辑页面，保存前校验 key、规则和颜色。
 */
@Composable
private fun EntryEditorScreen(
    entry: PasswordEntry,
    onCancel: () -> Unit,
    onSave: (PasswordEntry) -> Unit,
) {
    var key by remember(entry.id) { mutableStateOf(entry.key) }
    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var color by remember(entry.id) { mutableStateOf(entry.colorHex) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("编辑密码项", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(key, { key = it }, label = { Text("key") }, singleLine = true)
        OutlinedTextField(title, { title = it }, label = { Text("标题") }, singleLine = true)
        OutlinedTextField(color, { color = it }, label = { Text("背景颜色 HEX") }, singleLine = true)
        if (key != entry.key) {
            Text(
                "修改 key 会改变生成密码，旧恢复配方将失效",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorRules.fixedColors.forEach { fixed ->
                TextButton(onClick = { color = fixed }) { Text("●") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = {
                error = when {
                    key.matches(Regex("[A-Za-z_.-]{1,128}")).not() -> "key 格式无效"
                    title.isBlank() -> "标题不能为空"
                    !ColorRules.isValidHex(color) -> "颜色格式无效"
                    else -> null
                }
                if (error == null) onSave(entry.copy(key = key, title = title, colorHex = color))
            }) {
                Text("确认")
            }
        }
    }
}

/**
 * 文件夹添加/编辑页面。
 */
@Composable
private fun FolderEditorScreen(
    folder: Folder,
    onCancel: () -> Unit,
    onSave: (Folder) -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var color by remember(folder.id) { mutableStateOf(folder.colorHex) }
    var error by remember(folder.id) { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("添加文件夹", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it }, label = { Text("文件夹名") }, singleLine = true)
        OutlinedTextField(color, { color = it }, label = { Text("背景颜色 HEX") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorRules.fixedColors.forEach { fixed ->
                TextButton(onClick = { color = fixed }) { Text("●") }
            }
        }
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
                if (error == null) onSave(folder.copy(name = name, colorHex = color))
            }) {
                Text("确认")
            }
        }
    }
}
