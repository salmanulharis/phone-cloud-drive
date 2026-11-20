package app.phoneclouddrive.admin.server

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Live state of the built-in server, published by [ServerService] for the UI and notification. */
data class ServerState(
    /** The foreground service is up (it may still be starting the processes). */
    val active: Boolean = false,
    val processes: List<ProcStatus> = emptyList(),
    /** SFTPGo answers its health check. */
    val driveHealthy: Boolean = false,
    /** Whether a Cloudflare tunnel is configured, and how many edge connections it has. */
    val tunnelConfigured: Boolean = false,
    val tunnelConnections: Int = 0,
    /** A problem that stops the server from starting at all (missing files, bad setup). */
    val fatal: String? = null,
) {
    val tunnelUp get() = tunnelConnections > 0
    fun process(name: String) = processes.firstOrNull { it.name == name }
}

/** Entry point for starting, stopping and reconfiguring the built-in server. */
object ServerController {
    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    internal fun update(f: (ServerState) -> ServerState) = _state.update(f)

    /** Starts the server (or applies new settings if it's running) and remembers to run after reboots. */
    fun start(ctx: Context) {
        ServerSettings(ctx).shouldRun = true
        ContextCompat.startForegroundService(ctx, intent(ctx, ServerService.ACTION_START))
    }

    fun stop(ctx: Context) {
        ServerSettings(ctx).shouldRun = false
        if (state.value.active) ctx.startService(intent(ctx, ServerService.ACTION_STOP))
    }

    /** Re-reads settings (LAN access, storage, tunnel) and restarts the processes, if running. */
    fun applySettings(ctx: Context) {
        if (state.value.active) ctx.startService(intent(ctx, ServerService.ACTION_RELAUNCH))
    }

    /** Invalidates every web login by replacing the session-signing key. */
    fun signOutEverywhere(ctx: Context) {
        if (state.value.active) ctx.startService(intent(ctx, ServerService.ACTION_ROTATE_KEY))
        else ServerService.writeSigningKey(ServerPaths(ctx))
    }

    /**
     * Recreates the app's admin account with a new password. Used automatically when the stored
     * password no longer works (e.g. the Keystore key was reset). Users and files are untouched.
     */
    fun resetAdmin(ctx: Context) {
        ServerSettings(ctx).shouldRun = true
        ContextCompat.startForegroundService(ctx, intent(ctx, ServerService.ACTION_RESET_ADMIN))
    }

    fun restartTunnel(ctx: Context) {
        if (state.value.active) ctx.startService(intent(ctx, ServerService.ACTION_RESTART_TUNNEL))
    }

    /** Recent output of one process, for the logs screen. */
    fun log(name: String): List<String> = ServerService.instance?.log(name).orEmpty()

    private fun intent(ctx: Context, action: String) = Intent(ctx, ServerService::class.java).setAction(action)
}
