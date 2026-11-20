package app.phoneclouddrive.admin

import android.app.Application
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phoneclouddrive.admin.server.Cloudflare
import app.phoneclouddrive.admin.server.ServerController
import app.phoneclouddrive.admin.server.ServerMode
import app.phoneclouddrive.admin.server.ServerSettings
import app.phoneclouddrive.admin.server.ServerState
import app.phoneclouddrive.admin.server.SftpgoConfig
import app.phoneclouddrive.admin.server.Zone
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Folder access presets mapped to SFTPGo permissions on "/". */
enum class Access(val label: String, val perms: List<String>) {
    FULL("Full access", listOf("*")),
    NO_DELETE("Upload, no delete", listOf("list", "download", "upload", "create_dirs")),
    READ_ONLY("View only", listOf("list", "download"));

    companion object {
        fun of(user: JSONObject): Access? {
            val arr = user.optJSONObject("permissions")?.optJSONArray("/") ?: return null
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            return entries.firstOrNull { it.perms.toSet() == set }
        }
    }
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("drive_admin", 0)
    val settings = ServerSettings(app)

    // ---- mode / setup ----

    var mode by mutableStateOf(settings.mode)
        private set
    var setupDone by mutableStateOf(settings.setupDone)
        private set
    val builtIn get() = mode == ServerMode.BUILT_IN

    fun chooseMode(m: ServerMode) {
        if (m != mode) {
            if (mode == ServerMode.BUILT_IN) ServerController.stop(app)
            logout()
        }
        settings.mode = m
        mode = m
        if (m == ServerMode.BUILT_IN && serverState.driveHealthy && !loggedIn) connectBuiltIn()
    }

    fun finishSetup() {
        settings.setupDone = true
        setupDone = true
    }

    // ---- built-in server ----

    var serverState by mutableStateOf(ServerState())
        private set

    var lanAccess by mutableStateOf(settings.lanAccess)
        private set
    var startOnBoot by mutableStateOf(settings.startOnBoot)
        private set
    var storageBase by mutableStateOf(settings.storageBase)
        private set
    var driveHost by mutableStateOf(settings.driveHost)
        private set
    var davHost by mutableStateOf(settings.davHost)
        private set
    var hasTunnel by mutableStateOf(settings.hasTunnel)
        private set

    /** The admin password was rejected once and a reset was requested; don't loop on it. */
    private var adminResetRequested = false

    fun startServer() = ServerController.start(app)

    fun stopServer() = ServerController.stop(app)

    fun changeLanAccess(on: Boolean) {
        settings.lanAccess = on
        lanAccess = on
        ServerController.applySettings(app)
    }

    fun changeStartOnBoot(on: Boolean) {
        settings.startOnBoot = on
        startOnBoot = on
    }

    fun setStorage(path: String) {
        settings.storageBase = path
        storageBase = path
        ServerController.applySettings(app)
        notice = "New users will be stored in ${prettyPath(path)}"
    }

    fun signOutEverywhere() {
        ServerController.signOutEverywhere(app)
        notice = "Every web and WebDAV session has been signed out"
    }

    private fun connectBuiltIn() = task {
        api = SftpgoApi("http://${SftpgoConfig.LOOPBACK}:${SftpgoConfig.ADMIN_PORT}")
        try {
            api.login(ServerSettings.ADMIN_USER, settings.adminPassword())
        } catch (e: ApiException) {
            if (e.code == 401 && !adminResetRequested) {
                // The stored admin password no longer matches (e.g. Keystore reset): recreate the
                // admin account. Users, shares and files are not affected.
                adminResetRequested = true
                notice = "Repairing the app's admin access…"
                ServerController.resetAdmin(app)
                return@task
            }
            throw e
        }
        adminResetRequested = false
        try {
            api.ensureDefenderSafe("127.0.0.1/32", "This phone (Drive Admin app)")
            api.ensureDefenderSafe("::1/128", "This phone (Drive Admin app)")
        } catch (e: ApiException) {
            // Not fatal: the drive works without it, the app just isn't protected from a lockout.
        }
        loggedIn = true
        load()
    }

    // ---- Cloudflare ----

    var zones by mutableStateOf<List<Zone>>(emptyList())
        private set

