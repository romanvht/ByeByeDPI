package io.github.romanvht.byedpi.utility

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.PrivateDnsState

object PrivateDnsUtils {
    private const val TAG = "PrivateDnsUtils"

    fun getState(context: Context): PrivateDnsState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return PrivateDnsState.Unsupported

        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return PrivateDnsState.Unknown
            val properties = manager.getLinkProperties(network) ?: return PrivateDnsState.Unknown
            val hostname = properties.privateDnsServerName
            if (hostname.isNullOrBlank()) PrivateDnsState.Inactive
            else PrivateDnsState.Configured(hostname)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to read private DNS state", e)
            PrivateDnsState.Unknown
        }
    }

    fun openSettings(context: Context) {
        Toast.makeText(context, R.string.private_dns_settings_message, Toast.LENGTH_LONG).show()

        for (action in listOf(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                context.startActivity(Intent(action))
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Settings activity not found: $action", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Settings activity not accessible: $action", e)
            }
        }

        Toast.makeText(context, R.string.private_dns_settings_unavailable, Toast.LENGTH_LONG).show()
    }
}
