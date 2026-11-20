package app.phoneclouddrive.admin

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.phoneclouddrive.admin.server.ProcState
import app.phoneclouddrive.admin.server.ServerService
import app.phoneclouddrive.admin.server.ServerState
import app.phoneclouddrive.admin.server.SftpgoConfig

/** Overall status of the built-in server, as shown at the top of Home. */
private data class Headline(val title: String, val detail: String, val health: Health, val icon: ImageVector)

private fun headline(s: ServerState, driveHost: String): Headline {
    val sftpgo = s.process(ServerService.SFTPGO)
    return when {
        !s.active -> Headline("Stopped", "Your drive is offline. Start it to let people sign in.", Health.OFF, Icons.Filled.CloudOff)
        s.fatal != null -> Headline("Can't start", s.fatal, Health.WARN, Icons.Filled.ErrorOutline)
        sftpgo?.state == ProcState.WAITING -> Headline("Waiting", sftpgo.message ?: "Waiting for the phone", Health.WARN, Icons.Filled.ErrorOutline)
        sftpgo?.state == ProcState.BACKOFF -> Headline("Restarting", sftpgo.message ?: "The server stopped and is restarting", Health.WARN, Icons.Filled.CloudSync)
        !s.driveHealthy -> Headline("Starting…", "Getting the drive ready", Health.BUSY, Icons.Filled.CloudSync)
        s.tunnelConfigured && !s.tunnelUp -> s.process(ServerService.CLOUDFLARED)?.message?.let {
            Headline("Tunnel problem", it, Health.WARN, Icons.Filled.ErrorOutline)
        } ?: Headline(
            "Connecting…", "The drive is running. Connecting to Cloudflare for the public address.", Health.BUSY, Icons.Filled.CloudSync
        )
        s.tunnelConfigured -> Headline("Online", driveHost.ifBlank { "Reachable through Cloudflare" }, Health.OK, Icons.Filled.CloudDone)
        else -> Headline("Running", "Only reachable from this phone. Set up Cloudflare to use it from anywhere.", Health.OK, Icons.Filled.Cloud)
    }
}

@Composable
fun HomeTab(vm: AppViewModel, onOpenLogs: (String) -> Unit, onSetUpCloudflare: () -> Unit) {
    val s = vm.serverState
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusHero(vm, s) }
        item { Checklist(vm, onSetUpCloudflare) }
        if (vm.hasTunnel || vm.lanAccess) item { AddressesCard(vm) }
        if (s.active) item { ServicesCard(vm, s, onOpenLogs) }
        if (vm.loggedIn) item { OverviewCard(vm) }
    }
}

