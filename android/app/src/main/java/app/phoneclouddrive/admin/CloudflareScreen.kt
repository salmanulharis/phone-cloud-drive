package app.phoneclouddrive.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.phoneclouddrive.admin.server.Cloudflare
import app.phoneclouddrive.admin.server.SftpgoConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudflareScreen(vm: AppViewModel, snackbar: SnackbarHostState, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloudflare connection") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
            CloudflareSetup(vm, onDone = onClose)
        }
    }
}

/** The setup form, also used as a step of the first-run wizard. */
@Composable
fun CloudflareSetup(vm: AppViewModel, onDone: () -> Unit) {
    var automatic by rememberSaveable { mutableStateOf(true) }
    Text(
        "Cloudflare gives your drive a secure https:// address that works from anywhere, without opening " +
            "ports on your router. You need a domain that uses Cloudflare (the free plan is enough).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = automatic, onClick = { automatic = true },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            icon = { SegmentedButtonDefaults.Icon(automatic) { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)) } }
        ) { Text("Automatic") }
        SegmentedButton(
            selected = !automatic, onClick = { automatic = false },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            icon = { SegmentedButtonDefaults.Icon(!automatic) { Icon(Icons.Filled.Key, null, Modifier.size(18.dp)) } }
        ) { Text("Tunnel token") }
    }
    Spacer(Modifier.height(16.dp))
    if (automatic) AutomaticSetup(vm, onDone) else ManualSetup(vm, onDone)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomaticSetup(vm: AppViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var apiToken by rememberSaveable { mutableStateOf("") }
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var driveSub by rememberSaveable { mutableStateOf("drive") }
    var withDav by rememberSaveable { mutableStateOf(true) }
    var davSub by rememberSaveable { mutableStateOf("dav") }
    val zone = vm.zones.firstOrNull { it.id == zoneId } ?: vm.zones.singleOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            "1. Create an API token",
            "In Cloudflare: My Profile › API Tokens › Create Token › Custom token, with these permissions:\n" +
                "• Account › Cloudflare Tunnel › Edit\n• Zone › DNS › Edit\n• Zone › Zone › Read\n" +
                "The token is used once for setup and is not saved on the phone."
        ) {
            OutlinedButton(onClick = { openUrl(ctx, Cloudflare.TOKEN_HELP_URL) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open Cloudflare")
            }
        }
        Text("2. Paste it and pick your domain", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = apiToken, onValueChange = { apiToken = it.trim() },
            label = { Text("API token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { vm.loadZones(apiToken) },
            enabled = apiToken.length >= 20 && !vm.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (vm.zones.isEmpty()) "Find my domains" else "Reload domains") }

        if (vm.zones.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                vm.zones.forEach { z ->
                    FilterChip(selected = zone?.id == z.id, onClick = { zoneId = z.id }, label = { Text(z.name) })
                }
            }
        }

        if (zone != null) {
            Text("3. Choose the addresses", style = MaterialTheme.typography.titleSmall)
            HostField("Web drive", driveSub, zone.name) { driveSub = it }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("WebDAV address", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Map the drive as a network drive on computers and phones",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = withDav, onCheckedChange = { withDav = it })
            }
            if (withDav) HostField("WebDAV", davSub, zone.name) { davSub = it }
            Button(
                onClick = { vm.setUpTunnel(apiToken, zone, driveSub, if (withDav) davSub else "", onDone) },
                enabled = !vm.busy && driveSub.isNotBlank() && (!withDav || (davSub.isNotBlank() && davSub != driveSub)),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(if (vm.busy) "Connecting…" else "Connect", fontWeight = FontWeight.SemiBold) }
            Text(
                "Creates a tunnel named \"${Cloudflare.TUNNEL_NAME}\" and DNS records for these addresses. " +
                    "Running it again updates them.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HostField(label: String, value: String, zone: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '.' }) },
        label = { Text(label) },
        suffix = { Text(".$zone") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ManualSetup(vm: AppViewModel, onDone: () -> Unit) {
    var token by rememberSaveable { mutableStateOf("") }
    var drive by rememberSaveable { mutableStateOf(vm.driveHost) }
    var dav by rememberSaveable { mutableStateOf(vm.davHost) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            "Set up the tunnel in Cloudflare",
            "Zero Trust › Networks › Tunnels › Create a tunnel (Cloudflared). Add public hostnames:\n" +
                "• your drive address  →  HTTP  127.0.0.1:${SftpgoConfig.WEB_PORT}\n" +
                "• your WebDAV address  →  HTTP  127.0.0.1:${SftpgoConfig.WEBDAV_PORT}  (optional)\n" +
                "Then copy the token from the install command (the long text after --token)."
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it.trim().removePrefix("--token").trim() },
            label = { Text("Tunnel token") },
            visualTransformation = PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
            isError = token.isNotEmpty() && Cloudflare.tunnelIdFromToken(token) == null,
            supportingText = { if (token.isNotEmpty() && Cloudflare.tunnelIdFromToken(token) == null) Text("Not a valid tunnel token") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = drive, onValueChange = { drive = it }, label = { Text("Drive address (e.g. drive.example.com)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = dav, onValueChange = { dav = it }, label = { Text("WebDAV address (optional)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { vm.saveManualTunnel(token, drive, dav, onDone) },
            enabled = !vm.busy && Cloudflare.tunnelIdFromToken(token) != null && drive.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Save and connect", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun InfoCard(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}