    fun loadZones(apiToken: String) = task {
        val list = Cloudflare(apiToken.trim()).zones()
        if (list.isEmpty()) error("No active domains found for this token. Check its Zone permissions.")
        zones = list
    }

    fun setUpTunnel(apiToken: String, zone: Zone, driveSub: String, davSub: String, onDone: () -> Unit) = task {
        val drive = hostFor(driveSub, zone.name)
        val dav = if (davSub.isBlank()) "" else hostFor(davSub, zone.name)
        val result = Cloudflare(apiToken.trim()).setUp(zone, Cloudflare.TUNNEL_NAME, drive, dav)
        saveTunnel(result.token, result.driveHost, result.davHost)
        zones = emptyList()
        notice = "Connected. Your drive will be at https://${result.driveHost}"
        onDone()
    }

    fun saveManualTunnel(token: String, drive: String, dav: String, onDone: () -> Unit) = task {
        val t = token.trim().removePrefix("cloudflared service install").trim()
        if (Cloudflare.tunnelIdFromToken(t) == null) error("That doesn't look like a tunnel token. Copy it from the tunnel's install command.")
        saveTunnel(t, cleanHost(drive), cleanHost(dav))
        notice = "Tunnel saved"
        onDone()
    }

    fun removeTunnel() {
        settings.clearTunnel()
        refreshTunnelState()
        ServerController.applySettings(app)
        notice = "Cloudflare connection removed from this phone"
    }

    private fun saveTunnel(token: String, drive: String, dav: String) {
        settings.tunnelToken = token
        settings.driveHost = drive
        settings.davHost = dav
        refreshTunnelState()
        if (serverState.active) ServerController.applySettings(app)
    }

    private fun refreshTunnelState() {
        hasTunnel = settings.hasTunnel
        driveHost = settings.driveHost
        davHost = settings.davHost
    }

    private fun hostFor(sub: String, zone: String): String {
        val s = sub.trim().lowercase().trim('.')
        require(s.isEmpty() || Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*").matches(s)) {
            "\"$sub\" isn't a valid subdomain"
        }
        return if (s.isEmpty()) zone else "$s.$zone"
    }

    private fun cleanHost(h: String) = h.trim().lowercase()
        .removePrefix("https://").removePrefix("http://").trimEnd('/')

    // ---- admin API session (built-in: automatic; external: login form) ----

    var server by mutableStateOf(prefs.getString("server", "http://127.0.0.1:8080")!!)
    var adminUser by mutableStateOf(prefs.getString("admin", "")!!)
    var loggedIn by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    var users by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var connections by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var status by mutableStateOf<JSONObject?>(null)
        private set

    private var api = SftpgoApi(server)
    private val secure = SecureStore(app)

    /** "Stay signed in" (external server): keep the admin password encrypted in the Android Keystore. */
    var staySignedIn by mutableStateOf(prefs.getBoolean("stay_signed_in", true))

    /** True while signing in automatically with the saved password. */
    var autoSigningIn by mutableStateOf(false)
        private set

    /** Sign in to an external server with the saved password, if any. */
    fun autoLogin() {
        if (loggedIn || !staySignedIn || adminUser.isBlank() || !secure.hasSaved()) return
        val saved = secure.load() ?: return
        autoSigningIn = true
        task {
            try {
                api = SftpgoApi(server)
                api.login(adminUser, saved)
                loggedIn = true
                load()
            } catch (e: ApiException) {
                // wrong/changed password: forget it. Server not reachable yet (code 0): keep it for a retry.
                if (e.code == 401) secure.clear()
                throw e
            } finally {
                autoSigningIn = false
            }
        }
    }

    val hasSavedLogin: Boolean
        get() = secure.hasSaved()

    /** True when the drive's files are on this phone, so file browsing and storage stats work. */
    val isLocalServer: Boolean
        get() = builtIn || server.contains("127.0.0.1") || server.contains("localhost")