@Composable
private fun StatusHero(vm: AppViewModel, s: ServerState) {
    val h = headline(s, vm.driveHost)
    val gradient = when (h.health) {
        Health.OK -> listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
        Health.BUSY, Health.WARN -> listOf(Color(0xFF6D5BD0), Color(0xFF8B5CF6))
        Health.OFF -> listOf(Color(0xFF4B5563), Color(0xFF374151))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(gradient), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(h.icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(h.detail, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(18.dp))
            if (s.active) {
                OutlinedButton(
                    onClick = { vm.stopServer() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Stop server")
                }
            } else {
                Button(
                    onClick = { vm.startServer() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF4F46E5)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Start server", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Things that keep the server from working well, each with a one-tap fix. Hidden when all is fine. */
@Composable
private fun Checklist(vm: AppViewModel, onSetUpCloudflare: () -> Unit) {
    val ctx = LocalContext.current
    val resumed = rememberResumeCounter()
    val files = remember(resumed) { PhoneSetup.hasFilesAccess(ctx) }
    val battery = remember(resumed) { PhoneSetup.batteryUnrestricted(ctx) }
    val notifications = remember(resumed) { PhoneSetup.notificationsAllowed(ctx) }
    val requestFiles = rememberFilesAccessRequester()
    val requestNotifications = rememberNotificationRequester()
    if (files && battery && notifications && vm.hasTunnel) return

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Finish setting up",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (!files) FixRow(Icons.Filled.FolderOpen, "Allow file access", "Needed to store the drive's files", "Allow", requestFiles)
            if (!battery) FixRow(
                Icons.Filled.BatteryAlert, "Run without battery limits", "Keeps the drive online with the screen off", "Allow"
            ) { ctx.startActivity(PhoneSetup.batteryIntent(ctx)) }
            if (!notifications && Build.VERSION.SDK_INT >= 33) FixRow(
                Icons.Filled.NotificationsOff, "Show the status notification", "Lets you see and stop the server anytime", "Allow",
                requestNotifications
            )
            if (!vm.hasTunnel) FixRow(
                Icons.Filled.Language, "Connect Cloudflare", "Gives your drive a secure public address", "Set up", onSetUpCloudflare
            )
        }
    }
}

@Composable
private fun FixRow(icon: ImageVector, title: String, detail: String, action: String, onClick: () -> Unit) {
    ItemRow(icon, title, detail, iconTint = MaterialTheme.colorScheme.onSecondaryContainer) {
        TextButton(onClick = onClick) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun AddressesCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    val lanIp = remember(vm.serverState.active, vm.lanAccess) { if (vm.lanAccess) PhoneSetup.lanAddress(ctx) else null }
    SectionTitle("Addresses")
    GroupCard {
        if (vm.hasTunnel && vm.driveHost.isNotBlank()) AddressRow(Icons.Filled.Language, "Web drive", "https://${vm.driveHost}", openable = true)
        if (vm.hasTunnel && vm.davHost.isNotBlank()) AddressRow(Icons.Filled.Dns, "WebDAV (network drive)", "https://${vm.davHost}")
        if (lanIp != null) {
            AddressRow(Icons.Filled.Wifi, "Web drive on home Wi-Fi", "http://$lanIp:${SftpgoConfig.WEB_PORT}", openable = true)
            AddressRow(Icons.Filled.Wifi, "WebDAV on home Wi-Fi", "http://$lanIp:${SftpgoConfig.WEBDAV_PORT}")
        }
    }
}

@Composable
private fun AddressRow(icon: ImageVector, label: String, url: String, openable: Boolean = false) {
    val ctx = LocalContext.current
    ItemRow(icon, label, url, onClick = if (openable) ({ openUrl(ctx, url) }) else null) {
        Row {
            if (openable) IconButton(onClick = { openUrl(ctx, url) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open") }
            IconButton(onClick = { copyText(ctx, label, url) }) { Icon(Icons.Filled.ContentCopy, "Copy") }
        }
    }
}

@Composable
private fun ServicesCard(vm: AppViewModel, s: ServerState, onOpenLogs: (String) -> Unit) {
    SectionTitle("Services")
    GroupCard {
        val drive = s.process(ServerService.SFTPGO)
        val driveHealth = when {
            s.driveHealthy -> Health.OK
            drive?.state == ProcState.WAITING || drive?.state == ProcState.BACKOFF -> Health.WARN
            else -> Health.BUSY
        }
        ItemRow(
            Icons.Filled.Cloud, "Drive server",
            buildString {
                append(if (vm.lanAccess) "Web and WebDAV · this phone + home Wi-Fi" else "Web and WebDAV · only through Cloudflare")
                if ((drive?.restarts ?: 0) > 0) append(" · restarted ${drive!!.restarts}×")
            },
            onClick = { onOpenLogs(ServerService.SFTPGO) }
        ) { StatusPill(if (s.driveHealthy) "Running" else stateLabel(drive?.state), driveHealth) }

        if (s.tunnelConfigured) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            val tunnel = s.process(ServerService.CLOUDFLARED)
            val problem = tunnel?.message
            ItemRow(
                Icons.Filled.Language, "Cloudflare tunnel",
                when {
                    s.tunnelUp -> "${s.tunnelConnections} connection(s) to Cloudflare"
                    problem != null -> problem
                    else -> "Not connected yet"
                },
                onClick = { onOpenLogs(ServerService.CLOUDFLARED) }
            ) {
                StatusPill(
                    when {
                        s.tunnelUp -> "Connected"
                        problem != null -> "Problem"
                        tunnel?.state == ProcState.RUNNING -> "Connecting"
                        else -> stateLabel(tunnel?.state)
                    },
                    when {
                        s.tunnelUp -> Health.OK
                        problem != null || tunnel?.state == ProcState.BACKOFF -> Health.WARN
                        else -> Health.BUSY
                    }
                )
            }
        }
    }
}

private fun stateLabel(state: ProcState?) = when (state) {
    ProcState.WAITING -> "Waiting"
    ProcState.BACKOFF -> "Restarting"
    ProcState.STOPPED, null -> "Stopped"
    else -> "Starting"
}
