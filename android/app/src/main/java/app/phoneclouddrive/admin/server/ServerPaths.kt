package app.phoneclouddrive.admin.server

import android.content.Context
import java.io.File

/**
 * On-device layout of the built-in server (all private to the app):
 *
 *   files/sftpgo/        sftpgo.db, signing.key  (config dir = data dir)
 *   files/sftpgo-ui/     templates/, static/, VERSION  (from assets/sftpgo-ui.zip)
 *   files/logs/          one log per process, rotated
 *   cache/               TMPDIR for the processes
 *
 * The programs themselves are lib*.so files in nativeLibraryDir (see native/build.sh).
 */
class ServerPaths(ctx: Context) {
    private val libDir = File(ctx.applicationInfo.nativeLibraryDir)
    val data = File(ctx.filesDir, "sftpgo")
    val ui = File(ctx.filesDir, "sftpgo-ui")
    val logs = File(ctx.filesDir, "logs")
    val tmp: File = ctx.cacheDir
    val database = File(data, "sftpgo.db")
    val signingKey = File(data, "signing.key")

    val sftpgo = File(libDir, "libsftpgo.so")
    val cloudflared = File(libDir, "libcloudflared.so")

    fun ensure() {
        listOf(data, logs, tmp).forEach { it.mkdirs() }
    }

    /** Base environment for every process. */
    fun env(): Map<String, String> = mapOf(
        "HOME" to data.path,
        "TMPDIR" to tmp.path,
        "PATH" to "/system/bin",
        "LANG" to "C.UTF-8",
    )
}
