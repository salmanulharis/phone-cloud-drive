package app.phoneclouddrive.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.storage.StorageManager
import android.provider.Settings
import java.io.File
import java.net.Inet4Address

/** A place the drive folder can live: internal storage, an SD card or a USB drive. */
data class DriveVolume(val label: String, val root: File, val removable: Boolean) {
    val drivePath: String get() = File(root, ".CloudDrive").path
    val free: Long get() = root.usableSpace
}

/** Phone settings the built-in server depends on, and shortcuts to fix them. */
object PhoneSetup {
    fun hasFilesAccess(ctx: Context) = FileOps.hasAccess(ctx)

    fun filesAccessIntent(ctx: Context) = FileOps.accessSettingsIntent(ctx)

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** Asks directly to run unrestricted in the background (needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). */
    @SuppressLint("BatteryLife")
    fun batteryIntent(ctx: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))

    fun notificationsAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun appSettingsIntent(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Mounted volumes the drive can use (secondary volumes need Android 11+ for direct paths). */
    fun volumes(ctx: Context): List<DriveVolume> {
        val sm = ctx.getSystemService(StorageManager::class.java)
        return sm.storageVolumes.mapNotNull { v ->
            if (v.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
            val root = when {
                Build.VERSION.SDK_INT >= 30 -> v.directory
                v.isPrimary -> Environment.getExternalStorageDirectory()
                else -> null
            } ?: return@mapNotNull null
            DriveVolume(v.getDescription(ctx), root, v.isRemovable)
        }
    }

    /** This phone's IPv4 address on the current network (for home Wi-Fi links), or null. */
    fun lanAddress(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }
}
