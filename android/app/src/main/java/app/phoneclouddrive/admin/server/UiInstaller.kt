package app.phoneclouddrive.admin.server

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Installs the web UI (SFTPGo's templates/static with this project's patches and theme, packed
 * by native/build.sh into assets/sftpgo-ui.zip) into [ServerPaths.ui].
 *
 * It only re-extracts when the bundled VERSION differs from the installed one, and swaps the new
 * copy in only after a complete extraction, so an interrupted update never leaves a broken UI.
 */
object UiInstaller {
    private const val ASSET = "sftpgo-ui.zip"
    private const val VERSION = "VERSION"

    @Synchronized
    fun ensure(ctx: Context, paths: ServerPaths) {
        val bundled = bundledVersion(ctx)
        val installed = File(paths.ui, VERSION).takeIf { it.isFile }?.readText()?.trim()
        if (bundled == installed && File(paths.ui, "templates").isDirectory) return

        val parent = paths.ui.parentFile!!
        val staging = File(parent, "sftpgo-ui.new")
        val old = File(parent, "sftpgo-ui.old")
        staging.deleteRecursively()
        old.deleteRecursively()
        extract(ctx, staging)
        if (!File(staging, "templates").isDirectory) throw IOException("The bundled web UI is incomplete")

        if (paths.ui.exists() && !paths.ui.renameTo(old)) throw IOException("Couldn't replace the web UI")
        if (!staging.renameTo(paths.ui)) {
            old.renameTo(paths.ui)
            throw IOException("Couldn't install the web UI")
        }
        old.deleteRecursively()
    }

    private fun bundledVersion(ctx: Context): String? =
        ZipInputStream(ctx.assets.open(ASSET).buffered()).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name.trimStart('.', '/') == VERSION }
                ?.let { zip.bufferedReader().readText().trim() }
        }

    private fun extract(ctx: Context, dest: File) {
        val root = dest.canonicalFile
        ZipInputStream(ctx.assets.open(ASSET).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(root, entry.name).canonicalFile
                // Zip-slip guard: every entry must stay inside the destination.
                if (out != root && !out.path.startsWith(root.path + File.separator)) {
                    throw IOException("Bad entry in web UI archive: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }
}
