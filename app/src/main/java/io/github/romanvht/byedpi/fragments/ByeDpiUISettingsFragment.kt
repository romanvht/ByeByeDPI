package io.github.romanvht.byedpi.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.*
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.UISettings
import io.github.romanvht.byedpi.data.UISettings.DesyncMethod.*
import io.github.romanvht.byedpi.data.UISettings.HostsMode.*
import io.github.romanvht.byedpi.utility.*

class ByeDpiUISettingsFragment : PreferenceFragmentCompat() {

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updatePreferences()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.byedpi_ui_settings, rootKey)

        setEditTestPreferenceListenerInt(
            "byedpi_max_connections",
            1,
            Short.MAX_VALUE.toInt()
        )
        setEditTestPreferenceListenerInt(
            "byedpi_buffer_size",
            1,
            Int.MAX_VALUE / 4
        )
        setEditTestPreferenceListenerInt("byedpi_default_ttl", 0, 255)
        setEditTestPreferenceListenerInt(
            "byedpi_split_position",
            Int.MIN_VALUE,
            Int.MAX_VALUE
        )
        setEditTestPreferenceListenerInt("byedpi_fake_ttl", 1, 255)
        setEditTestPreferenceListenerInt(
            "byedpi_tlsrec_position",
            2 * Short.MIN_VALUE,
            2 * Short.MAX_VALUE,
        )

        findPreferenceNotNull<EditTextPreference>("byedpi_oob_data")
            .setOnBindEditTextListener {
                it.filters = arrayOf(android.text.InputFilter.LengthFilter(1))
            }

        updatePreferences()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_editor, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId != R.id.action_help) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, getString(R.string.byedpi_docs).toUri()))
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), R.string.no_browser, Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.ui_editor)
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updatePreferences() {
        val desyncMethod = findPreferenceNotNull<ListPreference>("byedpi_desync_method").value
            ?.let { UISettings.DesyncMethod.fromNameOrNull(it) } ?: OOB
        val hostsMode = findPreferenceNotNull<ListPreference>("byedpi_hosts_mode").value
            ?.let { UISettings.HostsMode.fromNameOrNull(it) } ?: Disable

        val hostsBlacklist = findPreferenceNotNull<EditTextPreference>("byedpi_hosts_blacklist")
        val hostsWhitelist = findPreferenceNotNull<EditTextPreference>("byedpi_hosts_whitelist")
        val desyncHttp = findPreferenceNotNull<CheckBoxPreference>("byedpi_desync_http")
        val desyncHttps = findPreferenceNotNull<CheckBoxPreference>("byedpi_desync_https")
        val desyncUdp = findPreferenceNotNull<CheckBoxPreference>("byedpi_desync_udp")
        val splitPosition = findPreferenceNotNull<EditTextPreference>("byedpi_split_position")
        val splitAtHost = findPreferenceNotNull<CheckBoxPreference>("byedpi_split_at_host")
        val ttlFake = findPreferenceNotNull<EditTextPreference>("byedpi_fake_ttl")
        val fakeSni = findPreferenceNotNull<EditTextPreference>("byedpi_fake_sni")
        val fakeOffset = findPreferenceNotNull<EditTextPreference>("byedpi_fake_offset")
        val oobChar = findPreferenceNotNull<EditTextPreference>("byedpi_oob_data")
        val udpFakeCount = findPreferenceNotNull<EditTextPreference>("byedpi_udp_fake_count")
        val hostMixedCase = findPreferenceNotNull<CheckBoxPreference>("byedpi_host_mixed_case")
        val domainMixedCase = findPreferenceNotNull<CheckBoxPreference>("byedpi_domain_mixed_case")
        val hostRemoveSpaces = findPreferenceNotNull<CheckBoxPreference>("byedpi_host_remove_spaces")
        val splitTlsRec = findPreferenceNotNull<CheckBoxPreference>("byedpi_tlsrec_enabled")
        val splitTlsRecPosition = findPreferenceNotNull<EditTextPreference>("byedpi_tlsrec_position")
        val splitTlsRecAtSni = findPreferenceNotNull<CheckBoxPreference>("byedpi_tlsrec_at_sni")

        hostsBlacklist.isVisible = hostsMode == Blacklist
        hostsWhitelist.isVisible = hostsMode == Whitelist

        val desyncEnabled = desyncMethod != None
        splitPosition.isVisible = desyncEnabled
        splitAtHost.isVisible = desyncEnabled

        val isFake = desyncMethod == Fake
        ttlFake.isVisible = isFake
        fakeSni.isVisible = isFake
        fakeOffset.isVisible = isFake

        val isOob = desyncMethod == OOB || desyncMethod == DISOOB
        oobChar.isVisible = isOob

        val desyncAllProtocols = !desyncHttp.isChecked && !desyncHttps.isChecked && !desyncUdp.isChecked

        val desyncHttpEnabled = desyncAllProtocols || desyncHttp.isChecked
        hostMixedCase.isEnabled = desyncHttpEnabled
        domainMixedCase.isEnabled = desyncHttpEnabled
        hostRemoveSpaces.isEnabled = desyncHttpEnabled

        val desyncUdpEnabled = desyncAllProtocols || desyncUdp.isChecked
        udpFakeCount.isEnabled = desyncUdpEnabled

        val desyncHttpsEnabled = desyncAllProtocols || desyncHttps.isChecked
        splitTlsRec.isEnabled = desyncHttpsEnabled
        val tlsRecEnabled = desyncHttpsEnabled && splitTlsRec.isChecked
        splitTlsRecPosition.isEnabled = tlsRecEnabled
        splitTlsRecAtSni.isEnabled = tlsRecEnabled
    }
}
