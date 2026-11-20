package io.github.romanvht.byedpi.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import io.github.romanvht.byedpi.utility.AppDownloads

/** Gets the result of installing a split bundle (see ApkInstallUtils.installSession). */
class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "InstallResult"
        const val ACTION = "io.github.romanvht.byedpi.INSTALL_RESULT"
        const val EXTRA_APP_ID = "app_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        Log.i(TAG, "Install of $appId: status $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation != null) {
                    AppDownloads.onConfirmationNeeded(appId, confirmation)
                } else {
                    AppDownloads.onInstallFailed(appId, AppDownloads.Reason.InstallFailed)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> AppDownloads.onInstalled(context, appId)
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppDownloads.onInstallCancelled(appId)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> AppDownloads.onInstallFailed(appId, AppDownloads.Reason.InstallConflict)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> AppDownloads.onInstallFailed(appId, AppDownloads.Reason.UnsupportedDevice)
            else -> AppDownloads.onInstallFailed(appId, AppDownloads.Reason.InstallFailed)
        }
    }
}
