package app.phoneclouddrive.admin

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class Kind { FOLDER, IMAGE, VIDEO, AUDIO, OTHER }

data class DriveEntry(val file: File, val kind: Kind, val size: Long, val modified: Long, val childCount: Int)

object FileOps {
    /** Where SFTPGo keeps the drive. Matches SFTPGO_DATA_PROVIDER__USERS_BASE_DIR on the server. */
    const val DRIVE_DIR = ".CloudDrive"

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Android 10 has no "All files access" screen; it uses the runtime storage permissions instead. */
    val legacyStoragePermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    val usesLegacyStorage get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    fun accessSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    /** SFTPGo stores "/sdcard/..."; map it onto this device's primary storage path. */
    fun resolveHome(homeDir: String): File {
        val ext = Environment.getExternalStorageDirectory().path
        val p = homeDir
            .replaceFirst(Regex("^/sdcard(?=/|$)"), ext)
            .replaceFirst(Regex("^/storage/self/primary(?=/|$)"), ext)
        return File(p)
    }

    fun mimeOf(f: File): String {
        val ext = f.extension.lowercase(Locale.US)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun kindOf(f: File): Kind {
        if (f.isDirectory) return Kind.FOLDER
        val m = mimeOf(f)
        return when {
            m.startsWith("image/") -> Kind.IMAGE
            m.startsWith("video/") -> Kind.VIDEO
            m.startsWith("audio/") -> Kind.AUDIO
            else -> Kind.OTHER
        }
    }

    suspend fun list(dir: File): List<DriveEntry> = withContext(Dispatchers.IO) {
        (dir.listFiles() ?: emptyArray())
            .filter { it.name != ".nomedia" }
            .map {
                DriveEntry(
                    file = it,
                    kind = kindOf(it),
                    size = if (it.isFile) it.length() else 0,
                    modified = it.lastModified(),
                    childCount = if (it.isDirectory) (it.list()?.count { n -> n != ".nomedia" } ?: 0) else 0
                )
            }
            .sortedWith(compareBy<DriveEntry> { it.kind != Kind.FOLDER }.thenBy { it.file.name.lowercase() })
    }

    // ---- thumbnails ----

    private val thumbs = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun thumbnail(f: File, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${f.path}:${f.lastModified()}:$px"
        thumbs.get(key)?.let { return@withContext it }
        val bmp = try {
            when (kindOf(f)) {
                Kind.IMAGE -> decodeSampled(f, px)
                Kind.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ThumbnailUtils.createVideoThumbnail(f, Size(px, px), null) else null
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        bmp?.also { thumbs.put(key, it) }
    }

    private fun decodeSampled(f: File, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    // ---- actions ----

    /** Hand one file to a viewer app via a temporary content:// grant; nothing becomes public. */
    fun open(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(f))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(view, "Open ${f.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Copy a file out of the private drive into a public folder (Pictures/Movies/Music/Download, subfolder "Drive"),
     * so it shows up in Gallery and other apps. Returns the folder it was saved to.
     */
    suspend fun saveToPublic(ctx: Context, f: File): String = withContext(Dispatchers.IO) {
        val kind = kindOf(f)
        val (dir, collection) = when (kind) {
            Kind.IMAGE -> Environment.DIRECTORY_PICTURES to imagesUri()
            Kind.VIDEO -> Environment.DIRECTORY_MOVIES to videoUri()
            Kind.AUDIO -> Environment.DIRECTORY_MUSIC to audioUri()
            else -> Environment.DIRECTORY_DOWNLOADS to downloadsUri()
        }
        val target = "$dir/Drive"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && collection != null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(f))
                put(MediaStore.MediaColumns.RELATIVE_PATH, target)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(collection, values) ?: error("Couldn't create the public copy")
            resolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val outDir = File(Environment.getExternalStoragePublicDirectory(dir), "Drive").apply { mkdirs() }
            val out = uniqueFile(outDir, f.name)
            f.copyTo(out)
            MediaScannerConnection.scanFile(ctx, arrayOf(out.path), null, null)
        }
        target
    }

    suspend fun delete(f: File): Boolean = withContext(Dispatchers.IO) { f.deleteRecursively() }

    private fun uniqueFile(dir: File, name: String): File {
        var out = File(dir, name)
        var i = 1
        while (out.exists()) out = File(dir, "${name.substringBeforeLast('.')} ($i).${name.substringAfterLast('.', "")}".trimEnd('.')).also { i++ }
        return out
    }

    private fun imagesUri(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else null
    private fun videoUri(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else null
    private fun audioUri(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else null
    private fun downloadsUri(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else null
}
