package io.github.dovecoteescapee.byedpi.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

object AppsCache {
    private var cachedApps: List<AppInfo>? = null
    private var lastSelectedApps: Set<String> = emptySet()
    private var isLoading = false

    suspend fun loadApps(context: Context, selectedApps: Set<String> = emptySet()): List<AppInfo> {
        if (cachedApps != null) {
            return cachedApps!!.map { app ->
                app.copy(isSelected = selectedApps.contains(app.packageName))
            }.sortedWith(compareBy({ !it.isSelected }, { it.appName.lowercase() }))
        }

        while (isLoading) {
            kotlinx.coroutines.delay(10)
        }

        if (cachedApps != null) {
            return cachedApps!!.map { app ->
                app.copy(isSelected = selectedApps.contains(app.packageName))
            }.sortedWith(compareBy({ !it.isSelected }, { it.appName.lowercase() }))
        }

        isLoading = true
        lastSelectedApps = selectedApps

        return try {
            val apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                loadInstalledApps(context, selectedApps)
            }
            cachedApps = apps
            apps
        } finally {
            isLoading = false
        }
    }

    suspend fun preloadApps(context: Context) {
        if (cachedApps == null && !isLoading) {
            loadApps(context)
        }
    }

    private fun loadInstalledApps(
        context: Context,
        selectedApps: Set<String>
    ): List<AppInfo> {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(0)

        return installedApps
            .filter { it.packageName != context.packageName }
            .map { createAppInfo(it, pm, selectedApps) }
            .sortedWith(compareBy({ !it.isSelected }, { it.appName.lowercase() }))
    }

    private fun createAppInfo(
        appInfo: ApplicationInfo,
        pm: PackageManager,
        selectedApps: Set<String>
    ): AppInfo {
        val appName = try {
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            appInfo.packageName
        }

        val isSelected = selectedApps.contains(appInfo.packageName)

        return AppInfo(
            appName = appName,
            packageName = appInfo.packageName,
            icon = null,
            isSelected = isSelected
        )
    }

    fun loadAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            context.packageManager.defaultActivityIcon
        }
    }
}