package app.phoneclouddrive.admin

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("drive_admin", 0)

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

    /** "Stay signed in": keep the admin password encrypted in the Android Keystore. */
    var staySignedIn by mutableStateOf(prefs.getBoolean("stay_signed_in", true))

    /** True while signing in automatically with the saved password. */
    var autoSigningIn by mutableStateOf(false)
        private set

    init {
        autoLogin()
    }

    /** Sign in with the saved password, if any (app start, after a reboot, or "Retry"). */
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

    /** True when the app runs on the server phone itself, so local storage stats are meaningful. */
    val isLocalServer: Boolean
        get() = server.contains("127.0.0.1") || server.contains("localhost")

    fun phoneStorage(): Pair<Long, Long> {
        val st = StatFs(Environment.getExternalStorageDirectory().path)
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

    /** Explicit sign-out: also forgets the saved password. */
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
