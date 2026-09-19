package io.github.romanvht.byedpi.utility

import android.content.Context
import android.os.Build
import android.system.Os
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.core.ByeDpiProxyUIPreferences
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.PrivateDnsState

object DiagnosticUtils {
    fun buildReport(context: Context): String {
        val preferences = context.getPreferences()
        val mode = preferences.mode()
        val privateDnsState = PrivateDnsUtils.getState(context)
        val cmdEnabled = preferences.getCmdEnable()
        val strategy = if (cmdEnabled) {
            preferences.getCmdArgs()
        } else {
            ByeDpiProxyUIPreferences(preferences).uiargs.drop(1).joinToString(" ")
        }
        val (ip, port) = preferences.getProxyIpAndPort()
        val activeLists = DomainListUtils.getLists(context)
            .filter { it.isActive }
            .map { it.name }
            .sortedBy { it.lowercase() }
            .joinToString(", ")
            .ifEmpty { context.getString(R.string.diagnostic_none) }

        return buildString {
            appendLine(value(context, R.string.diagnostic_device, deviceName()))
            appendLine(value(
                context,
                R.string.diagnostic_android_version,
                "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            ))
            appendLine(value(context, R.string.diagnostic_kernel_version, Os.uname().release))
            appendLine(value(context, R.string.diagnostic_app_version, BuildConfig.VERSION_NAME))

            appendLine()
            appendLine(context.getString(R.string.diagnostic_app_settings))
            appendLine(value(context, R.string.diagnostic_command_line, state(context, cmdEnabled)))
            appendLine(value(context, R.string.diagnostic_strategy, strategy))
            appendLine(value(context, R.string.mode_setting, modeName(context, mode)))
            appendLine(value(context, R.string.private_dns, privateDnsDescription(context, privateDnsState)))
            if (mode == Mode.VPN) {
                if (privateDnsState !is PrivateDnsState.Configured) {
                    val dns = preferences.getStringNotNull("dns_ip", "1.1.1.1").ifBlank { context.getString(R.string.dns_system) }
                    appendLine(value(
                        context,
                        R.string.dbs_ip_setting,
                        dns
                    ))
                }
                appendLine(value(
                    context,
                    R.string.ipv6_setting,
                    state(context, preferences.getBoolean("ipv6_enable", false))
                ))
                appendLine(value(
                    context,
                    R.string.applist_setting,
                    appFilter(context, preferences.getStringNotNull("applist_type", "disable"))
                ))
            }
            appendLine(value(context, R.string.bye_dpi_proxy_ip_setting, ip))
            appendLine(value(context, R.string.byedpi_proxy_port_setting, port))

            appendLine()
            appendLine(context.getString(R.string.permission_category))
            appendLine(value(
                context,
                R.string.diagnostic_vpn_permission,
                context.getString(
                    if (PermissionUtils.hasVpnPermission(context)) R.string.diagnostic_vpn_allowed
                    else R.string.diagnostic_vpn_denied
                )
            ))
            appendLine(value(
                context,
                R.string.diagnostic_battery_exclusion,
                availability(context, PermissionUtils.isBatteryOptimizationDisabled(context))
            ))
            appendLine(value(
                context,
                R.string.storage_access,
                availability(context, PermissionUtils.hasStorageAccess(context))
            ))

            appendLine()
            appendLine(context.getString(R.string.diagnostic_test_settings))
            appendLine(value(
                context,
                R.string.test_delay,
                "${preferences.getIntStringNotNull("byedpi_proxytest_delay", 1)}${context.getString(R.string.diagnostic_seconds)}"
            ))
            appendLine(value(
                context,
                R.string.test_requests,
                preferences.getIntStringNotNull("byedpi_proxytest_requests", 1).toString()
            ))
            appendLine(value(
                context,
                R.string.test_requests_limit,
                preferences.getIntStringNotNull("byedpi_proxytest_limit", 20).toString()
            ))
            appendLine(value(
                context,
                R.string.test_timeout,
                "${preferences.getLongStringNotNull("byedpi_proxytest_timeout", 5)}${context.getString(R.string.diagnostic_seconds)}"
            ))
            appendLine(value(
                context,
                R.string.test_settings_sni,
                preferences.getStringNotNull("byedpi_proxytest_sni", "google.com")
            ))
            appendLine(value(
                context,
                R.string.test_settings_usercommands,
                yesNo(context, preferences.getBoolean("byedpi_proxytest_usercommands", false))
            ))
            append(value(context, R.string.domain_lists, activeLists))
        }
    }

    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(" ")

    private fun privateDnsDescription(context: Context, state: PrivateDnsState): String = when (state) {
        is PrivateDnsState.Configured -> state.hostname
        PrivateDnsState.Inactive -> context.getString(R.string.private_dns_inactive)
        PrivateDnsState.Unknown -> context.getString(R.string.private_dns_unknown)
        PrivateDnsState.Unsupported -> context.getString(R.string.private_dns_unsupported)
    }

    private fun value(context: Context, label: Int, value: String): String =
        "${context.getString(label)}: $value"

    private fun state(context: Context, enabled: Boolean): String = context.getString(
        if (enabled) R.string.diagnostic_enabled else R.string.diagnostic_disabled
    )

    private fun yesNo(context: Context, value: Boolean): String = context.getString(
        if (value) R.string.diagnostic_yes else R.string.diagnostic_no
    )

    private fun availability(context: Context, available: Boolean): String = context.getString(
        if (available) R.string.diagnostic_available else R.string.diagnostic_unavailable
    )

    private fun modeName(context: Context, mode: Mode): String = when (mode) {
        Mode.VPN -> "VPN"
        Mode.Proxy -> context.getString(R.string.diagnostic_proxy)
    }

    private fun appFilter(context: Context, value: String): String {
        val values = context.resources.getStringArray(R.array.applist_types_entries)
        val names = context.resources.getStringArray(R.array.applist_types)
        return names.getOrElse(values.indexOf(value)) { value }
    }
}
