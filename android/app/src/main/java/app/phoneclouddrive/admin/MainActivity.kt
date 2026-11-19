package app.phoneclouddrive.admin

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DriveAdminTheme { App() } }
    }
}

@Composable
fun DriveAdminTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF9DB8FF))
        else -> lightColorScheme(primary = Color(0xFF1E5BD7))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it); vm.error = null } }
    LaunchedEffect(vm.notice) { vm.notice?.let { snackbar.showSnackbar(it); vm.notice = null } }

    if (!vm.loggedIn && vm.autoSigningIn) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(Modifier.size(84.dp))
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(Modifier.widthIn(max = 160.dp))
                Spacer(Modifier.height(12.dp))
                Text("Signing in…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (!vm.loggedIn) {
        LoginScreen(vm, snackbar)
    } else {
        MainScreen(vm, snackbar)
    }
}

// ---------------- Login ----------------

/** The app icon as an in-app logo: gradient squircle with the white cloud mark. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(26))
            .background(
                Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF6D4AF0), Color(0xFF9333EA)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground), null,
            modifier = Modifier.fillMaxSize().scale(1.45f)
        )
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    var password by rememberSaveable { mutableStateOf("") }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark(Modifier.size(84.dp))
            Spacer(Modifier.height(16.dp))
            Text("Drive Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Manage your cloud drive users and storage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Column(Modifier.widthIn(max = 420.dp)) {
                OutlinedTextField(
                    value = vm.server, onValueChange = { vm.server = it },
                    label = { Text("Server address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { Text("This phone: http://127.0.0.1:8080 · Another phone on the same Wi-Fi: http://<server phone's IP>:8080") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.adminUser, onValueChange = { vm.adminUser = it },
                    label = { Text("Admin username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Admin password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Stay signed in on this phone", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Login is kept encrypted in the Android Keystore",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = vm.staySignedIn, onCheckedChange = { vm.staySignedIn = it })
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.login(password) },
                    enabled = !vm.busy && vm.adminUser.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text(if (vm.busy) "Signing in…" else "Sign in") }
                if (vm.hasSavedLogin && !vm.busy) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.autoLogin() },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Retry saved login") }
                }
            }
        }
    }
}

// ---------------- Main ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var browsing by rememberSaveable { mutableStateOf(false) }

    val selectedUser = selected?.let { name -> vm.users.firstOrNull { it.optString("username") == name } }
    if (selected != null && selectedUser == null && !vm.busy) selected = null

    if (selectedUser != null && browsing) {
        FilesScreen(
            vm, selectedUser.optString("username"), selectedUser.optString("home_dir"), snackbar,
            onClose = { browsing = false }
        )
        return
    }
    if (selectedUser != null) {
        BackHandler { selected = null }
        UserDetailScreen(vm, selectedUser, snackbar, onBack = { selected = null }, onBrowse = { browsing = true })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drive Admin") },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !vm.busy) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = { vm.signOut() }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Person, null) }, label = { Text("Users") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Active (${vm.connections.size})") }
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Add user") }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            when (tab) {
                0 -> UsersTab(vm) { selected = it }
                else -> ConnectionsTab(vm)
            }
        }
    }

    if (showAdd) AddUserDialog(vm, onDismiss = { showAdd = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsersTab(vm: AppViewModel, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OverviewCard(vm) }
        item {
            Text(
                "Users (${vm.users.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )
        }
        if (vm.users.isEmpty() && !vm.busy) {
            item { Text("No users yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp)) }
        }
        items(vm.users, key = { it.optString("username") }) { u -> UserCard(u) { onOpen(u.optString("username")) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewCard(vm: AppViewModel) {
    val totalUsed = vm.users.sumOf { it.optLong("used_quota_size") }
    val active = vm.users.count { it.optInt("status") == 1 }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Cloud drive", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Used by users", formatBytes(totalUsed))
                Stat("Users", "$active active / ${vm.users.size}")
                Stat("Online now", "${vm.connections.size}")
            }
            if (vm.isLocalServer) {
                val (free, total) = remember(vm.users) { vm.phoneStorage() }
                val used = total - free
                Spacer(Modifier.height(14.dp))
                Text(
                    "Phone storage: ${formatBytes(free)} free of ${formatBytes(total)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(6.dp))
                UsageBar(
                    fraction = used.toFloat() / total,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    height = 8.dp
                )
            }
            vm.status?.let { s ->
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceChip("Web", true)
                    ServiceChip("WebDAV", s.optJSONObject("webdav")?.optBoolean("is_active") == true)
                    ServiceChip("SFTP", s.optJSONObject("ssh")?.optBoolean("is_active") == true)
                    ServiceChip("Database", s.optJSONObject("data_provider")?.optBoolean("is_active") == true)
                }
            }
        }
    }
}

/** Simple, readable usage bar: rounded track with a solid filled portion (no gap or end dot). */
@Composable
fun UsageBar(fraction: Float, color: Color, track: Color, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
    }
}

