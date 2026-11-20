package io.github.romanvht.byedpi.utility

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat
import java.util.Locale
import java.util.TimeZone

/** Finds out which country the device is in, without using the network. */
object RegionUtils {
    private const val TAG = "RegionUtils"
    private const val PREF_REGION = "region"
    private const val AUTO = "auto"

    /** The country the user chose, or the detected one. ISO 3166 code such as "TR", or null when unknown. */
    fun currentCountry(context: Context): String? = chosenCountry(context) ?: detectCountry(context)

    /** The country the user chose by hand, or null when it is detected automatically. */
    fun chosenCountry(context: Context): String? =
        context.getPreferences().getString(PREF_REGION, AUTO)?.takeIf { it != AUTO && isCountryCode(it) }

    /** Saves the user's choice. Null goes back to automatic detection. */
    fun setChosenCountry(context: Context, country: String?) {
        context.getPreferences().edit { putString(PREF_REGION, country ?: AUTO) }
    }

    fun detectCountry(context: Context): String? {
        val telephony = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (e: Exception) {
            Log.w(TAG, "No telephony service", e)
            null
        }

        val network = try {
            // The mobile network shows where the device is now, even when roaming.
            telephony?.takeIf { it.phoneType != TelephonyManager.PHONE_TYPE_CDMA }?.networkCountryIso
        } catch (e: Exception) {
            null
        }
        val sim = try {
            telephony?.simCountryIso
        } catch (e: Exception) {
            null
        }
        val timeZone = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.icu.util.TimeZone.getRegion(TimeZone.getDefault().id)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return pickCountry(network, sim, timeZone, Locale.getDefault().country)
    }

    /** Picks the first usable country code: mobile network, then SIM card, then time zone, then language settings. */
    fun pickCountry(network: String?, sim: String?, timeZone: String?, locale: String?): String? =
        listOf(network, sim, timeZone, locale)
            .map { it?.trim()?.uppercase(Locale.ROOT) }
            .firstOrNull { it != null && isCountryCode(it) }

    private val countryCodes: Set<String> by lazy { Locale.getISOCountries().toSet() }

    /** True for a two-letter code of a real country (not "ZZ", "001" or other placeholders). */
    fun isCountryCode(code: String): Boolean = code in countryCodes

    /** The country's name in the app language, for example "Türkiye" or "Турция". */
    fun countryName(context: Context, code: String): String {
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        val country = try {
            Locale.Builder().setRegion(code).build()
        } catch (e: Exception) {
            return code
        }
        return country.getDisplayCountry(locale).ifBlank { code }
    }
}
