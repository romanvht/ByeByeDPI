package io.github.romanvht.byedpi.utility

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment

object PermissionUtils {
    private const val TAG = "PermissionUtils"
    private const val STORAGE_PERMISSION_REQUEST = 1001

    private val legacyStoragePermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasVpnPermission(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    fun requestNotificationPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = "package:${context.packageName}".toUri()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request ignore battery optimizations", e)
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Failed to open battery optimization settings", fallbackException)
            }
        }
    }

    fun hasStorageAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }

        for (permission in legacyStoragePermissions) {
            val result = ContextCompat.checkSelfPermission(context, permission)
            if (result != PackageManager.PERMISSION_GRANTED) return false
        }

        return true
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun storageAccessIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = "package:${context.packageName}".toUri()
        return intent
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun storageAccessFallbackIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    fun getLegacyStoragePermissions(): Array<String> {
        return legacyStoragePermissions.copyOf()
    }

    fun requestStorageAccess(fragment: Fragment) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                fragment.startActivity(storageAccessIntent(fragment.requireContext()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request storage access", e)
                try {
                    fragment.startActivity(storageAccessFallbackIntent())
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "Failed to open storage settings", fallbackException)
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                fragment.requireActivity(),
                getLegacyStoragePermissions(),
                STORAGE_PERMISSION_REQUEST
            )
        }
    }
}