@Composable
fun ServiceChip(name: String, ok: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (ok) Color(0xFF1B873F).copy(alpha = 0.16f) else MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            (if (ok) "● " else "○ ") + name,
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) Color(0xFF1B873F) else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusBadge(enabled: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            if (enabled) "Active" else "Disabled",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun UsageBlock(u: JSONObject) {
    val used = u.optLong("used_quota_size")
    val quota = u.optLong("quota_size")
    val count = u.optInt("used_quota_files")
    val files = if (count == 1) "1 file" else "$count files"
    if (quota > 0) {
        val frac = (used.toFloat() / quota).coerceIn(0f, 1f)
        Text("${formatBytes(used)} of ${formatBytes(quota)} · $files", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        UsageBar(
            fraction = frac,
            color = if (frac > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            height = 6.dp
        )
    } else {
        Text("${formatBytes(used)} used · no limit · $files", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun UserCard(u: JSONObject, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    u.optString("username"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                StatusBadge(u.optInt("status") == 1)
            }
            Spacer(Modifier.height(8.dp))
            UsageBlock(u)
            Spacer(Modifier.height(6.dp))
            Text(
                "${Access.of(u)?.label ?: "Custom access"} · last login ${formatTime(u.optLong("last_login"))}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------- Connections ----------------

@Composable
fun ConnectionsTab(vm: AppViewModel) {
    if (vm.connections.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Nobody is connected right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(vm.connections, key = { it.optString("connection_id") }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.optString("username"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${c.optString("protocol")} · ${c.optString("remote_address")}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "connected ${formatTime(c.optLong("connection_time"))} · active ${formatTime(c.optLong("last_activity"))}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { vm.disconnect(c.optString("connection_id")) }, enabled = !vm.busy) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}

// ---------------- Shared form pieces ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuotaPicker(quotaText: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = quotaText,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
        label = { Text("Storage limit (GB)") },
        placeholder = { Text("Empty = no limit") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("1", "5", "10", "25", "50").forEach { gb ->
            FilterChip(selected = quotaText == gb, onClick = { onChange(gb) }, label = { Text("$gb GB") })
        }
        FilterChip(selected = quotaText.isBlank(), onClick = { onChange("") }, label = { Text("No limit") })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessPicker(access: Access?, onChange: (Access) -> Unit) {
    Text("Access", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Access.entries.forEach { a ->
            FilterChip(selected = access == a, onClick = { onChange(a) }, label = { Text(a.label) })
        }
    }
    if (access == null) {
        Text(
            "Custom permissions set in the web admin. Pick one above to replace them.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------- Add user ----------------

@Composable
fun AddUserDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var quota by rememberSaveable { mutableStateOf("5") }
    var access by rememberSaveable { mutableStateOf(Access.FULL) }
    val nameOk = name.isNotBlank() && name.trim().all { it.isLetterOrDigit() || it in "._-@" }
    val passOk = pass.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New drive user") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Username") }, singleLine = true,
                    isError = name.isNotEmpty() && !nameOk,
                    supportingText = { Text("Letters, numbers, . _ - @") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = pass.isNotEmpty() && !passOk,
                    supportingText = { Text("At least 8 characters") },
                    modifier = Modifier.fillMaxWidth()
                )
                QuotaPicker(quota) { quota = it }
                AccessPicker(access) { access = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.createUser(name, pass, quota.toDoubleOrNull() ?: 0.0, access, onDismiss) },
                enabled = nameOk && passOk && !vm.busy
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------- User detail ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(vm: AppViewModel, u: JSONObject, snackbar: SnackbarHostState, onBack: () -> Unit, onBrowse: () -> Unit) {
    val name = u.optString("username")
    var enabled by rememberSaveable(name) { mutableStateOf(u.optInt("status") == 1) }
    var quota by rememberSaveable(name) { mutableStateOf(formatGbInput(bytesToGb(u.optLong("quota_size")))) }
    var access by rememberSaveable(name) { mutableStateOf(Access.of(u)) }
    var newPass by rememberSaveable(name) { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.rescan(name) }, enabled = !vm.busy) { Icon(Icons.Filled.Refresh, "Recalculate usage") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Storage used", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    UsageBlock(u)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Last login ${formatTime(u.optLong("last_login"))} · created ${formatTime(u.optLong("created_at"))}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Folder: ${u.optString("home_dir").replace("/storage/emulated/0", "Internal storage").replace("/sdcard", "Internal storage")} (private)",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onBrowse, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.ic_kind_folder), null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Browse files")
                        }
                        OutlinedButton(onClick = { vm.rescan(name) }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Recalculate")
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Account enabled", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "User can log in" else "User is blocked from logging in",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            HorizontalDivider()
            QuotaPicker(quota) { quota = it }
            HorizontalDivider()
            AccessPicker(access) { access = it }
            HorizontalDivider()
            OutlinedTextField(
                value = newPass, onValueChange = { newPass = it },
                label = { Text("New password (optional)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = newPass.isNotEmpty() && newPass.length < 8,
                supportingText = { Text("Leave empty to keep the current password") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    vm.saveUser(name, enabled, quota.toDoubleOrNull() ?: 0.0, access, newPass) { newPass = "" }
                },
                enabled = !vm.busy && (newPass.isEmpty() || newPass.length >= 8),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Save changes") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = !vm.busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, null)
                Spacer(Modifier.size(8.dp))
                Text("Delete user")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $name?") },
            text = {
                Text("$name will no longer be able to log in. Their files stay on the phone in CloudDrive/$name, and you can delete them from the Files app.")
            },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; vm.deleteUser(name, onBack) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
