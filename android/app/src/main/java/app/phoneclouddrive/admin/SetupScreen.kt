package app.phoneclouddrive.admin

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.phoneclouddrive.admin.server.ProcState
import app.phoneclouddrive.admin.server.ServerMode
import app.phoneclouddrive.admin.server.ServerService

private const val WELCOME = 0
private const val PHONE = 1
private const val CLOUDFLARE = 2
private const val START = 3

/** First run: pick a mode, grant what the server needs, connect Cloudflare, start. */
@Composable
fun SetupScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    var step by rememberSaveable { mutableIntStateOf(WELCOME) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    if (showLog) {
        LogsScreen(ServerService.SFTPGO, onClose = { showLog = false })
        return
    }
    if (step > WELCOME) BackHandler { step-- }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                if (step > WELCOME) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { step-- }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Text("Step $step of 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(progress = { step / 3f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
                when (step) {
                    WELCOME -> Welcome(
                        onBuiltIn = { vm.chooseMode(ServerMode.BUILT_IN); step = PHONE },
                        onExternal = { vm.chooseMode(ServerMode.EXTERNAL); vm.finishSetup() }
                    )
                    PHONE -> PhoneStep { step = CLOUDFLARE }
                    CLOUDFLARE -> CloudflareStep(vm) { step = START }
                    else -> StartStep(vm, onShowLog = { showLog = true })
                }
            }
        }
    }
}

@Composable
private fun Welcome(onBuiltIn: () -> Unit, onExternal: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(32.dp))
        BrandMark(Modifier.size(88.dp))
        Spacer(Modifier.height(20.dp))
        Text("Your own cloud drive", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Turn this phone into a private drive you can reach from anywhere: web drive, network drive (WebDAV) and share links.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        ChoiceCard(
            Icons.Filled.PhoneAndroid, "Run the drive on this phone", "Recommended. Everything runs inside this app, nothing else to install.",
            primary = true, onClick = onBuiltIn
        )
        Spacer(Modifier.height(12.dp))
        ChoiceCard(
            Icons.Filled.Dns, "Manage an existing server", "Connect to an SFTPGo server you already run, e.g. the Termux setup.",
            primary = false, onClick = onExternal
        )
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, body: String, primary: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp), tint = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Column {
                val onPrimary = MaterialTheme.colorScheme.onPrimaryContainer
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (primary) onPrimary else MaterialTheme.colorScheme.onSurface)
                Text(
                    body, style = MaterialTheme.typography.bodyMedium,
                    color = if (primary) onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun PhoneStep(onNext: () -> Unit) {
    val ctx = LocalContext.current
    val resumed = rememberResumeCounter()
    val files = remember(resumed) { PhoneSetup.hasFilesAccess(ctx) }
    val battery = remember(resumed) { PhoneSetup.batteryUnrestricted(ctx) }
    val notifications = remember(resumed) { PhoneSetup.notificationsAllowed(ctx) }
    val requestFiles = rememberFilesAccessRequester()
    val requestNotifications = rememberNotificationRequester()

    StepHeader("Prepare the phone", "The drive needs a few permissions to store files and stay online with the screen off.")
    GroupCard {
        GrantRow(Icons.Filled.FolderOpen, "All files access", "Required. Files are kept in a hidden folder that Gallery doesn't scan.", files, requestFiles)
        GrantRow(Icons.Filled.BatteryChargingFull, "No battery limits", "Recommended. Stops Android from pausing the server.", battery) {
            ctx.startActivity(PhoneSetup.batteryIntent(ctx))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            GrantRow(Icons.Filled.Notifications, "Notifications", "Recommended. Shows the server status with a Stop button.", notifications, requestNotifications)
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onNext, enabled = files, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Continue") }
    if (!files) {
        Text(
            "Allow file access to continue.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun GrantRow(icon: ImageVector, title: String, subtitle: String, granted: Boolean, fix: () -> Unit) {
    ItemRow(icon, title, subtitle, onClick = if (granted) null else fix) {
        if (granted) Icon(Icons.Filled.CheckCircle, "Allowed", tint = StatusColors.ok)
        else TextButton(onClick = fix) { Text("Allow", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun CloudflareStep(vm: AppViewModel, onNext: () -> Unit) {
    StepHeader("Connect Cloudflare", "Optional, but it's how you reach the drive away from this phone.")
    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
    if (vm.hasTunnel) {
        ItemRow(Icons.Filled.CheckCircle, "Connected", vm.driveHost.ifBlank { "Tunnel saved" }, iconTint = StatusColors.ok)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Continue") }
    } else {
        CloudflareSetup(vm, onDone = onNext)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
    }
}

@Composable
private fun StartStep(vm: AppViewModel, onShowLog: () -> Unit) {
    LaunchedEffect(Unit) { if (!vm.serverState.active) vm.startServer() }
    val s = vm.serverState
    val sftpgo = s.process(ServerService.SFTPGO)
    val problem = s.fatal ?: sftpgo?.message?.takeIf { sftpgo.state == ProcState.WAITING || sftpgo.state == ProcState.BACKOFF }

    StepHeader("Start the drive", "The server runs in the background and comes back by itself after a reboot.")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        when {
            vm.loggedIn -> {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(64.dp), tint = StatusColors.ok)
                Spacer(Modifier.height(12.dp))
                Text("Your drive is running", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (vm.hasTunnel) "Next: add a user, then sign in at https://${vm.driveHost}" else "Next: add a user from the Users tab.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { vm.finishSetup() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Open Drive Admin") }
            }
            problem != null -> {
                Text(problem, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.startServer() }) { Text("Try again") }
                TextButton(onClick = onShowLog) { Text("View log") }
            }
            else -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(if (s.driveHealthy) "Signing in…" else "Starting the server…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
