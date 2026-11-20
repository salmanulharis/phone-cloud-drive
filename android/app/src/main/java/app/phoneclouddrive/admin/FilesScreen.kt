package app.phoneclouddrive.admin

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import java.io.File

/** Browses one user's drive folder directly on the server phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(vm: AppViewModel, username: String, homeDir: String, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember(homeDir) { FileOps.resolveHome(homeDir) }
    var dir by remember(root) { mutableStateOf(root) }
    var access by remember { mutableStateOf(FileOps.hasAccess(ctx)) }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<DriveEntry>>(emptyList()) }
    var toDelete by remember { mutableStateOf<DriveEntry?>(null) }
    val requestAccess = rememberFilesAccessRequester()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = FileOps.hasAccess(ctx) }

    LaunchedEffect(dir, access, reload) {
        if (!access) return@LaunchedEffect
        loading = true
        entries = FileOps.list(dir)
        loading = false
    }

    val atRoot = dir.canonicalPath == root.canonicalPath
    BackHandler { if (atRoot) onClose() else dir = dir.parentFile ?: root }

    val crumbs = buildList {
        add(username)
        if (!atRoot) addAll(dir.canonicalPath.removePrefix(root.canonicalPath).trim('/').split('/'))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (atRoot) "$username's files" else dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            crumbs.joinToString("  ›  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (atRoot) onClose() else dir = dir.parentFile ?: root }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = { IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Refresh") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

            when {
                !vm.isLocalServer -> CenterNote(
                    "File browsing works on the server phone",
                    "Open Drive Admin on the server phone itself (server address http://127.0.0.1:8080) to see files."
                )
                !access -> CenterNote(
                    "Allow file access",
                    "Drive Admin needs “All files access” to read the private drive folder. It only reads .CloudDrive.",
                    action = "Allow" to requestAccess
                )
                !root.exists() -> CenterNote("Folder is empty", "Nothing has been uploaded for $username yet.")
                else -> {
                    PrivacyBanner(entries)
                    if (entries.isEmpty() && !loading) {
                        CenterNote("Empty folder", "Files uploaded to the drive will appear here.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(entries, key = { it.file.path }) { e ->
                                FileRow(
                                    e = e,
                                    onClick = {
                                        if (e.kind == Kind.FOLDER) dir = e.file
                                        else try {
                                            FileOps.open(ctx, e.file)
                                        } catch (x: ActivityNotFoundException) {
                                            vm.notice = "No app can open ${e.file.name}"
                                        } catch (x: Exception) {
                                            vm.error = x.message
                                        }
                                    },
                                    onSavePublic = {
                                        scope.launch {
                                            try {
                                                val where = FileOps.saveToPublic(ctx, e.file)
                                                vm.notice = "Copied to $where. It's now visible in Gallery and other apps."
                                            } catch (x: Exception) {
                                                vm.error = "Couldn't copy: ${x.message}"
                                            }
                                        }
                                    },
                                    onDelete = { toDelete = e }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${e.file.name}?") },
            text = {
                Text(
                    if (e.kind == Kind.FOLDER) "This permanently deletes the folder and the ${e.childCount} item(s) inside it from $username's drive. This can't be undone."
                    else "This permanently deletes the file from $username's drive. This can't be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        toDelete = null
                        scope.launch {
                            if (FileOps.delete(e.file)) {
                                vm.notice = "Deleted ${e.file.name}"
                                vm.rescan(username) // keep the user's storage usage accurate
                            } else vm.error = "Couldn't delete ${e.file.name}"
                            reload++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PrivacyBanner(entries: List<DriveEntry>) {
    val folders = entries.count { it.kind == Kind.FOLDER }
    val files = entries.size - folders
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_eye_off), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(6.dp))
                Text("Private · hidden from Gallery", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "$folders folders · $files files",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FileRow(e: DriveEntry, onClick: () -> Unit, onSavePublic: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumb(e)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.kind == Kind.FOLDER) "${e.childCount} items · ${formatTime(e.modified)}"
                else "${formatBytes(e.size)} · ${formatTime(e.modified)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (e.kind != Kind.FOLDER) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { menu = false; onClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to phone (make public)") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_save_public), null) },
                        onClick = { menu = false; onSavePublic() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun FileThumb(e: DriveEntry) {
    val sizeDp = 44.dp
    val px = with(LocalDensity.current) { sizeDp.roundToPx() }
    val bmp by produceState<android.graphics.Bitmap?>(null, e.file.path, e.modified) {
        value = if (e.kind == Kind.IMAGE || e.kind == Kind.VIDEO) FileOps.thumbnail(e.file, px) else null
    }
    val shape = RoundedCornerShape(12.dp)
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp).clip(shape)
        )
    } else {
        val (icon, bg, fg) = when (e.kind) {
            Kind.FOLDER -> Triple(R.drawable.ic_kind_folder, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            Kind.IMAGE -> Triple(R.drawable.ic_kind_image, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            Kind.VIDEO -> Triple(R.drawable.ic_kind_video, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            Kind.AUDIO -> Triple(R.drawable.ic_kind_audio, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Kind.OTHER -> Triple(R.drawable.ic_kind_file, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(sizeDp).clip(shape).background(bg), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = fg)
        }
    }
}

@Composable
private fun CenterNote(title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.let { (label, go) -> Button(onClick = go, modifier = Modifier.padding(top = 8.dp)) { Text(label) } }
        }
    }
}
