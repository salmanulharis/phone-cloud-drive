package app.phoneclouddrive.admin.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.phoneclouddrive.admin.FileOps
import app.phoneclouddrive.admin.MainActivity
import app.phoneclouddrive.admin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Foreground service that runs the built-in server: SFTPGo (web drive, WebDAV, admin API) and,
 * when configured, cloudflared. It holds a partial wakelock and a Wi-Fi lock so both keep working
 * with the screen off, restarts the tunnel when the network changes, and publishes health to
 * [ServerController.state].
 */
class ServerService : Service() {
    companion object {
        const val ACTION_START = "app.phoneclouddrive.admin.START"
        const val ACTION_STOP = "app.phoneclouddrive.admin.STOP"
        const val ACTION_RELAUNCH = "app.phoneclouddrive.admin.RELAUNCH"
        const val ACTION_ROTATE_KEY = "app.phoneclouddrive.admin.ROTATE_KEY"
        const val ACTION_RESET_ADMIN = "app.phoneclouddrive.admin.RESET_ADMIN"
        const val ACTION_RESTART_TUNNEL = "app.phoneclouddrive.admin.RESTART_TUNNEL"

        const val SFTPGO = "sftpgo"
        const val CLOUDFLARED = "cloudflared"
        private val METRICS = "${SftpgoConfig.LOOPBACK}:${SftpgoConfig.TUNNEL_METRICS_PORT}"
        private const val CHANNEL = "server"
        private const val NOTIFICATION_ID = 1

        @Volatile
        internal var instance: ServerService? = null
            private set

        /** Writes a new random session-signing key (owner-only file inside the app's private dir). */
        fun writeSigningKey(paths: ServerPaths) {
            paths.ensure()
            val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
            val key = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
            val tmp = File(paths.data, "signing.key.tmp")
            tmp.writeText(key)
            tmp.setReadable(false, false)
            tmp.setReadable(true, true)
            if (!tmp.renameTo(paths.signingKey)) error("Couldn't write the signing key")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** All start/stop work runs here, one operation at a time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial = Dispatchers.IO.limitedParallelism(1)

    private lateinit var paths: ServerPaths
    private lateinit var settings: ServerSettings
    @Volatile private var supervisor: Supervisor? = null
    private var healthJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNotificationText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        paths = ServerPaths(this)
        settings = ServerSettings(this)
        startInForeground(getString(R.string.server_starting))
        acquireLocks()
        watchNetwork()
        ServerController.update { ServerState(active = true) }
        healthJob = scope.launch { healthLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch(serial) {
                settings.shouldRun = false // also when stopped from the notification
                shutdown()
                stopSelf()
            }
            ACTION_RELAUNCH -> scope.launch(serial) { relaunch() }
            ACTION_ROTATE_KEY -> scope.launch(serial) {
                writeSigningKey(paths)
                supervisor?.restart(SFTPGO)
            }
            ACTION_RESET_ADMIN -> scope.launch(serial) {
                shutdown()
                resetAdminAccount()
                launchProcesses()
            }
            ACTION_RESTART_TUNNEL -> scope.launch(serial) { supervisor?.restart(CLOUDFLARED) }
            // ACTION_START, or null when Android restarts the service (START_STICKY)
            else -> scope.launch(serial) { if (supervisor == null) launchProcesses() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        healthJob?.cancel()
        // Normally already stopped by ACTION_STOP; this covers stopService()/system shutdowns.
        supervisor?.stop()
        supervisor = null
        networkCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        instance = null
        ServerController.update { ServerState() }
        super.onDestroy()
    }

    fun log(name: String): List<String> =
        supervisor?.daemons?.firstOrNull { it.spec.name == name }?.log?.snapshot().orEmpty()

    // ---- processes ----

    private fun launchProcesses() {
        try {
            check(paths.sftpgo.exists()) { "The server program is missing from this build of the app." }
            paths.ensure()
            UiInstaller.ensure(this, paths)
            if (!paths.signingKey.exists() || paths.signingKey.length() == 0L) writeSigningKey(paths)
        } catch (e: Exception) {
            ServerController.update { it.copy(fatal = e.message ?: e.toString()) }
            return
        }
        Supervisor.killStale(listOf(paths.sftpgo, paths.cloudflared))
        val sv = Supervisor(buildSpecs(), paths.logs) { publish() }
        supervisor = sv
        ServerController.update { it.copy(fatal = null, tunnelConfigured = settings.hasTunnel) }
        sv.start()
    }

    private fun shutdown() {
        supervisor?.stop()
        supervisor = null
        publish()
    }

    private fun relaunch() {
        shutdown()
        launchProcesses()
    }

    private fun buildSpecs(): List<ProcessSpec> {
        val base = paths.env()
        val sftpgoEnv = base + SftpgoConfig.env(
            SftpgoConfig.Input(
                dataDir = paths.data.path,
                uiDir = paths.ui.path,
                storageBase = settings.storageBase,
                lanAccess = settings.lanAccess,
                adminUser = ServerSettings.ADMIN_USER,
                adminPassword = settings.adminPassword(),
            )
        )
        val specs = mutableListOf(
            ProcessSpec(
                name = SFTPGO,
                // An empty log file path makes SFTPGo log to stdout, which the supervisor captures.
                command = listOf(paths.sftpgo.path, "serve", "--config-dir", paths.data.path, "--log-file-path", ""),
                env = sftpgoEnv,
                workdir = paths.data,
                prepare = { prepareSftpgo(sftpgoEnv) },
            )
        )
        settings.tunnelToken?.let { token ->
            specs += ProcessSpec(
                name = CLOUDFLARED,
                command = listOf(
                    paths.cloudflared.path, "tunnel", "--no-autoupdate",
                    "--protocol", "http2", "--metrics", METRICS, "run",
                ),
                // Through the environment so the token never shows up in a process listing.
                env = base + mapOf("TUNNEL_TOKEN" to token),
                workdir = paths.data,
                explain = ::explainTunnel,
            )
        }
        return specs
    }

    /** Runs before every SFTPGo start. Returns a user-facing problem, or null when ready. */
    private fun prepareSftpgo(env: Map<String, String>): String? {
        if (!FileOps.hasAccess(this)) return getString(R.string.problem_files_access)
        val base = File(settings.storageBase)
        base.mkdirs()
        if (!base.isDirectory || !base.canWrite()) return getString(R.string.problem_storage, base.path)
        File(base, ".nomedia").takeIf { !it.exists() }?.createNewFile()

        // Creates the database on first run and migrates it after SFTPGo updates.
        val output = File(paths.logs, "initprovider.log")
        val init = ProcessBuilder(paths.sftpgo.path, "initprovider", "--config-dir", paths.data.path)
            .directory(paths.data).redirectErrorStream(true).redirectOutput(output)
        init.environment().putAll(env)
        val p = init.start()
        if (!p.waitFor(2, TimeUnit.MINUTES)) {
            p.destroyForcibly()
            return "Preparing the database timed out"
        }
        if (p.exitValue() != 0) {
            val detail = Supervisor.stripAnsi(output.readText()).lines().lastOrNull { it.isNotBlank() }.orEmpty()
            return "Couldn't prepare the database. $detail".trim()
        }
        return null
    }

    /** Deletes the admin row (server stopped) so SFTPGo recreates it with a new password. */
    private fun resetAdminAccount() {
        settings.newAdminPassword()
        if (!paths.database.exists()) return
        try {
            SQLiteDatabase.openDatabase(paths.database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.setForeignKeyConstraintsEnabled(true)
                db.delete("admins", "username = ?", arrayOf(ServerSettings.ADMIN_USER))
            }
        } catch (e: Exception) {
            ServerController.update { it.copy(fatal = "Couldn't reset the admin account: ${e.message}") }
        }
    }

    // ---- health ----

    private suspend fun healthLoop() {
        while (scope.isActive) {
            val sv = supervisor
            val drive = sv != null && httpOk("http://127.0.0.1:${SftpgoConfig.ADMIN_PORT}/healthz")
            val tunnel = if (sv != null && settings.hasTunnel) tunnelConnections() else 0
            ServerController.update {
                it.copy(driveHealthy = drive, tunnelConnections = tunnel, tunnelConfigured = settings.hasTunnel)
            }
            publish()
            delay(if (drive && (tunnel > 0 || !settings.hasTunnel)) 10_000 else 3_000)
        }
    }

    private fun httpOk(url: String): Boolean = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 2000
            readTimeout = 2000
            try { responseCode in 200..299 } finally { disconnect() }
        }
    } catch (_: Exception) {
        false
    }

    /** Ready edge connections reported by cloudflared's /ready endpoint (0 when not connected). */
    private fun tunnelConnections(): Int = try {
        (URL("http://$METRICS/ready").openConnection() as HttpURLConnection).run {
            connectTimeout = 2000
            readTimeout = 2000
            try {
                val stream = if (responseCode >= 400) errorStream else inputStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                JSONObject(body).optInt("readyConnections", 0)
            } finally {
                disconnect()
            }
        }
    } catch (_: Exception) {
        0
    }

    private fun publish() {
        val sv = supervisor
        ServerController.update { it.copy(active = true, processes = sv?.statuses().orEmpty()) }
        updateNotification()
    }

    // ---- notification ----

    private fun startInForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_server), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_server_description)
                setShowBadge(false)
            }
        )
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(text), type)
        lastNotificationText = text
    }

    private fun updateNotification() {
        val s = ServerController.state.value
        val sftpgo = s.process(SFTPGO)
        val text = when {
            s.fatal != null -> s.fatal
            sftpgo?.state == ProcState.WAITING -> sftpgo.message ?: getString(R.string.server_waiting)
            !s.driveHealthy -> getString(R.string.server_starting)
            s.tunnelConfigured && !s.tunnelUp ->
                s.process(CLOUDFLARED)?.message ?: getString(R.string.server_tunnel_connecting)
            s.tunnelConfigured -> getString(R.string.server_online_at, settings.driveHost.ifBlank { "Cloudflare" })
            else -> getString(R.string.server_online_local)
        }
        if (text == lastNotificationText) return
        lastNotificationText = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle(getString(R.string.server_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ---- staying awake and online ----

    private fun acquireLocks() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DriveAdmin:server")
            .apply { setReferenceCounted(false); acquire() }
        @Suppress("DEPRECATION") // still the only way to keep Wi-Fi fully up with the screen off
        wifiLock = getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "DriveAdmin:server")
            .apply { setReferenceCounted(false); acquire() }
    }

    /** Reconnects the tunnel straight away when the phone switches networks (Wi-Fi <-> mobile). */
    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null
            private var first = true

            override fun onAvailable(network: Network) {
                val changed = !first && network != current
                first = false
                current = network
                if (changed) scope.launch(serial) {
                    delay(2_000) // let routes and DNS settle
                    supervisor?.restart(CLOUDFLARED)
                }
            }
        }
        cm.registerDefaultNetworkCallback(cb)
        networkCallback = cb
    }
}

/** Brings the built-in server back after a reboot or an app update, if it was running. */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val s = ServerSettings(ctx)
        if (s.mode == ServerMode.BUILT_IN && s.setupDone && s.shouldRun && s.startOnBoot) {
            ServerController.start(ctx)
        }
    }
}
