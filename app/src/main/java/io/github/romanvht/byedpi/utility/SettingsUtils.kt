package io.github.romanvht.byedpi.utility

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppSettings
import java.io.File
import java.util.Locale

object SettingsUtils {
    private const val TAG = "SettingsUtils"
    private val separatePreferenceKeys = setOf("byedpi_command_history", "selected_apps")

    /** Internal state that should not travel between devices with a settings backup. */
    private val internalPreferenceKeys = setOf(
        "applied_configuration",
        "welcome_shown",
        "is_test_running",
        "battery_optimization_requested",
        "notification_permission_requested",
    )

    /** Backups made by these apps can be imported too (this app is a fork of ByeByeDPI). */
    private val compatibleBackupApps = setOf(BuildConfig.APPLICATION_ID, "io.github.romanvht.byedpi")

    enum class Section {
        SETTINGS,
        HISTORY,
        APPS,
        DOMAIN_LISTS
    }

    /** Supported app languages: BCP 47 tag to the language's own name. */
    val languages: List<Pair<String, String>> = listOf(
        "az" to "Azərbaycanca",
        "id" to "Bahasa Indonesia",
        "de" to "Deutsch",
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "uz" to "Oʻzbekcha",
        "pt-BR" to "Português (Brasil)",
        "vi" to "Tiếng Việt",
        "tr" to "Türkçe",
        "be" to "Беларуская",
        "kk" to "Қазақша",
        "ru" to "Русский",
        "uk" to "Українська",
        "ar" to "العربية",
        "fa" to "فارسی",
        "hi" to "हिन्दी",
        "zh-CN" to "简体中文",
    )

    private const val SYSTEM_LANGUAGE = "system"

    fun getLanguagePreference(context: Context): String =
        context.getPreferences().getStringNotNull("language", SYSTEM_LANGUAGE)

    /** Language code (without region) of the language the app is shown in. */
    fun getCurrentLanguage(context: Context): String {
        val lang = getLanguagePreference(context)
        if (lang != SYSTEM_LANGUAGE) return Locale.forLanguageTag(lang).language

        val locales = AppCompatDelegate.getApplicationLocales()
        return locales[0]?.language ?: Locale.getDefault().language
    }

    fun languageName(context: Context, tag: String): String =
        languages.firstOrNull { it.first == tag }?.second ?: context.getString(R.string.language_system)

    fun setLang(lang: String) {
        val appLocale = localeByName(lang) ?: LocaleListCompat.getEmptyLocaleList()

        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != appLocale.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    /**
     * Applies the saved language. On Android 13+ the system remembers the app language itself and
     * the user can also change it in the system settings, so the saved value follows the system there.
     */
    fun restoreLanguage(context: Context) {
        val prefs = context.getPreferences()
        val saved = prefs.getStringNotNull("language", SYSTEM_LANGUAGE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val current = AppCompatDelegate.getApplicationLocales()
            val tag = if (current.isEmpty) SYSTEM_LANGUAGE else matchLanguage(current.toLanguageTags())
            if (tag != null && tag != saved) prefs.edit { putString("language", tag) }
        } else {
            setLang(saved)
        }
    }

    private fun matchLanguage(tags: String): String? {
        val locale = Locale.forLanguageTag(tags.substringBefore(','))
        val exact = languages.firstOrNull { Locale.forLanguageTag(it.first) == locale }
        return exact?.first ?: languages.firstOrNull { Locale.forLanguageTag(it.first).language == locale.language }?.first
    }

    private fun localeByName(lang: String): LocaleListCompat? = when {
        lang == SYSTEM_LANGUAGE -> LocaleListCompat.getEmptyLocaleList()
        languages.any { it.first == lang } -> LocaleListCompat.forLanguageTags(lang)
        else -> {
            Log.w(TAG, "Invalid value for language: $lang")
            null
        }
    }

    /** Shows a list of languages. The choice is saved and applied right away. */
    fun showLanguagePicker(activity: Activity, onSelected: (String) -> Unit = {}) {
        val tags = listOf(SYSTEM_LANGUAGE) + languages.map { it.first }
        val names = (listOf(activity.getString(R.string.language_system)) + languages.map { it.second }).toTypedArray()
        val checked = tags.indexOf(getLanguagePreference(activity)).coerceAtLeast(0)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.lang_settings)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                val tag = tags[which]
                activity.getPreferences().edit(commit = true) { putString("language", tag) }
                onSelected(tag)
                setLang(tag)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun setTheme(name: String) {
        val appTheme = themeByName(name) ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

        if (AppCompatDelegate.getDefaultNightMode() != appTheme) {
            AppCompatDelegate.setDefaultNightMode(appTheme)
        }
    }

    private fun themeByName(name: String): Int? = when (name) {
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            Log.w(TAG, "Invalid value for app_theme: $name")
            null
        }
    }

    /** Material You colors taken from the wallpaper (Android 12+), if the user turned them on. */
    fun useDynamicColors(context: Context): Boolean =
        DynamicColors.isDynamicColorAvailable() && context.getPreferences().getBoolean("dynamic_colors", false)

