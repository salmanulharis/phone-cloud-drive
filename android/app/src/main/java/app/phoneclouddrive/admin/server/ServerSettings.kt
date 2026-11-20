package app.phoneclouddrive.admin.server

import android.content.Context
import android.os.Environment
import app.phoneclouddrive.admin.SecureStore
import java.io.File
import java.security.SecureRandom

/** Where the app's server lives: built into the app, or an external SFTPGo (the Termux setup). */
enum class ServerMode { BUILT_IN, EXTERNAL }

/**
 * Persistent settings of the built-in server. Secrets (admin password, tunnel token) are kept
 * in [SecureStore]; everything else is plain SharedPreferences.
 */
class ServerSettings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("server", Context.MODE_PRIVATE)
    private val secure = SecureStore(ctx)

    /** Null until the first-run setup has picked a mode. */
    var mode: ServerMode?
        get() = prefs.getString(KEY_MODE, null)?.let { runCatching { ServerMode.valueOf(it) }.getOrNull() }
        set(v) = prefs.edit().putString(KEY_MODE, v?.name).apply()

    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(v) = prefs.edit().putBoolean("setup_done", v).apply()

    /** Start the server after a reboot or an app update (only if it was running before). */
    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", true)
        set(v) = prefs.edit().putBoolean("start_on_boot", v).apply()

    /** The user's last start/stop choice; used to bring the server back after a reboot. */
    var shouldRun: Boolean
        get() = prefs.getBoolean("should_run", false)
        set(v) = prefs.edit().putBoolean("should_run", v).apply()

    /** Also serve the web drive and WebDAV on the home Wi-Fi (off: Cloudflare is the only way in). */
    var lanAccess: Boolean
        get() = prefs.getBoolean("lan_access", false)
        set(v) = prefs.edit().putBoolean("lan_access", v).apply()

    /** Folder that holds one sub-folder per user. */
    var storageBase: String
        get() = prefs.getString("storage_base", null) ?: defaultStorageBase()
        set(v) = prefs.edit().putString("storage_base", v).apply()

    // ---- Cloudflare ----

    var tunnelToken: String?
        get() = secure.get(SecureStore.TUNNEL_TOKEN)
        set(v) = if (v.isNullOrBlank()) secure.remove(SecureStore.TUNNEL_TOKEN) else secure.put(SecureStore.TUNNEL_TOKEN, v.trim())

    val hasTunnel: Boolean get() = secure.has(SecureStore.TUNNEL_TOKEN)

    /** Public hostname of the web drive, e.g. drive.example.com (for display and links). */
    var driveHost: String
        get() = prefs.getString("drive_host", "")!!
        set(v) = prefs.edit().putString("drive_host", v.trim().lowercase()).apply()

    /** Public hostname for WebDAV, e.g. dav.example.com (optional). */
    var davHost: String
        get() = prefs.getString("dav_host", "")!!
        set(v) = prefs.edit().putString("dav_host", v.trim().lowercase()).apply()

    fun clearTunnel() {
        tunnelToken = null
        prefs.edit().remove("drive_host").remove("dav_host").apply()
    }

    // ---- built-in admin account ----

    /** The built-in server's admin password: generated once, never shown, kept in the Keystore. */
    fun adminPassword(): String =
        secure.get(SecureStore.ADMIN_PASSWORD) ?: newAdminPassword()

    /** Replaces the admin password (used when the old one was lost, see [ServerController]). */
    fun newAdminPassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val rnd = SecureRandom()
        val pw = (1..32).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        secure.put(SecureStore.ADMIN_PASSWORD, pw)
        return pw
    }

    companion object {
        private const val KEY_MODE = "mode"
        const val ADMIN_USER = "admin"

        fun defaultStorageBase(): String =
            File(Environment.getExternalStorageDirectory(), ".CloudDrive").path
    }
}
