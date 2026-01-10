package io.github.dovecoteescapee.byedpi.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.Gson
import io.github.dovecoteescapee.byedpi.BuildConfig
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.AppsCache
import io.github.dovecoteescapee.byedpi.data.AppSettings
import io.github.dovecoteescapee.byedpi.fragments.ByeDpiCommandLineSettingsFragment
import io.github.dovecoteescapee.byedpi.fragments.ByeDpiUISettingsFragment
import io.github.dovecoteescapee.byedpi.fragments.MainSettingsFragment
import io.github.dovecoteescapee.byedpi.utility.HistoryUtils
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import io.github.dovecoteescapee.byedpi.utility.getSelectedApps
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val openFragment = intent.getStringExtra("open_fragment")

        when (openFragment) {
            "cmd" -> {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, ByeDpiCommandLineSettingsFragment())
                    .commit()
            }
            "ui" -> {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, ByeDpiUISettingsFragment())
                    .commit()
            }
            else -> {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, MainSettingsFragment())
                    .commit()
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch(Dispatchers.IO) {
            AppsCache.preloadApps(this@SettingsActivity)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }

        R.id.action_reset_settings -> {
            val prefs = getPreferences()
            val editor = prefs.edit()

            editor.clear()
            editor.apply()

            recreate()
            true
        }

        R.id.action_export_settings -> {
            val fileName = "bbd_${System.currentTimeMillis().toReadableDateTime()}.json"
            exportSettingsLauncher.launch(fileName)
            true
        }

        R.id.action_import_settings -> {
            importSettingsLauncher.launch(arrayOf("application/json"))
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private val exportSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val prefs = getPreferences()
            val history = HistoryUtils(this).getHistory()
            val apps = prefs.getSelectedApps()

            val settings = prefs.all.filterKeys { key ->
                key !in setOf("byedpi_command_history", "selected_apps")
            }

            val export = AppSettings(
                app = BuildConfig.APPLICATION_ID,
                version = BuildConfig.VERSION_NAME,
                history = history,
                apps = apps,
                settings = settings
            )

            val json = Gson().toJson(export)

            contentResolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
        }
    }

    private val importSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            contentResolver.openInputStream(it)?.use { inputStream ->
                val json = inputStream.bufferedReader().readText()

                val import = Gson().fromJson(json, AppSettings::class.java)

                if (import.app != BuildConfig.APPLICATION_ID) {
                    Toast.makeText(this, "Invalid config", Toast.LENGTH_LONG).show()
                    return@use
                }

                val prefs = getPreferences()
                prefs.edit {
                    clear()
                    import.settings.forEach { (key, value) ->
                        when (value) {
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                            is String -> putString(key, value)
                            is Float -> putFloat(key, value)
                            is Long -> putLong(key, value)
                        }
                    }
                    putStringSet("selected_apps", import.apps.toSet())
                }
                HistoryUtils(this).saveHistory(import.history)

                recreate()
            }
        }
    }

    private fun Long.toReadableDateTime(): String {
        val format = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault())
        return format.format(this)
    }
}
