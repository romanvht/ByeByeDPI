package io.github.romanvht.byedpi.utility

import android.net.InetAddresses
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.github.romanvht.byedpi.R

private const val TAG = "ValidateUtils"

fun checkIp(ip: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        InetAddresses.isNumericAddress(ip)
    } else {
        // This pattern doesn't not support IPv6
        // @Suppress("DEPRECATION")
        // Patterns.IP_ADDRESS.matcher(ip).matches()
        true
    }
}

fun checkNotLocalIp(ip: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        InetAddresses.isNumericAddress(ip) && InetAddresses.parseNumericAddress(ip).let {
            !it.isAnyLocalAddress && !it.isLoopbackAddress
        }
    } else {
        // This pattern doesn't not support IPv6
        // @Suppress("DEPRECATION")
        // Patterns.IP_ADDRESS.matcher(ip).matches()
        true
    }
}

fun checkDomain(domain: String): Boolean {
    if (domain.isEmpty()) return false

    if (domain.length > 253) return false
    if (domain.startsWith(".") || domain.endsWith(".")) return false
    if (!domain.contains(".")) return false

    return true
}

fun PreferenceFragmentCompat.setEditTestPreferenceListenerDomain(key: String) {
    setEditTextPreferenceListener(key) { value ->
        value.isNotEmpty() && checkDomain(value)
    }
}

fun PreferenceFragmentCompat.setEditTestPreferenceListenerPort(key: String) {
    setEditTestPreferenceListenerInt(key, 1, 65535)
}

fun PreferenceFragmentCompat.setEditTestPreferenceListenerInt(
    key: String,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE
) {
    setEditTextPreferenceListener(key) { value ->
        value.toIntOrNull()?.let { it in min..max } ?: false
    }
}

fun PreferenceFragmentCompat.setEditTextPreferenceListener(
    key: String,
    check: (String) -> Boolean
) {
    findPreferenceNotNull<EditTextPreference>(key)
        .setOnPreferenceChangeListener { preference, newValue ->
            when (newValue) {
                is String -> {
                    val valid = check(newValue)
                    if (!valid) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.invalid_value, preference.title),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    valid
                }

                else -> {
                    Log.w(
                        TAG,
                        "Invalid type for ${preference.key}: " +
                                "$newValue has type ${newValue::class.java}"
                    )
                    false
                }
            }
        }
}