    /** Free and total bytes of the volume holding the drive. */
    fun phoneStorage(): Pair<Long, Long> {
        val dir = File(if (builtIn) storageBase else ServerSettings.defaultStorageBase())
        val probe = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() } ?: File("/sdcard")
        val st = StatFs(probe.path)
        return st.availableBytes to st.totalBytes
    }

    fun login(password: String) = task {
        val url = server.trim().trimEnd('/')
        server = url
        api = SftpgoApi(url)
        api.login(adminUser.trim(), password)
        prefs.edit()
            .putString("server", url)
            .putString("admin", adminUser.trim())
            .putBoolean("stay_signed_in", staySignedIn)
            .apply()
        if (staySignedIn) secure.save(password) else secure.clear()
        loggedIn = true
        load()
    }

    /** External server: sign out and forget the saved password. */
    fun signOut() {
        secure.clear()
        logout()
    }

    fun logout() {
        loggedIn = false
        users = emptyList()
        connections = emptyList()
        status = null
        api = SftpgoApi(server)
    }

    fun refresh() = task { load() }

    /** Background refresh while the app is open: no spinner, errors are ignored. */
    fun quietRefresh() {
        if (!loggedIn || busy) return
        viewModelScope.launch {
            try {
                load()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun load() {
        users = api.users().sortedBy { it.optString("username").lowercase() }
        connections = api.connections()
        status = try { api.status() } catch (e: Exception) { null }
    }

    fun createUser(name: String, password: String, quotaGb: Double, access: Access, onDone: () -> Unit) = task {
        val u = JSONObject()
            .put("username", name.trim())
            .put("password", password)
            .put("status", 1)
            .put("permissions", JSONObject().put("/", JSONArray(access.perms)))
            .put("quota_size", gbToBytes(quotaGb))
        api.createUser(u)
        notice = "User ${name.trim()} created"
        load()
        onDone()
    }

    fun saveUser(
        name: String, enabled: Boolean, quotaGb: Double, access: Access?, newPassword: String,
        onDone: () -> Unit
    ) = task {
        val u = api.user(name) // start from the full current record so nothing else changes
        u.put("status", if (enabled) 1 else 0)
        u.put("quota_size", gbToBytes(quotaGb))
        if (access != null) {
            val perms = u.optJSONObject("permissions") ?: JSONObject()
            perms.put("/", JSONArray(access.perms))
            u.put("permissions", perms)
        }
        if (newPassword.isNotEmpty()) u.put("password", newPassword) else u.remove("password")
        api.updateUser(name, u)
        notice = "Saved $name"
        load()
        onDone()
    }

    fun deleteUser(name: String, onDone: () -> Unit) = task {
        api.deleteUser(name)
        notice = "Deleted $name (files kept in CloudDrive/$name)"
        load()
        onDone()
    }

    fun rescan(name: String) = task {
        api.scanQuota(name)
        notice = "Recalculating usage for $name…"
        kotlinx.coroutines.delay(1500)
        load()
    }

    fun disconnect(id: String) = task {
        api.disconnect(id)
        notice = "Disconnected"
        load()
    }

    // Last in the class on purpose: the collector runs immediately (Main.immediate) and touches
    // properties declared above, which must already be initialised.
    init {
        viewModelScope.launch {
            ServerController.state.collect { s ->
                val wasHealthy = serverState.driveHealthy
                serverState = s
                if (!builtIn) return@collect
                if (s.driveHealthy && !wasHealthy && !loggedIn) connectBuiltIn()
                if (!s.active && loggedIn) logout()
            }
        }
        if (mode == ServerMode.EXTERNAL) autoLogin()
    }

    private fun task(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: ApiException) {
                error = e.message
                if (e.code == 401 && loggedIn) logout()
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }
}

// ---- formatting helpers ----

private const val GB = 1024.0 * 1024 * 1024

fun gbToBytes(gb: Double): Long = if (gb <= 0) 0 else (gb * GB).toLong()

fun bytesToGb(b: Long): Double = b / GB

fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

fun formatGbInput(gb: Double): String =
    if (gb <= 0) "" else if (gb % 1.0 == 0.0) gb.toLong().toString() else String.format(Locale.US, "%.2f", gb)

fun formatTime(ms: Long): String {
    if (ms <= 0) return "never"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} h ago"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} days ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    }
}

/** "/storage/emulated/0/.CloudDrive" -> "Internal storage/.CloudDrive". */
fun prettyPath(path: String): String = path
    .replaceFirst(Regex("^(/storage/emulated/0|/sdcard|/storage/self/primary)(?=/|$)"), "Internal storage")
    .replaceFirst(Regex("^/storage/([0-9A-F]{4}-[0-9A-F]{4})(?=/|$)"), "SD card ($1)")