    fun exportSettings(context: Context, uri: Uri, sections: Set<Section>) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(settingsJson(context, sections).toByteArray())
            }
            exportDone(context)
        } catch (e: Exception) {
            exportFailed(context, e)
        }
    }

    fun exportSettings(context: Context, file: File, sections: Set<Section>) {
        try {
            file.writeText(settingsJson(context, sections))
            exportDone(context)
        } catch (e: Exception) {
            exportFailed(context, e)
        }
    }

    fun readSettings(context: Context, uri: Uri): AppSettings? {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return null

            parseSettings(context, json)
        } catch (e: Exception) {
            importFailed(context, e)
            null
        }
    }

    fun readSettings(context: Context, file: File): AppSettings? {
        return try {
            parseSettings(context, file.readText())
        } catch (e: Exception) {
            importFailed(context, e)
            null
        }
    }

    private fun settingsJson(context: Context, sections: Set<Section>): String {
        val prefs = context.getPreferences()
        val export = AppSettings(
            app = BuildConfig.APPLICATION_ID,
            version = BuildConfig.VERSION_NAME,
            history = if (Section.HISTORY in sections) {
                HistoryUtils(context).getHistory()
            } else {
                null
            },
            apps = if (Section.APPS in sections) prefs.getSelectedApps() else null,
            domainLists = if (Section.DOMAIN_LISTS in sections) {
                DomainListUtils.getAllLists(context)
            } else {
                null
            },
            settings = if (Section.SETTINGS in sections) {
                prefs.all.filterKeys { it !in separatePreferenceKeys && it !in internalPreferenceKeys }
            } else {
                null
            }
        )
        return Gson().toJson(export)
    }

    private fun parseSettings(context: Context, json: String): AppSettings? {
        val settings = Gson().fromJson(json, AppSettings::class.java)
        if (settings?.app in compatibleBackupApps) return settings

        Toast.makeText(context, R.string.import_failed, Toast.LENGTH_LONG).show()
        return null
    }

    private fun exportDone(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.export_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportFailed(context: Context, exception: Exception) {
        Log.e(TAG, "Failed to export settings", exception)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFailed(context: Context, exception: Exception) {
        Log.e(TAG, "Failed to read settings", exception)
        Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
    }

    fun getAvailableSections(settings: AppSettings): Set<Section> = buildSet {
        if (settings.settings != null) add(Section.SETTINGS)
        if (settings.history != null) add(Section.HISTORY)
        if (settings.apps != null) add(Section.APPS)
        if (settings.domainLists != null) add(Section.DOMAIN_LISTS)
    }

    fun importSettings(
        context: Context,
        import: AppSettings,
        sections: Set<Section>,
        onRestart: () -> Unit
    ) {
        try {
            val prefs = context.getPreferences()

            if (Section.SETTINGS in sections && import.settings != null) {
                prefs.edit(commit = true) {
                    prefs.all.keys
                        .filter { it !in separatePreferenceKeys && it !in internalPreferenceKeys }
                        .forEach { remove(it) }

                    import.settings
                        .filterKeys { it !in separatePreferenceKeys && it !in internalPreferenceKeys }
                        .forEach { (key, value) ->
                            when (value) {
                                is Int -> putInt(key, value)
                                is Boolean -> putBoolean(key, value)
                                is String -> putString(key, value)
                                is Float -> putFloat(key, value)
                                is Long -> putLong(key, value)
                                is Double -> {
                                    when (value) {
                                        value.toInt().toDouble() -> {
                                            putInt(key, value.toInt())
                                        }
                                        value.toLong().toDouble() -> {
                                            putLong(key, value.toLong())
                                        }
                                        else -> {
                                            putFloat(key, value.toFloat())
                                        }
                                    }
                                }
                                is Collection<*> -> {
                                    if (value.all { it is String }) {
                                        @Suppress("UNCHECKED_CAST")
                                        putStringSet(key, (value as Collection<String>).toSet())
                                    }
                                }
                            }
                        }
                }
            }

            if (Section.APPS in sections && import.apps != null) {
                prefs.edit(commit = true) { putStringSet("selected_apps", import.apps.toSet()) }
            }

            if (Section.HISTORY in sections && import.history != null) {
                HistoryUtils(context).saveHistory(import.history)
            }

            if (Section.DOMAIN_LISTS in sections && import.domainLists != null) {
                val normalized = import.domainLists.map {
                    if (it.isBuiltIn) {
                        it.copy(
                            isModified = it.isModified,
                            isDeleted = it.isDeleted
                        )
                    } else {
                        it.copy(
                            isModified = false,
                            isDeleted = false
                        )
                    }
                }

                DomainListUtils.saveLists(context, normalized)
            }

            if (Section.SETTINGS in sections) {
                val newLang = prefs.getString("language", SYSTEM_LANGUAGE) ?: SYSTEM_LANGUAGE
                val newTheme = prefs.getString("app_theme", "system") ?: "system"
                setLang(newLang)
                setTheme(newTheme)
            }

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.import_done, Toast.LENGTH_SHORT).show()
                onRestart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import settings", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun resetSettings(context: Context, onRestart: () -> Unit) {
        try {
            val prefs = context.getPreferences()
            val keep = prefs.all.filterKeys { it == "welcome_shown" }

            prefs.edit(commit = true) {
                clear()
                keep.forEach { (key, value) -> if (value is Boolean) putBoolean(key, value) }
            }

            HistoryUtils(context).saveHistory(emptyList())
            DomainListUtils.resetLists(context)

            setLang(SYSTEM_LANGUAGE)
            setTheme("system")

            Handler(Looper.getMainLooper()).post {
                onRestart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset settings", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.reset_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
