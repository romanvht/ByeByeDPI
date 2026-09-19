package io.github.romanvht.byedpi.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.*
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.activities.TestActivity
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.PrivateDnsState
import io.github.romanvht.byedpi.utility.*
import androidx.core.net.toUri

class MainSettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private val TAG: String = MainSettingsFragment::class.java.simpleName
    }

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updatePreferences()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_settings, rootKey)

        setEditTextPreferenceListener("byedpi_proxy_ip") { checkIp(it) }
        setEditTestPreferenceListenerPort("byedpi_proxy_port")
        setEditTextPreferenceListener("dns_ip") { it.isBlank() || checkNotLocalIp(it) }

        findPreferenceNotNull<ListPreference>("language")
            .setOnPreferenceChangeListener { _, newValue ->
                SettingsUtils.setLang(newValue as String)
                true
            }

        findPreferenceNotNull<ListPreference>("app_theme")
            .setOnPreferenceChangeListener { _, newValue ->
                SettingsUtils.setTheme(newValue as String)
                true
            }

        findPreferenceNotNull<Preference>("proxy_test")
            .setOnPreferenceClickListener {
                val intent = Intent(context, TestActivity::class.java)
                startActivity(intent)
                true
            }

        findPreferenceNotNull<Preference>("battery_optimization")
            .setOnPreferenceClickListener {
                PermissionUtils.requestBatteryOptimization(requireContext())
                true
            }

        findPreferenceNotNull<Preference>("storage_access")
            .setOnPreferenceClickListener {
                PermissionUtils.requestStorageAccess(this)
                true
            }

        findPreferenceNotNull<Preference>("donate")
            .setOnPreferenceClickListener {
                showDonateDialog()
                true
            }

        findPreferenceNotNull<Preference>("dns_ip_picker")
            .setOnPreferenceClickListener {
                if (PrivateDnsUtils.getState(requireContext()) is PrivateDnsState.Configured) {
                    updatePreferences()
                    PrivateDnsUtils.openSettings(requireContext())
                } else {
                    showDnsDialog()
                }
                true
            }

        findPreferenceNotNull<Preference>("private_dns")
            .setOnPreferenceClickListener {
                PrivateDnsUtils.openSettings(requireContext())
                true
            }

        findPreferenceNotNull<Preference>("version").summary = BuildConfig.VERSION_NAME
        findPreferenceNotNull<Preference>("byedpi_version").summary = "0.17.3 (ba53229)"

        updatePreferences()
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
        updatePreferences()
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updatePreferences() {
        val cmdEnable = findPreferenceNotNull<SwitchPreference>("byedpi_enable_cmd_settings").isChecked
        val mode = findPreferenceNotNull<ListPreference>("byedpi_mode").value.let { Mode.fromString(it) }
        val dns = findPreferenceNotNull<Preference>("dns_ip_picker")
        val dnsEdit = findPreferenceNotNull<EditTextPreference>("dns_ip")
        val privateDns = findPreferenceNotNull<Preference>("private_dns")
        val privateDnsState = PrivateDnsUtils.getState(requireContext())
        val ipv6 = findPreferenceNotNull<SwitchPreference>("ipv6_enable")
        val proxy = findPreferenceNotNull<PreferenceCategory>("byedpi_proxy_category")

        val applistType = findPreferenceNotNull<ListPreference>("applist_type")
        val selectedApps = findPreferenceNotNull<Preference>("selected_apps")
        val batteryOptimization = findPreferenceNotNull<Preference>("battery_optimization")
        val storageAccess = findPreferenceNotNull<Preference>("storage_access")

        val uiSettings = findPreferenceNotNull<Preference>("byedpi_ui_settings")
        val cmdSettings = findPreferenceNotNull<Preference>("byedpi_cmd_settings")
        val proxyTest = findPreferenceNotNull<Preference>("proxy_test")

        if (cmdEnable) {
            val (cmdIp, cmdPort) = sharedPreferences?.checkIpAndPortInCmd() ?: Pair(null, null)
            proxy.isVisible = cmdIp == null && cmdPort == null
        } else {
            proxy.isVisible = true
        }

        uiSettings.isEnabled = !cmdEnable
        cmdSettings.isEnabled = cmdEnable
        proxyTest.isEnabled = cmdEnable

        dns.summary = dnsSummary()
        dnsEdit.isVisible = false
        privateDns.summary = (privateDnsState as? PrivateDnsState.Configured)?.hostname
        privateDns.isVisible = mode == Mode.VPN && privateDnsState is PrivateDnsState.Configured

        when (mode) {
            Mode.VPN -> {
                dns.isVisible = privateDnsState !is PrivateDnsState.Configured
                ipv6.isVisible = true

                when (applistType.value) {
                    "disable" -> {
                        applistType.isVisible = true
                        selectedApps.isVisible = false
                    }
                    "blacklist", "whitelist" -> {
                        applistType.isVisible = true
                        selectedApps.isVisible = true
                    }
                    else -> {
                        applistType.isVisible = true
                        selectedApps.isVisible = false
                        Log.w(TAG, "Unexpected applistType value: ${applistType.value}")
                    }
                }
            }

            Mode.Proxy -> {
                dns.isVisible = false
                ipv6.isVisible = false
                applistType.isVisible = false
                selectedApps.isVisible = false
            }
        }

        if (PermissionUtils.isBatteryOptimizationDisabled(requireContext())) {
            batteryOptimization.summary = getString(R.string.battery_optimization_disabled_summary)
        } else {
            batteryOptimization.summary = getString(R.string.battery_optimization_summary)
        }

        if (PermissionUtils.hasStorageAccess(requireContext())) {
            storageAccess.summary = getString(R.string.storage_access_allowed_summary)
        } else {
            storageAccess.summary = getString(R.string.storage_access_summary)
        }
    }

    private val dnsPresets: List<Pair<String, String>>
        get() = listOf(
            "" to getString(R.string.dns_system),
            "8.8.8.8" to getString(R.string.dns_google),
            "1.1.1.1" to getString(R.string.dns_cloudflare),
            "9.9.9.9" to getString(R.string.dns_quad9),
            "77.88.8.8" to getString(R.string.dns_yandex),
        )

    private fun dnsSummary(): String {
        val ip = sharedPreferences?.getStringNotNull("dns_ip", "1.1.1.1") ?: "1.1.1.1"
        val preset = dnsPresets.firstOrNull { it.first == ip } ?: return ip
        return if (preset.first.isBlank()) preset.second else "${preset.second} ($ip)"
    }

    private fun showDnsDialog() {
        val context = requireContext()
        val presets = dnsPresets

        val items = (presets.map { it.second } + getString(R.string.dns_custom)).toTypedArray()
        val dnsPref = findPreferenceNotNull<EditTextPreference>("dns_ip")

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dbs_ip_setting))
            .setItems(items) { _, which ->
                if (which < presets.size) {
                    sharedPreferences?.edit { putString("dns_ip", presets[which].first) }
                } else {
                    onDisplayPreferenceDialog(dnsPref)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDonateDialog() {
        val context = requireContext()

        val items = arrayOf(
            getString(R.string.donate_message),
            getString(R.string.donate_cloudtips),
            getString(R.string.donate_boosty),
            getString(R.string.donate_telegram_bot),
            getString(R.string.donate_thanks),
        )

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.donate))
            .setItems(items) { _, which ->
                when (which) {
                    1 -> openUrl("https://pay.cloudtips.ru/p/92c754db")
                    2 -> openUrl("https://boosty.to/romanvht/donate")
                    3 -> openUrl("https://t.me/romanvht_donate_bot")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }
}
