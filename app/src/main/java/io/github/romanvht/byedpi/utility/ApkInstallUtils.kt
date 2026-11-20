package io.github.romanvht.byedpi.utility

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.github.romanvht.byedpi.receiver.InstallResultReceiver
import java.io.File
import java.security.MessageDigest

/** Installing, finding and opening other apps. */
object ApkInstallUtils {
    private const val TAG = "ApkInstall"

    /**
     * Where downloaded APK files are kept. Android 7+ installs them from the app's private cache
     * through a content link; older versions need a file the installer can read.
     */
    fun apkDir(context: Context): File? {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) context.cacheDir else context.externalCacheDir
        return base?.let { File(it, "apks") }
    }

    /** On Android 8+ the user has to allow each app to install other apps. */
    fun canInstallApps(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${activity.packageName}".toUri())
        return start(activity, intent)
    }

    /** Opens Android's installer for the file. Android asks the user before installing. */
    fun install(activity: Activity, file: File): Boolean {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                FileProvider.getUriForFile(activity, "${activity.packageName}.apks", file)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "File is outside the shared folder", e)
                return false
            }
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(activity, intent)
    }

    /** An APK file's package name and the SHA-256 fingerprints of the certificates it is signed with. */
    class ArchiveInfo(val packageName: String, val fingerprints: Set<String>)

    /**
     * Reads an APK file. Android checks the signatures while reading it, so the fingerprints belong
     * to keys that really signed the file. With key rotation, the earlier keys are included too.
     */
    fun archiveInfo(context: Context, file: File): ArchiveInfo? = try {
        val manager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9 and 10 read the certificates of an APK file only when GET_SIGNATURES is set too
            @Suppress("DEPRECATION")
            val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageArchiveInfo(file.path, flags)
            }
            val signing = info?.signingInfo
            val certificates = when {
                signing == null -> emptyArray()
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory
            }
            info?.packageName?.let { ArchiveInfo(it, certificates.orEmpty().map { cert -> sha256(cert.toByteArray()) }.toSet()) }
        } else {
            @Suppress("DEPRECATION")
            val info = manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val certificates = info?.signatures.orEmpty()
            info?.packageName?.let { ArchiveInfo(it, certificates.map { cert -> sha256(cert.toByteArray()) }.toSet()) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the APK file", e)
        null
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Installs an app made of several APK files (a split bundle). Android asks the user to confirm;
     * [InstallResultReceiver] gets the result. Blocking: call it off the main thread.
     */
    fun installSession(context: Context, appId: String, packageName: String, files: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(files.sumOf { it.length() })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                files.forEachIndexed { index, file ->
                    file.inputStream().use { input ->
                        session.openWrite("$index-${file.name}", 0, file.length()).use { output ->
                            input.copyTo(output, 64 * 1024)
                            session.fsync(output)
                        }
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION)
                    .putExtra(InstallResultReceiver.EXTRA_APP_ID, appId)
                // The installer adds the result to this intent, so it has to be mutable on Android 12+
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw e
        }
    }

    /** Starts the confirmation screen that Android shows before installing. */
    fun confirmInstall(activity: Activity, confirmation: Intent): Boolean = start(activity, confirmation)

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** The first of the package names that is installed. */
    fun installedPackage(context: Context, packages: List<String>): String? =
        packages.firstOrNull { isInstalled(context, it) }

    fun launch(activity: Activity, packageName: String): Boolean {
        val manager = activity.packageManager
        val intent = manager.getLaunchIntentForPackage(packageName)
            ?: manager.getLeanbackLaunchIntentForPackage(packageName)
            ?: return false
        return start(activity, intent)
    }

    /** Opens the app's page in Google Play. */
    fun openStorePage(activity: Activity, packageName: String): Boolean {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        if (start(activity, market.setPackage("com.android.vending"))) return true
        return start(activity, Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
    }

    fun openWebsite(activity: Activity, url: String): Boolean =
        start(activity, Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun start(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app for ${intent.data}", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to open ${intent.data}", e)
        false
    }
}
