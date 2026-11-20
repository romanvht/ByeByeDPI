package io.github.romanvht.byedpi.activities

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.databinding.ActivityMainBinding
import io.github.romanvht.byedpi.databinding.ItemMainRowBinding
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var historyUtils: HistoryUtils
    private var haloAnimator: Animator? = null
    private var pendingChangesSnackbar: Snackbar? = null
    private var shownState: ConnectionState? = null

    private enum class ConnectionState { Disconnected, Connecting, Connected, Disconnecting }

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
        private const val BATTERY_OPTIMIZATION_REQUESTED = "battery_optimization_requested"
        const val NOTIFICATIONS_REQUESTED = "notification_permission_requested"

        /** Start the service as soon as the main screen is shown (used after applying a strategy). */
        const val EXTRA_CONNECT = "connect"

        private fun collectLogs(): String? {
            return try {
                val process = Runtime.getRuntime().exec("logcat *:D -d")
                process.inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to collect logs", exception)
                null
            }
        }
    }

    private val vpnRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                ServiceManager.start(this, Mode.VPN)
            } else {
                showVpnPermissionDenied()
            }
            updateStatus()
        }

    private val notificationRegister =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { connectNow() }

    private val logsRegister = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::handleLogsFileResult
    )

    private fun handleLogsFileResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK) return
        val data = result.data ?: return
        val uri = data.data
        val path = data.getStringExtra(FileActivity.EXTRA_PATH)
        val file = if (path == null) null else File(path)
        if (uri == null && file == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            val logs = collectLogs()
            if (logs == null) {
                runOnUiThread { showMessage(R.string.logs_failed) }
                return@launch
            }

            val saved = try {
                val outputStream = when {
                    uri != null -> contentResolver.openOutputStream(uri)
                    file != null -> file.outputStream()
                    else -> null
                }
                outputStream?.use { stream -> stream.write(logs.toByteArray()) } != null
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save logs", exception)
                false
            }
            runOnUiThread { showMessage(if (saved) R.string.logs_saved else R.string.logs_failed) }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            Log.d(TAG, "Received intent: ${intent.action}")

            val sender = Sender.entries.getOrNull(intent.getIntExtra(SENDER, -1))
            if (sender == null) {
                Log.w(TAG, "Received intent with unknown sender")
                return
            }

            when (intent.action) {
                STARTED_BROADCAST -> {
                    updateStatus()
                    if (!ServiceManager.isStarting) onConnected()
                }

                STOPPED_BROADCAST -> updateStatus()

                FAILED_BROADCAST -> {
                    updateStatus()
                    val mode = getString(if (sender == Sender.VPN) R.string.mode_vpn else R.string.mode_proxy)
                    Snackbar.make(binding.root, getString(R.string.failed_to_start, mode), Snackbar.LENGTH_LONG)
                        .setAction(R.string.main_editor) { openEditor() }
                        .show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceManager.refresh(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        historyUtils = HistoryUtils(this)

        val intentFilter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }
        ContextCompat.registerReceiver(this, receiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setupViews()

        val prefs = getPreferences()
        if (!prefs.getBoolean(WelcomeActivity.WELCOME_SHOWN, false)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
        } else if (savedInstanceState == null) {
            val connectRequested = intent?.getBooleanExtra(EXTRA_CONNECT, false) == true
            if (connectRequested || prefs.getBoolean("auto_connect", false)) {
                if (!TestService.isRunning && appStatus.first != AppStatus.Running) start()
            }
        }

        ShortcutUtils.update(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_CONNECT, false) && appStatus.first != AppStatus.Running) {
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        ServiceManager.refresh(this)
        updateStatus()
        updateSetupRows()
        checkPendingChanges()
    }

    override fun onDestroy() {
        super.onDestroy()
        haloAnimator?.cancel()
        unregisterReceiver(receiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_diagnostics -> {
                showDiagnostics()
                true
            }

            R.id.action_save_logs -> {
                val intent = Intent(this, FileActivity::class.java)
                intent.putExtra(FileActivity.EXTRA_MODE, FileActivity.MODE_CREATE)
                intent.putExtra(FileActivity.EXTRA_TYPE, FileActivity.TYPE_LOGS)
                logsRegister.launch(intent)
                true
            }

            R.id.action_close_app -> {
                TestService.stop()
                lifecycleScope.launch {
                    try {
                        TestService.awaitStopped()
                        ServiceManager.waitStop()
                    } catch (exception: Exception) {
                        Log.e(TAG, "Failed to stop service before closing", exception)
                        return@launch
                    }
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(0)
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupViews() {
        val connect = binding.connectPanel

        connect.connectButton.setOnClickListener {
            connect.connectButton.isClickable = false
            when (appStatus.first) {
                AppStatus.Halted -> start()
                AppStatus.Running -> stop()
            }
            connect.connectButton.postDelayed({ connect.connectButton.isClickable = true }, 800)
        }

        // Visible focus ring for D-pad navigation (Android TV)
        connect.connectButton.setOnFocusChangeListener { view, hasFocus ->
            connect.connectButton.strokeWidth = if (hasFocus) (4 * view.resources.displayMetrics.density).toInt() else 0
        }

        val details = binding.detailsPanel
        details.modeRow.bind(R.drawable.ic_vpn_key, R.string.mode_setting)
        details.strategyRow.bind(R.drawable.ic_tune, R.string.main_strategy)
        details.actionFindStrategy.bind(R.drawable.ic_speed, R.string.title_test, R.string.main_find_strategy_summary)
        details.actionBlockedApps.bind(R.drawable.ic_get_app, R.string.blocked_apps)
        details.actionEditor.bind(R.drawable.ic_code, R.string.main_editor, R.string.main_editor_summary)
        details.actionSettings.bind(R.drawable.baseline_settings_24, R.string.title_settings, R.string.main_settings_summary)

        details.modeRow.root.setOnClickListener { showModePicker() }
        details.strategyRow.root.setOnClickListener { onStrategyClicked() }
        details.actionFindStrategy.root.setOnClickListener { openStrategyFinder() }
        details.actionBlockedApps.root.setOnClickListener { startActivity(Intent(this, BlockedAppsActivity::class.java)) }
        details.actionEditor.root.setOnClickListener { openEditor() }
        details.actionSettings.root.setOnClickListener { openSettings() }
    }

    private fun ItemMainRowBinding.bind(@DrawableRes icon: Int, @StringRes title: Int, @StringRes summary: Int? = null) {
        this.icon.setImageResource(icon)
        this.title.setText(title)
        if (summary != null) this.summary.setText(summary)
    }

    // region Connection

    private fun start() {
        if (TestService.isRunning) {
            showMessage(R.string.test_unavailable)
            return
        }

        val permission = PermissionUtils.notificationPermission
        if (permission != null && !PermissionUtils.hasNotificationPermission(this) &&
            !getPreferences().getBoolean(NOTIFICATIONS_REQUESTED, false)
        ) {
            // Ask once, so the connection can be seen and controlled from the notification shade
            getPreferences().edit { putBoolean(NOTIFICATIONS_REQUESTED, true) }
            notificationRegister.launch(permission)
            return
        }

        connectNow()
    }

    private fun connectNow() {
        when (getPreferences().mode()) {
            Mode.VPN -> {
                val intentPrepare = VpnService.prepare(this)
                if (intentPrepare != null) {
                    try {
                        vpnRegister.launch(intentPrepare)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to request VPN permission", e)
                        showVpnPermissionDenied()
                    }
                } else {
                    ServiceManager.start(this, Mode.VPN)
                }
            }

            Mode.Proxy -> ServiceManager.start(this, Mode.Proxy)
        }
        updateStatus()
    }

    private fun stop() {
        ServiceManager.stop()
        updateStatus()
    }

    /** Applies changed settings by restarting the running service. */
    private fun reconnect() {
        if (TestService.isRunning) {
            showMessage(R.string.test_unavailable)
            return
        }
        val mode = getPreferences().mode()
        if (mode == Mode.VPN && VpnService.prepare(this) != null) {
            ServiceManager.stop()
            connectNow()
        } else {
            ServiceManager.restart(this, mode)
        }
        updateStatus()
    }

    private fun onConnected() {
        checkPendingChanges()
        requestBatteryOptimization()
    }

    private fun showVpnPermissionDenied() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vpn_permission_denied)
            .setMessage(getString(R.string.vpn_permission_denied_hint, getString(R.string.app_name)))
            .setPositiveButton(R.string.action_try_again) { _, _ -> start() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateStatus() {
        val (status, mode) = appStatus
        val state = when {
            status == AppStatus.Halted -> ConnectionState.Disconnected
            ServiceManager.isStopping -> ConnectionState.Disconnecting
            ServiceManager.isStarting -> ConnectionState.Connecting
            else -> ConnectionState.Connected
        }

        Log.i(TAG, "Updating status: $status, $mode, $state")

        val connect = binding.connectPanel
        val prefs = getPreferences()
        val shownMode = if (status == AppStatus.Running) mode else prefs.mode()
        val (ip, port) = prefs.getProxyIpAndPort()
        val address = BidiFormatter.getInstance().unicodeWrap(getString(R.string.proxy_address, ip, port), TextDirectionHeuristicsCompat.LTR)

        val title = when (state) {
            ConnectionState.Disconnected -> R.string.status_disconnected
            ConnectionState.Connecting -> R.string.status_connecting
            ConnectionState.Connected -> if (shownMode == Mode.VPN) R.string.status_connected else R.string.status_proxy_running
            ConnectionState.Disconnecting -> R.string.status_disconnecting
        }
        connect.statusTitle.setText(title)
        connect.statusSubtitle.text = when (state) {
            ConnectionState.Disconnected -> getString(R.string.status_disconnected_hint)
            ConnectionState.Connecting -> getString(R.string.status_connecting_hint)
            ConnectionState.Connected -> if (shownMode == Mode.VPN) {
                getString(R.string.status_connected_hint)
            } else {
                getString(R.string.status_proxy_hint, address)
            }
            ConnectionState.Disconnecting -> ""
        }
        connect.statusSubtitle.visibility = if (connect.statusSubtitle.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        val active = state == ConnectionState.Connected
        val busy = state == ConnectionState.Connecting || state == ConnectionState.Disconnecting
        val primary = MaterialColors.getColor(connect.connectButton, androidx.appcompat.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(connect.connectButton, com.google.android.material.R.attr.colorOnPrimary)
        val idle = MaterialColors.getColor(connect.connectButton, com.google.android.material.R.attr.colorSurfaceContainerHighest)
        val onSurface = MaterialColors.getColor(connect.connectButton, com.google.android.material.R.attr.colorOnSurface)

        connect.connectButton.setCardBackgroundColor(if (active) primary else idle)
        connect.connectIcon.setColorFilter(if (active) onPrimary else primary)
        connect.statusTitle.setTextColor(if (active) primary else onSurface)
        connect.connectProgress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
        connect.connectButton.contentDescription = getString(
            if (status == AppStatus.Running) R.string.vpn_disconnect else R.string.vpn_connect
        )

        if (active) startHalo() else stopHalo()
        if (state != shownState) {
            shownState = state
            updateSetupRows()
            if (state == ConnectionState.Disconnected) pendingChangesSnackbar?.dismiss()
        }
    }

    private fun startHalo() {
        if (haloAnimator?.isRunning == true) return
        val halo = binding.connectPanel.connectHalo
        haloAnimator = ObjectAnimator.ofPropertyValuesHolder(
            halo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.12f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.35f, 0f),
        ).apply {
            duration = 2200
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopHalo() {
        haloAnimator?.cancel()
        haloAnimator = null
        binding.connectPanel.connectHalo.alpha = 0f
    }

    // endregion

    // region Current setup

    private fun updateSetupRows() {
        val prefs = getPreferences()
        val details = binding.detailsPanel

        details.actionBlockedApps.summary.text = RegionUtils.currentCountry(this)
            ?.let { getString(R.string.blocked_apps_summary, RegionUtils.countryName(this, it)) }
            ?: getString(R.string.blocked_apps_summary_unknown)
        val running = appStatus.first == AppStatus.Running
        val mode = if (running) appStatus.second else prefs.mode()

        details.modeRow.summary.text = when (mode) {
            Mode.VPN -> {
                val apps = prefs.getSelectedApps().size
                when (prefs.getStringNotNull("applist_type", "disable")) {
                    "blacklist" -> resources.getQuantityString(R.plurals.mode_vpn_except_apps, apps, apps)
                    "whitelist" -> resources.getQuantityString(R.plurals.mode_vpn_only_apps, apps, apps)
                    else -> getString(R.string.mode_vpn_all_apps)
                }
            }

            Mode.Proxy -> {
                val (ip, port) = prefs.getProxyIpAndPort()
                getString(R.string.mode_proxy_summary, BidiFormatter.getInstance().unicodeWrap(
                    getString(R.string.proxy_address, ip, port), TextDirectionHeuristicsCompat.LTR
                ))
            }
        }

        val summary = details.strategyRow.summary
        if (!prefs.getCmdEnable()) {
            summary.typeface = Typeface.DEFAULT
            summary.textDirection = View.TEXT_DIRECTION_LOCALE
            summary.setText(R.string.main_strategy_visual)
            return
        }

        val args = prefs.getCmdArgs()
        val name = historyUtils.getHistory().find { it.text == args }?.name?.takeIf { it.isNotBlank() }
        when {
            name != null -> {
                summary.typeface = Typeface.DEFAULT
                summary.textDirection = View.TEXT_DIRECTION_LOCALE
                summary.text = name
            }

            args.isBlank() -> {
                summary.typeface = Typeface.DEFAULT
                summary.textDirection = View.TEXT_DIRECTION_LOCALE
                summary.setText(R.string.main_strategy_empty)
            }

            else -> {
                summary.typeface = Typeface.MONOSPACE
                summary.textDirection = View.TEXT_DIRECTION_LTR
                summary.text = args
            }
        }
    }

    private fun showModePicker() {
        val prefs = getPreferences()
        val modes = listOf(Mode.VPN, Mode.Proxy)
        val labels = arrayOf(getString(R.string.mode_vpn_choice), getString(R.string.mode_proxy_choice))
        val checked = modes.indexOf(prefs.mode())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mode_setting)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                val selected = modes[which]
                if (selected == prefs.mode()) return@setSingleChoiceItems
                prefs.edit { putString("byedpi_mode", if (selected == Mode.VPN) "vpn" else "proxy") }
                updateSetupRows()
                if (appStatus.first == AppStatus.Running) reconnect() else updateStatus()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onStrategyClicked() {
        if (getPreferences().getCmdEnable()) {
            showStrategyPicker()
        } else {
            openEditor()
        }
    }

    private fun showStrategyPicker() {
        val history = historyUtils.getHistory().sortedByDescending { it.pinned }
        val current = getPreferences().getCmdArgs()

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.main_strategy_picker)
            .setPositiveButton(R.string.action_find_strategy_short) { _, _ -> openStrategyFinder() }
            .setNeutralButton(R.string.main_editor) { _, _ -> openEditor() }

        if (history.isEmpty()) {
            builder.setMessage(R.string.main_strategy_no_saved).show()
            return
        }

        val adapter = object : ArrayAdapter<Command>(this, R.layout.item_strategy_choice, history) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_strategy_choice, parent, false)
                val command = history[position]
                val name = command.name?.takeIf { it.isNotBlank() }

                view.findViewById<TextView>(R.id.strategyName).apply {
                    visibility = if (name != null) View.VISIBLE else View.GONE
                    text = name
                }
                view.findViewById<TextView>(R.id.strategyText).text = command.text
                view.findViewById<View>(R.id.strategyIcon).visibility =
                    if (command.text == current) View.VISIBLE else View.INVISIBLE
                return view
            }
        }

        builder.setAdapter(adapter) { _, which -> applyStrategy(history[which].text) }.show()
    }

    private fun applyStrategy(commandText: String) {
        val prefs = getPreferences()
        val changed = prefs.getCmdArgs() != commandText || !prefs.getCmdEnable()
        prefs.edit {
            putString("byedpi_cmd_args", commandText)
            putBoolean("byedpi_enable_cmd_settings", true)
        }
        updateSetupRows()

        if (changed && appStatus.first == AppStatus.Running) {
            reconnect()
            showMessage(R.string.service_restart)
        }
    }

    /** Offers a reconnect when settings were changed while connected. */
    private fun checkPendingChanges() {
        val outdated = appStatus.first == AppStatus.Running &&
            !ServiceManager.isStarting &&
            ServiceManager.isConfigurationOutdated(this)

        if (!outdated) {
            pendingChangesSnackbar?.dismiss()
            pendingChangesSnackbar = null
            return
        }
        if (pendingChangesSnackbar?.isShownOrQueued == true) return

        pendingChangesSnackbar = Snackbar.make(binding.root, R.string.settings_changed_reconnect, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.action_reconnect) { reconnect() }
            .also { it.show() }
    }

    // endregion

    // region Navigation

    private fun openStrategyFinder() {
        startActivity(Intent(this, TestActivity::class.java))
    }

    private fun openEditor() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra("open_fragment", if (getPreferences().getCmdEnable()) "cmd" else "ui")
        startActivity(intent)
    }

    private fun openSettings() {
        if (TestService.isRunning) {
            showMessage(R.string.test_unavailable)
            return
        }
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // endregion

    private fun showDiagnostics() {
        val report = DiagnosticUtils.buildReport(this)
        val padding = (24 * resources.displayMetrics.density).toInt()

        val textView = TextView(this).apply {
            text = report
            setTextIsSelectable(true)
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.diagnostics)
            .setView(scrollView)
            .setPositiveButton(R.string.diagnostic_copy) { _, _ ->
                ClipboardUtils.copy(this, report, getString(R.string.diagnostics))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus()
        }
        dialog.show()
    }

    private fun requestBatteryOptimization() {
        val preferences = getPreferences()
        if (preferences.getBoolean(BATTERY_OPTIMIZATION_REQUESTED, false)) return
        if (PermissionUtils.isBatteryOptimizationDisabled(this)) return

        preferences.edit { putBoolean(BATTERY_OPTIMIZATION_REQUESTED, true) }
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_battery)
            .setTitle(R.string.battery_dialog_title)
            .setMessage(getString(R.string.battery_dialog_text, getString(R.string.app_name)))
            .setPositiveButton(R.string.action_allow) { _, _ -> PermissionUtils.requestBatteryOptimization(this) }
            .setNegativeButton(R.string.action_not_now, null)
            .show()
    }

    private fun showMessage(@StringRes message: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
