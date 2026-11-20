package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.StrategyResultAdapter
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.data.TestState
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.DomainListUtils
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestActivity : BaseActivity() {

    private lateinit var strategiesRecyclerView: RecyclerView
    private lateinit var statusTextView: TextView
    private lateinit var hintTextView: TextView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var emptyView: View
    private lateinit var startStopButton: MaterialButton
    private lateinit var strategyAdapter: StrategyResultAdapter
    private lateinit var cmdHistoryUtils: HistoryUtils

    private val strategies = mutableListOf<StrategyResult>()
    private var renderedRunId = -1L
    private val isTesting: Boolean get() = TestService.isRunning
    private val prefs by lazy { getPreferences() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceManager.refresh(this)
        setContentView(R.layout.activity_proxy_test)
        setupToolbar()

        cmdHistoryUtils = HistoryUtils(this)
        strategiesRecyclerView = findViewById(R.id.strategiesRecyclerView)
        startStopButton = findViewById(R.id.startStopButton)
        statusTextView = findViewById(R.id.status_text)
        hintTextView = findViewById(R.id.hint_text)
        progressIndicator = findViewById(R.id.test_progress)
        emptyView = findViewById(R.id.empty_view)

        strategyAdapter = StrategyResultAdapter(this,
            onApply = { command -> applyStrategy(command) },
            onCopy = { command -> ClipboardUtils.copy(this, command, "command") },
        )

        strategiesRecyclerView.layoutManager = LinearLayoutManager(this)
        strategiesRecyclerView.adapter = strategyAdapter

        // Create the built-in domain lists on first use, so the summary shows what will be checked
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { DomainListUtils.syncLists(applicationContext) }
            renderState(TestService.state.value)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TestService.state.collect { state ->
                    renderState(state)
                    if (!state.isLoaded && !state.isRunning) TestService.loadResults(applicationContext)
                }
            }
        }

        startStopButton.setOnClickListener {
            startStopButton.isClickable = false

            if (isTesting) TestService.stop() else TestService.start(this)

            startStopButton.postDelayed({ startStopButton.isClickable = true }, 1000)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Opened from the notification: go to the main screen instead of leaving the app
                if (isTaskRoot) startActivity(Intent(this@TestActivity, MainActivity::class.java))
                finish()
            }
        })

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        renderState(TestService.state.value)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_test, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy_log -> {
                copyLog()
                true
            }
            R.id.action_settings -> {
                if (!isTesting) {
                    startActivity(Intent(this, TestSettingsActivity::class.java))
                } else {
                    showMessage(getString(R.string.test_unavailable))
                }
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun renderState(state: TestState) {
        val total = state.strategies.size
        val completed = state.strategies.count { it.isCompleted }

        startStopButton.text = getString(when {
            state.isRunning -> R.string.test_stop
            total > 0 -> R.string.test_restart
            else -> R.string.test_start
        })
        startStopButton.setIconResource(if (state.isRunning) R.drawable.ic_close else R.drawable.ic_speed)
        startStopButton.isEnabled = !state.isStopping
        if (state.isRunning) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        when {
            state.isRunning -> {
                statusTextView.text = when {
                    state.isStopping -> getString(R.string.test_stopping)
                    state.currentStrategy > 0 -> getString(R.string.test_process, state.currentStrategy, total)
                    else -> getString(R.string.test_preparing)
                }
                hintTextView.text = getString(R.string.test_running_hint)
                progressIndicator.visibility = View.VISIBLE
                if (total > 0) {
                    progressIndicator.isIndeterminate = false
                    progressIndicator.max = total
                    progressIndicator.setProgressCompat(completed, true)
                } else if (!progressIndicator.isIndeterminate) {
                    progressIndicator.visibility = View.INVISIBLE
                    progressIndicator.isIndeterminate = true
                    progressIndicator.visibility = View.VISIBLE
                }
            }

            state.wasInterrupted -> {
                statusTextView.text = getString(R.string.test_proxy_error)
                hintTextView.text = getString(R.string.test_crash)
                progressIndicator.visibility = View.GONE
            }

            state.failed -> {
                statusTextView.text = getString(R.string.test_proxy_error)
                hintTextView.text = getString(R.string.test_failed_hint)
                progressIndicator.visibility = View.GONE
            }

            state.strategies.isNotEmpty() -> {
                val working = state.strategies.any { it.isCompleted && it.successCount > 0 }
                statusTextView.text = getString(if (working) R.string.test_complete else R.string.test_nothing_works)
                hintTextView.text = getString(if (working) R.string.test_complete_info else R.string.test_nothing_works_hint)
                progressIndicator.visibility = View.GONE
            }

            else -> {
                statusTextView.text = getString(R.string.test_ready)
                hintTextView.text = sitesSummary()
                progressIndicator.visibility = View.GONE
            }
        }

        emptyView.visibility = if (!state.isRunning && state.strategies.isEmpty()) View.VISIBLE else View.GONE

        strategyAdapter.setTestingState(state.isRunning)
        val replace = renderedRunId != state.runId || strategies.map { it.command } != state.strategies.map { it.command }
        val reorder = replace || strategies.indices.any { strategies[it].isCompleted != state.strategies[it].isCompleted }
        if (replace) {
            strategies.clear()
            strategies.addAll(state.strategies.map { it.copy(siteResults = it.siteResults.toMutableList()) })
            renderedRunId = state.runId
        } else {
            strategies.forEachIndexed { index, strategy ->
                val value = state.strategies[index]
                if (strategy.copy(isExpanded = value.isExpanded) != value) {
                    strategy.successCount = value.successCount
                    strategy.totalRequests = value.totalRequests
                    strategy.currentProgress = value.currentProgress
                    strategy.isCompleted = value.isCompleted
                    strategy.siteResults.clear()
                    strategy.siteResults.addAll(value.siteResults)
                    if (!reorder) strategyAdapter.updateStrategy(strategy)
                }
            }
        }
        if (reorder) strategyAdapter.updateStrategies(strategies)
    }

    private fun sitesSummary(): String {
        val names = DomainListUtils.getLists(this).filter { it.isActive }.map { it.name }
        return if (names.isEmpty()) {
            getString(R.string.test_sites_none)
        } else {
            getString(R.string.test_sites_hint, names.joinToString(", "))
        }
    }

    /** Saves the strategy, switches to command-line strategies and reconnects if needed. */
    private fun applyStrategy(command: String) {
        if (isTesting) return

        prefs.edit(commit = true) {
            putString("byedpi_cmd_args", command)
            putBoolean("byedpi_enable_cmd_settings", true)
        }
        strategyAdapter.notifyItemRangeChanged(0, strategyAdapter.itemCount)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { cmdHistoryUtils.addCommand(command) }

            if (isTesting) return@launch
            val running = appStatus.first == AppStatus.Running
            val mode = prefs.mode()
            if (running && (mode != Mode.VPN || VpnService.prepare(this@TestActivity) == null)) {
                ServiceManager.restart(this@TestActivity, mode)
                showMessage(getString(R.string.service_restart))
            } else {
                Snackbar.make(strategiesRecyclerView, R.string.strategy_applied, Snackbar.LENGTH_LONG)
                    .setAction(R.string.vpn_connect) { connect() }
                    .show()
            }
        }
    }

    private fun connect() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_CONNECT, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun copyLog() {
        val completeStrategies = strategies.filter { it.isCompleted }

        if (completeStrategies.isEmpty()) {
            showMessage(getString(R.string.test_log_empty))
            return
        }

        val sb = StringBuilder()

        completeStrategies.forEach { strategy ->
            sb.appendLine("${strategy.command}\n")

            strategy.siteResults.forEach { site ->
                sb.appendLine("${site.site} - ${site.successCount}/${site.totalCount}")
            }

            sb.appendLine("\n${strategy.successCount}/${strategy.totalRequests}")
            sb.appendLine("-------------")
        }

        ClipboardUtils.copy(this, sb.toString(), "proxy_test_log")
    }

    private fun showMessage(message: String) {
        Snackbar.make(strategiesRecyclerView, message, Snackbar.LENGTH_SHORT).show()
    }
}
