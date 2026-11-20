package app.phoneclouddrive.admin

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.phoneclouddrive.admin.server.ServerController
import app.phoneclouddrive.admin.server.ServerMode
import app.phoneclouddrive.admin.server.ServerService
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun SettingsTab(vm: AppViewModel, onCloudflare: () -> Unit, onOpenLogs: (String) -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var pickStorage by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.builtIn) {
            item {
                SectionTitle("Server")
                GroupCard {
                    SwitchRow(Icons.Filled.PowerSettingsNew, "Start after reboot", "Brings the drive back after the phone restarts", vm.startOnBoot, vm::changeStartOnBoot)
                    SwitchRow(
                        Icons.Filled.Wifi, "Home Wi-Fi access",
                        if (vm.lanAccess) "Also reachable on your local network (http, no Cloudflare)" else "Off: users can only connect through Cloudflare",
                        vm.lanAccess, vm::changeLanAccess
                    )
                    ItemRow(Icons.Filled.SdCard, "Storage location", prettyPath(vm.storageBase), onClick = { pickStorage = true })
                }
            }
            item {
                SectionTitle("Cloudflare")
                GroupCard {
                    if (vm.hasTunnel) {
                        ItemRow(Icons.Filled.Language, "Web drive", vm.driveHost.ifBlank { "Address not set" }, onClick = onCloudflare)
                        if (vm.davHost.isNotBlank()) ItemRow(Icons.Filled.Dns, "WebDAV", vm.davHost, onClick = onCloudflare)
                        ItemRow(Icons.Filled.Refresh, "Reconnect tunnel", null, onClick = { ServerController.restartTunnel(ctx) })
                        ItemRow(Icons.Filled.LinkOff, "Remove connection", "Stops publishing the drive on the internet", onClick = { confirm = Confirm.REMOVE_TUNNEL })
                    } else {
                        ItemRow(Icons.Filled.Cloud, "Set up Cloudflare", "Give the drive a secure public address", onClick = onCloudflare)
                    }
                }
            }
            item {
                SectionTitle("Security")
                GroupCard {
                    ItemRow(Icons.Filled.LockReset, "Sign out everywhere", "Ends every web drive session on all devices", onClick = { confirm = Confirm.SIGN_OUT_ALL })
                    ItemRow(Icons.Filled.Info, "Admin access", "Only this app can manage the drive. The admin panel and API are never reachable from the network.")
                }
            }
            item { PhoneSettingsSection() }
            item {
                SectionTitle("Logs")
                GroupCard {
                    ItemRow(Icons.AutoMirrored.Filled.Article, "Drive server log", null, onClick = { onOpenLogs(ServerService.SFTPGO) })
                    ItemRow(Icons.AutoMirrored.Filled.Article, "Cloudflare tunnel log", null, onClick = { onOpenLogs(ServerService.CLOUDFLARED) })
                }
            }
        } else {
            item {
                SectionTitle("Server")
                GroupCard {
                    ItemRow(Icons.Filled.Dns, "Connected to", vm.server)
                    ItemRow(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", "Also forgets the saved login", onClick = { vm.signOut() })
                }
            }
        }
        item {
            SectionTitle("Mode")
            GroupCard {
                ItemRow(
                    Icons.Filled.SwapHoriz,
                    if (vm.builtIn) "Use an external server instead" else "Run the drive on this phone instead",
                    if (vm.builtIn) "Manage a Termux-based server; the built-in server stops" else "Built-in server, no Termux needed",
                    onClick = { confirm = Confirm.SWITCH_MODE }
                )
            }
        }
        item {
            SectionTitle("About")
            GroupCard {
                val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
                val sftpgo = remember { File(ctx.filesDir, "sftpgo-ui/VERSION").takeIf { it.exists() }?.readText()?.substringBefore('-') }
                ItemRow(Icons.Filled.Info, "Drive Admin $version", if (vm.builtIn) "Built-in server: SFTPGo ${sftpgo ?: ""} and cloudflared".replace("  ", " ") else "External server mode")
            }
        }
    }

    if (pickStorage) StorageDialog(vm) { pickStorage = false }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.body) },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = null
                        when (c) {
                            Confirm.REMOVE_TUNNEL -> vm.removeTunnel()
                            Confirm.SIGN_OUT_ALL -> vm.signOutEverywhere()
                            Confirm.SWITCH_MODE -> vm.chooseMode(if (vm.builtIn) ServerMode.EXTERNAL else ServerMode.BUILT_IN)
                        }
                    },
                    colors = if (c.destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) { Text(c.action) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

private enum class Confirm(val title: String, val body: String, val action: String, val destructive: Boolean) {
    REMOVE_TUNNEL(
        "Remove the Cloudflare connection?",
        "The drive will no longer be reachable on the internet. The tunnel and DNS records stay in your Cloudflare account; you can delete them there.",
        "Remove", true
    ),
    SIGN_OUT_ALL(
        "Sign out everywhere?",
        "Everyone signed in to the web drive is signed out on every device and has to sign in again. Files and accounts are not affected.",
        "Sign out all", true
    ),
    SWITCH_MODE(
        "Switch server mode?",
        "You can switch back anytime. Users and files of each server stay where they are.",
        "Switch", false
    ),
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ItemRow(icon, title, subtitle, onClick = { onChange(!checked) }) { Switch(checked = checked, onCheckedChange = onChange) }
}

/** Live status of the phone permissions the server relies on, each with a shortcut to fix it. */
@Composable
private fun PhoneSettingsSection() {
    val ctx = LocalContext.current
    val resumed = rememberResumeCounter()
    val files = remember(resumed) { PhoneSetup.hasFilesAccess(ctx) }
    val battery = remember(resumed) { PhoneSetup.batteryUnrestricted(ctx) }
    val notifications = remember(resumed) { PhoneSetup.notificationsAllowed(ctx) }
    val requestFiles = rememberFilesAccessRequester()
    val requestNotifications = rememberNotificationRequester()
    SectionTitle("Phone")
    GroupCard {
        PermissionRow(Icons.Filled.FolderOpen, "All files access", "Stores the drive's files", files, requestFiles)
        PermissionRow(Icons.Filled.BatteryChargingFull, "No battery limits", "Keeps the server running with the screen off", battery) {
            ctx.startActivity(PhoneSetup.batteryIntent(ctx))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(Icons.Filled.Notifications, "Notifications", "Shows the server status and a Stop button", notifications, requestNotifications)
        }
        ItemRow(
            Icons.Filled.Smartphone, "Manufacturer battery settings",
            "Some phones (Samsung, Xiaomi…) stop background apps on their own. Set Drive Admin to \"Unrestricted\" / \"Never sleeping\".",
            onClick = { ctx.startActivity(PhoneSetup.appSettingsIntent(ctx)) }
        )
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, subtitle: String, granted: Boolean, fix: () -> Unit) {
    ItemRow(icon, title, subtitle, onClick = if (granted) null else fix) {
        if (granted) Icon(Icons.Filled.CheckCircle, "Allowed", tint = StatusColors.ok)
        else TextButton(onClick = fix) { Text("Allow") }
    }
}

@Composable
private fun StorageDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val volumes = remember { PhoneSetup.volumes(ctx) }
    var selected by remember { mutableStateOf(vm.storageBase) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage location") },
        text = {
            Column {
                Text(
                    "Where new users' folders are created. Existing users keep their current folder.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                volumes.forEach { v ->
                    ItemRow(
                        if (v.removable) Icons.Filled.SdCard else Icons.Filled.Smartphone,
                        v.label, "${formatBytes(v.free)} free · ${prettyPath(v.drivePath)}",
                        onClick = { selected = v.drivePath }
                    ) { RadioButton(selected = selected == v.drivePath, onClick = { selected = v.drivePath }) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (selected != vm.storageBase) vm.setStorage(selected); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Live tail of one server process's output. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(name: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    var lines by remember { mutableStateOf(ServerController.log(name)) }
    val list = rememberLazyListState()
    LaunchedEffect(name) {
        while (true) {
            lines = ServerController.log(name)
            delay(2000)
        }
    }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    val title = if (name == ServerService.CLOUDFLARED) "Cloudflare tunnel log" else "Drive server log"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { copyText(ctx, title, lines.joinToString("\n")) }, enabled = lines.isNotEmpty()) {
                        Icon(Icons.Filled.ContentCopy, "Copy log")
                    }
                }
            )
        }
    ) { pad ->
        if (lines.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp)) {
                Text(
                    if (vmActive()) "Nothing logged yet." else "The server isn't running. Logs from earlier runs are in the app's files/logs folder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.padding(pad).fillMaxSize().horizontalScroll(rememberScrollState()),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(lines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), softWrap = false)
                }
            }
        }
    }
}

private fun vmActive() = ServerController.state.value.active
