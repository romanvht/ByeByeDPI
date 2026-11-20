package io.github.romanvht.byedpi.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.SettingsUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getStringNotNull

abstract class BaseActivity : AppCompatActivity() {

    private var dynamicColorsApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getPreferences()

        SettingsUtils.restoreLanguage(this)
        SettingsUtils.setTheme(prefs.getStringNotNull("app_theme", "system"))

        dynamicColorsApplied = SettingsUtils.useDynamicColors(this)
        if (dynamicColorsApplied) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Pick up a changed "Use system colors" setting when coming back to an older screen
        if (dynamicColorsApplied != SettingsUtils.useDynamicColors(this)) recreate()
    }

    protected fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

}
