package io.github.romanvht.byedpi.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.*
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.SettingsActivity
import io.github.romanvht.byedpi.activities.TestActivity
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.PrivateDnsState
import io.github.romanvht.byedpi.utility.*

class MainSettingsFragment : PreferenceFragmentCompat() {
    companion object {
        const val BYEDPI_VERSION = "0.17.3 (ba53229)"
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

        findPreferenceNotNull<ListPreference>("language").apply {
            entries = (listOf(getString(R.string.language_system)) + SettingsUtils.languages.map { it.second }).toTypedArray()
            entryValues = (listOf("system") + SettingsUtils.languages.map { it.first }).toTypedArray()
            summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
                SettingsUtils.languageName(requireContext(), preference.value ?: "system")
            }
            setOnPreferenceChangeListener { _, newValue ->
                SettingsUtils.setLang(newValue as String)
                true
            }
        }

        findPreferenceNotNull<ListPreference>("app_theme")
            .setOnPreferenceChangeListener { _, newValue ->
                SettingsUtils.setTheme(newValue as String)
                true
            }

        findPreferenceNotNull<SwitchPreference>("dynamic_colors").apply {
            isVisible = DynamicColors.isDynamicColorAvailable()
            setOnPreferenceChangeListener { _, _ ->
                // Apply the new colors once the value is saved
                listView?.post { activity?.recreate() }
                true
            }
        }

        findPreferenceNotNull<ListPreference>("byedpi_mode").summaryProvider =
            Preference.SummaryProvider<ListPreference> { preference ->
                when (Mode.fromStringOrNull(preference.value ?: "vpn") ?: Mode.VPN) {
                    Mode.VPN -> getString(R.string.mode_vpn_summary)
                    Mode.Proxy -> getString(R.string.mode_proxy_settings_summary)
                }
            }

        findPreferenceNotNull<Preference>("proxy_test")
            .setOnPreferenceClickListener {
                startActivity(Intent(context, TestActivity::class.java))
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

        findPreferenceNotNull<Preference>("export_settings").setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.exportSettings()
            true
        }

        findPreferenceNotNull<Preference>("import_settings").setOnPreferenceClickListener {
            (activity as? SettingsActivity)?.importSettings()
            true
        }

        findPreferenceNotNull<Preference>("reset_settings").setOnPreferenceClickListener {
            confirmReset()
            true
        }

        findPreferenceNotNull<Preference>("source_code").setOnPreferenceClickListener {
            openUrl(getString(R.string.source_code_url))
            true
        }

        findPreferenceNotNull<Preference>("documentation").setOnPreferenceClickListener {
            openUrl(getString(R.string.byedpi_docs))
            true
        }

        findPreferenceNotNull<Preference>("credits").setOnPreferenceClickListener {
            showCredits()
            true
        }

        findPreferenceNotNull<Preference>("version").summary = getString(R.string.version_summary, BuildConfig.VERSION_NAME)
        findPreferenceNotNull<Preference>("byedpi_version").summary = BYEDPI_VERSION

        updatePreferences()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.title_settings)
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
        updatePreferences()
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updatePreferences() {
        val context = context ?: return
        val cmdEnable = findPreferenceNotNull<SwitchPreference>("byedpi_enable_cmd_settings").isChecked
        val mode = Mode.fromStringOrNull(findPreferenceNotNull<ListPreference>("byedpi_mode").value ?: "vpn") ?: Mode.VPN
        val dns = findPreferenceNotNull<Preference>("dns_ip_picker")
        val privateDns = findPreferenceNotNull<Preference>("private_dns")
        val privateDnsState = PrivateDnsUtils.getState(context)
        val ipv6 = findPreferenceNotNull<SwitchPreference>("ipv6_enable")
        val proxy = findPreferenceNotNull<PreferenceCategory>("byedpi_proxy_category")

        val applistType = findPreferenceNotNull<ListPreference>("applist_type")
        val selectedApps = findPreferenceNotNull<Preference>("selected_apps")
        val batteryOptimization = findPreferenceNotNull<Preference>("battery_optimization")
        val storageAccess = findPreferenceNotNull<Preference>("storage_access")

        val uiSettings = findPreferenceNotNull<Preference>("byedpi_ui_settings")
        val cmdSettings = findPreferenceNotNull<Preference>("byedpi_cmd_settings")

        // The listening address can also be set in the command line; then these fields are ignored
        if (cmdEnable) {
            val (cmdIp, cmdPort) = sharedPreferences?.checkIpAndPortInCmd() ?: Pair(null, null)
            proxy.isVisible = cmdIp == null && cmdPort == null
        } else {
            proxy.isVisible = true
        }

        uiSettings.isEnabled = !cmdEnable
        cmdSettings.isEnabled = cmdEnable

        dns.summary = dnsSummary()
        privateDns.summary = (privateDnsState as? PrivateDnsState.Configured)?.hostname
        privateDns.isVisible = mode == Mode.VPN && privateDnsState is PrivateDnsState.Configured

        val vpn = mode == Mode.VPN
        dns.isVisible = vpn && privateDnsState !is PrivateDnsState.Configured
        ipv6.isVisible = vpn
        applistType.isVisible = vpn
        selectedApps.isVisible = vpn && applistType.value in setOf("blacklist", "whitelist")

        val appCount = sharedPreferences?.getSelectedApps()?.size ?: 0
        selectedApps.summary = if (appCount == 0) {
            getString(R.string.apps_select_none)
        } else {
            resources.getQuantityString(R.plurals.apps_selected, appCount, appCount)
        }

        batteryOptimization.summary = getString(
            if (PermissionUtils.isBatteryOptimizationDisabled(context)) R.string.battery_optimization_disabled_summary
            else R.string.battery_optimization_summary
        )

        storageAccess.summary = getString(
            if (PermissionUtils.hasStorageAccess(context)) R.string.storage_access_allowed_summary
            else R.string.storage_access_summary
        )
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
        val presets = dnsPresets
        val current = sharedPreferences?.getStringNotNull("dns_ip", "1.1.1.1") ?: "1.1.1.1"
        val items = (presets.map { if (it.first.isBlank()) it.second else "${it.second} (${it.first})" } +
            getString(R.string.dns_custom)).toTypedArray()
        val checked = presets.indexOfFirst { it.first == current }.let { if (it == -1) presets.size else it }
        val dnsPref = findPreferenceNotNull<EditTextPreference>("dns_ip")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dbs_ip_setting)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                dialog.dismiss()
                if (which < presets.size) {
                    sharedPreferences?.edit { putString("dns_ip", presets[which].first) }
                } else {
                    onDisplayPreferenceDialog(dnsPref)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reset_settings)
            .setMessage(R.string.reset_settings_confirm)
            .setPositiveButton(R.string.action_reset) { _, _ ->
                (activity as? SettingsActivity)?.resetSettings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCredits() {
        val projects = listOf(
            "ByeDPI — hufrea" to "https://github.com/hufrea/byedpi",
            "hev-socks5-tunnel — heiher" to "https://github.com/heiher/hev-socks5-tunnel",
            "ByeByeDPI — romanvht" to "https://github.com/romanvht/ByeByeDPI",
            "ByeDPIAndroid — dovecoteescapee" to "https://github.com/dovecoteescapee/ByeDPIAndroid",
            getString(R.string.credits_license) to "https://www.gnu.org/licenses/gpl-3.0.html",
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.credits_title, getString(R.string.app_name)))
            .setItems(projects.map { it.first }.toTypedArray()) { _, which -> openUrl(projects[which].second) }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
