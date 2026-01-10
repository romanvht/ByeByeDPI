package io.github.dovecoteescapee.byedpi.fragments

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.*
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.utility.*

class ProxyTestSettingsFragment : PreferenceFragmentCompat() {

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updatePreferences()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.proxy_test_settings, rootKey)

        setEditTestPreferenceListenerInt(
            "byedpi_proxytest_delay",
            0,
            10
        )

        setEditTestPreferenceListenerInt(
            "byedpi_proxytest_requests",
            1,
            20
        )

        setEditTestPreferenceListenerInt(
            "byedpi_proxytest_timeout",
            1,
            15
        )

        setEditTestPreferenceListenerDomain(
            "byedpi_proxytest_sni"
        )

        updatePreferences()
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updatePreferences() {
        val switchUserCommands = findPreferenceNotNull<SwitchPreferenceCompat>("byedpi_proxytest_usercommands")
        val textUserDomains = findPreferenceNotNull<EditTextPreference>("byedpi_proxytest_domains")
        val textUserCommands = findPreferenceNotNull<EditTextPreference>("byedpi_proxytest_commands")
        val domainLists = findPreferenceNotNull<MultiSelectListPreference>("byedpi_proxytest_domain_lists")

        val setUserCommands = { enable: Boolean -> textUserCommands.isEnabled = enable }

        textUserDomains.isEnabled = domainLists.values?.contains("custom") == true
        setUserCommands(switchUserCommands.isChecked)

        if (domainLists.values?.isNotEmpty() == true) {
            domainLists.summary = domainLists.values.joinToString("\n")
        }
    }
}
