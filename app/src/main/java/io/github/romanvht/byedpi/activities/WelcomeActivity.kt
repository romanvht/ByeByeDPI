package io.github.romanvht.byedpi.activities

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.databinding.ActivityWelcomeBinding
import io.github.romanvht.byedpi.databinding.ItemFeatureRowBinding
import io.github.romanvht.byedpi.utility.PermissionUtils
import io.github.romanvht.byedpi.utility.SettingsUtils
import io.github.romanvht.byedpi.utility.getPreferences

/** Shown once on the first launch: explains what the app does and lets the user pick a language. */
class WelcomeActivity : BaseActivity() {

    companion object {
        const val WELCOME_SHOWN = "welcome_shown"
    }

    private lateinit var binding: ActivityWelcomeBinding

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finishWelcome() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.welcomeTitle.text = getString(R.string.welcome_title, getString(R.string.app_name))
        val appName = getString(R.string.app_name)
        binding.featureLocal.bind(R.drawable.ic_smartphone, R.string.welcome_local_title, getString(R.string.welcome_local_text))
        binding.featureNotVpn.bind(R.drawable.ic_shield_info, R.string.welcome_not_vpn_title, getString(R.string.welcome_not_vpn_text, appName))
        binding.featureStrategy.bind(R.drawable.ic_speed, R.string.welcome_strategy_title, getString(R.string.welcome_strategy_text))

        binding.languageButton.text = SettingsUtils.languageName(this, SettingsUtils.getLanguagePreference(this))
        binding.languageButton.setOnClickListener { showLanguagePicker() }

        binding.startButton.setOnClickListener {
            val permission = PermissionUtils.notificationPermission
            if (permission != null && !PermissionUtils.hasNotificationPermission(this)) {
                getPreferences().edit { putBoolean(MainActivity.NOTIFICATIONS_REQUESTED, true) }
                notificationPermission.launch(permission)
            } else {
                finishWelcome()
            }
        }
        binding.startButton.requestFocus()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWelcome()
        })
    }

    private fun ItemFeatureRowBinding.bind(@DrawableRes icon: Int, @StringRes title: Int, text: CharSequence) {
        this.icon.setImageResource(icon)
        this.title.setText(title)
        this.summary.text = text
    }

    private fun showLanguagePicker() {
        SettingsUtils.showLanguagePicker(this) { tag ->
            binding.languageButton.text = SettingsUtils.languageName(this, tag)
        }
    }

    private fun finishWelcome() {
        getPreferences().edit { putBoolean(WELCOME_SHOWN, true) }
        finish()
    }
}
