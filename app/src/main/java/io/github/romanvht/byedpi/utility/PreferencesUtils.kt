package io.github.romanvht.byedpi.utility

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import io.github.romanvht.byedpi.data.Mode

private const val DEFAULT_CMD_ARGS = "-o1 -a1 -r-5+se"

val PreferenceFragmentCompat.sharedPreferences
    get() = preferenceScreen.sharedPreferences

fun Context.getPreferences(): SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(this)

fun SharedPreferences.getIntStringNotNull(key: String, defValue: Int): Int =
    getString(key, defValue.toString())?.toIntOrNull() ?: defValue

fun SharedPreferences.getLongStringNotNull(key: String, defValue: Long): Long =
    getString(key, defValue.toString())?.toLongOrNull() ?: defValue

fun SharedPreferences.getStringNotNull(key: String, defValue: String): String =
    getString(key, defValue) ?: defValue

fun SharedPreferences.mode(): Mode =
    Mode.fromStringOrNull(getStringNotNull("byedpi_mode", "vpn")) ?: Mode.VPN

fun <T : Preference> PreferenceFragmentCompat.findPreferenceNotNull(key: CharSequence): T =
    findPreference(key) ?: throw IllegalStateException("Preference $key not found")

fun SharedPreferences.getSelectedApps(): List<String> {
    return getStringSet("selected_apps", emptySet())?.toList() ?: emptyList()
}

fun SharedPreferences.getCmdEnable(): Boolean {
    return getBoolean("byedpi_enable_cmd_settings", true)
}

fun SharedPreferences.getCmdArgs(): String {
    return getStringNotNull("byedpi_cmd_args", DEFAULT_CMD_ARGS)
}

fun SharedPreferences.checkIpAndPortInCmd(): Pair<String?, String?> {
    if (!getCmdEnable()) return Pair(null, null)
    return shellSplit(getCmdArgs()).checkIpAndPortInArgs()
}

fun List<String>.checkIpAndPortInArgs(): Pair<String?, String?> {
    val flags = setOf(
        "--daemon", "--no-domain", "--no-ipv6", "--no-udp", "--help", "--version",
        "--transparent", "--tfo", "--md5sig", "--wait-send", "--drop-sack",
    )
    var host: String? = null
    var port: String? = null
    var index = 0
    while (index < size) {
        val arg = this[index++]
        if (arg == "--") break
        if (arg.startsWith("--")) {
            val key = arg.substringBefore('=')
            if (flags.any { it.startsWith(key) }) continue
            val value = if ('=' in arg) arg.substringAfter('=') else getOrNull(index++)
            when (key) {
                "--ip" -> host = value
                "--po", "--por", "--port" -> port = value
            }
        } else if (arg.startsWith("-")) {
            for (position in 1 until arg.length) {
                val key = arg[position]
                if (key in "DNXUhvEFSZY") continue
                val value = arg.substring(position + 1).ifEmpty { getOrNull(index++) }
                when (key) {
                    'i' -> host = value
                    'p' -> port = value
                }
                break
            }
        }
    }
    return host to port
}


fun SharedPreferences.getProxyIpAndPort(): Pair<String, String> {
    val (cmdIp, cmdPort) = checkIpAndPortInCmd()

    val ip = cmdIp ?: getStringNotNull("byedpi_proxy_ip", "127.0.0.1")
    val port = cmdPort ?: getStringNotNull("byedpi_proxy_port", "1080")

    return Pair(ip, port)
}
