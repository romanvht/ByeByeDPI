package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.StrategyResultAdapter
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.SiteResult
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.SiteCheckUtils
import io.github.romanvht.byedpi.utility.getIntStringNotNull
import io.github.romanvht.byedpi.utility.getLongStringNotNull
import androidx.core.content.edit
import io.github.romanvht.byedpi.utility.getStringNotNull
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.utility.DomainListUtils
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.*
import java.io.File

class TestActivity : BaseActivity() {

    private lateinit var strategiesRecyclerView: RecyclerView
    private lateinit var progressTextView: TextView
    private lateinit var disclaimerTextView: TextView
    private lateinit var startStopButton: MaterialButton
    private lateinit var strategyAdapter: StrategyResultAdapter

    private lateinit var siteChecker: SiteCheckUtils
    private lateinit var cmdHistoryUtils: HistoryUtils
    private lateinit var sites: List<String>
    private lateinit var cmds: List<String>

    private var savedCmd: String = ""
    private var testJob: Job? = null
    private val strategies = mutableListOf<StrategyResult>()
    private val gson = Gson()

    private var isTesting: Boolean
        get() = prefs.getBoolean("is_test_running", false)
        set(value) {
            prefs.edit(commit = true) { putBoolean("is_test_running", value) }
        }

    private val prefs by lazy { getPreferences() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proxy_test)
        setupToolbar()

        val ip = prefs.getStringNotNull("byedpi_proxy_ip", "127.0.0.1")
        val port = prefs.getIntStringNotNull("byedpi_proxy_port", 1080)

        siteChecker = SiteCheckUtils(ip, port)
        cmdHistoryUtils = HistoryUtils(this)

        strategiesRecyclerView = findViewById(R.id.strategiesRecyclerView)
        startStopButton = findViewById(R.id.startStopButton)
        progressTextView = findViewById(R.id.progressTextView)
        disclaimerTextView = findViewById(R.id.disclaimerTextView)

        strategyAdapter = StrategyResultAdapter(this,
            onApply = { command ->
                addToHistory(command)
            }
        )

        strategiesRecyclerView.layoutManager = LinearLayoutManager(this)
        strategiesRecyclerView.adapter = strategyAdapter

        lifecycleScope.launch {
            val previousResults = loadResults()

            if (previousResults.isNotEmpty()) {
                progressTextView.text = getString(R.string.test_complete)
                disclaimerTextView.visibility = View.GONE

                strategies.clear()
                strategies.addAll(previousResults)

                strategyAdapter.updateStrategies(strategies)
            }

            if (isTesting) {
                progressTextView.text = getString(R.string.test_proxy_error)
                disclaimerTextView.text = getString(R.string.test_crash)
                disclaimerTextView.visibility = View.VISIBLE
                isTesting = false
            }
        }

        startStopButton.setOnClickListener {
            startStopButton.isClickable = false

            if (isTesting) {
                stopTesting()
            } else {
                startTesting()
            }

            startStopButton.postDelayed({ startStopButton.isClickable = true }, 1000)
        }

        startStopButton.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                startStopButton.strokeWidth = 10
                startStopButton.strokeColor = ColorStateList.valueOf(android.graphics.Color.argb(100, 0, 0, 0))
            } else {
                startStopButton.strokeWidth = 0
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTesting) {
                    stopTesting()
                } else {
                    if (appStatus.first == AppStatus.Running) {
                        val intent = Intent(this@TestActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                    }
                }

                finish()
            }
        })

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
                    val intent = Intent(this, TestSettingsActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
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

    private suspend fun waitForProxyStatus(statusNeeded: AppStatus): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 3000) {
            if (appStatus.first == statusNeeded) {
                delay(500)
                return true
            }
            delay(100)
        }
        return false
    }

    private suspend fun isProxyRunning(): Boolean = withContext(Dispatchers.IO) {
        appStatus.first == AppStatus.Running
    }

    private fun updateCmdArgs(cmd: String) {
        prefs.edit(commit = true) { putString("byedpi_cmd_args", cmd) }
    }

    private fun startTesting() {
        sites = loadSites()
        cmds = loadCmds()

        if (sites.isEmpty()) {
            Toast.makeText(this, R.string.test_settings_domain_empty, Toast.LENGTH_LONG).show()
            return
        }

        testJob = lifecycleScope.launch(Dispatchers.IO) {
            isTesting = true
            savedCmd = prefs.getCmdArgs()

            strategies.clear()
            strategies.addAll(cmds.map { StrategyResult(command = it) })

            withContext(Dispatchers.Main) {
                disclaimerTextView.visibility = View.GONE

                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startStopButton.text = getString(R.string.test_stop)
                progressTextView.text = ""

                strategyAdapter.setTestingState(true)
                strategyAdapter.updateStrategies(strategies, sortByPercentage = false)
            }

            if (isProxyRunning()) {
                ServiceManager.stop(this@TestActivity)
                waitForProxyStatus(AppStatus.Halted)
            }

            val delaySec = prefs.getIntStringNotNull("byedpi_proxytest_delay", 1)
            val requestsCount = prefs.getIntStringNotNull("byedpi_proxytest_requests", 1)
            val requestTimeout = prefs.getLongStringNotNull("byedpi_proxytest_timeout", 5)
            val requestLimit = prefs.getIntStringNotNull("byedpi_proxytest_limit", 20)

            for (strategyIndex in strategies.indices) {
                if (!isActive) break

                val strategy = strategies[strategyIndex]
                val cmdIndex = strategyIndex + 1

                withContext(Dispatchers.Main) {
                    progressTextView.text = getString(R.string.test_process, cmdIndex, cmds.size)
                }

                updateCmdArgs(strategy.command)

                if (isProxyRunning()) stopTesting()
                else ServiceManager.start(this@TestActivity, Mode.Proxy)

                if (!waitForProxyStatus(AppStatus.Running)) {
                    stopTesting()
                }

                delay(delaySec * 500L)

                val totalRequests = sites.size * requestsCount
                strategy.totalRequests = totalRequests

                withContext(Dispatchers.Main) {
                    strategyAdapter.notifyItemChanged(strategyIndex)
                }

                siteChecker.checkSitesAsync(
                    sites = sites,
                    requestsCount = requestsCount,
                    requestTimeout = requestTimeout,
                    concurrentRequests = requestLimit,
                    fullLog = true,
                    onSiteChecked = { site, successCount, countRequests ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            strategy.currentProgress += countRequests
                            strategy.successCount += successCount
                            strategy.siteResults.add(SiteResult(site, successCount, countRequests))

                            strategyAdapter.notifyItemChanged(strategyIndex, "progress")
                        }
                    }
                )

                strategy.isCompleted = true

                withContext(Dispatchers.Main) {
                    strategyAdapter.updateStrategies(strategies, sortByPercentage = true)
                    saveResults(strategies)
                }

                if (isProxyRunning()) ServiceManager.stop(this@TestActivity)
                else stopTesting()

                if (!waitForProxyStatus(AppStatus.Halted)) {
                    stopTesting()
                }

                delay(delaySec * 500L)
            }

            stopTesting()
        }
    }

    private fun stopTesting() {
        if (!isTesting) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            isTesting = false
            updateCmdArgs(savedCmd)

            testJob?.cancel()
            testJob = null

            if (isProxyRunning()) {
                ServiceManager.stop(this@TestActivity)
            }

            withContext(Dispatchers.Main) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startStopButton.text = getString(R.string.test_start)
                progressTextView.text = getString(R.string.test_complete)

                strategyAdapter.setTestingState(false)
                strategyAdapter.updateStrategies(strategies, sortByPercentage = true)

                saveResults(strategies)
            }
        }
    }

    private fun addToHistory(command: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            updateCmdArgs(command)
            cmdHistoryUtils.addCommand(command)

            val mode = prefs.mode()
            if (mode == Mode.VPN && VpnService.prepare(this@TestActivity) != null) return@launch

            val toastText = if (appStatus.first == AppStatus.Running) {
                ServiceManager.restart(this@TestActivity, mode)
                R.string.service_restart
            } else {
                R.string.cmd_history_applied
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@TestActivity, toastText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveResults(results: List<StrategyResult>) {
        val file = File(filesDir, "proxy_test_results.json")
        val json = gson.toJson(results)
        file.writeText(json)
    }

    private fun loadResults(): List<StrategyResult> {
        val file = File(filesDir, "proxy_test_results.json")
        return if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<List<StrategyResult>>() {}.type
                gson.fromJson<List<StrategyResult>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun loadSites(): List<String> {
        DomainListUtils.syncLists(this)
        return DomainListUtils.getActiveDomains(this)
    }

    private fun loadCmds(): List<String> {
        val userCommands = prefs.getBoolean("byedpi_proxytest_usercommands", false)
        val sniValue = prefs.getStringNotNull("byedpi_proxytest_sni", "google.com")

        return if (userCommands) {
            val content = prefs.getStringNotNull("byedpi_proxytest_commands", "")
            content.replace("{sni}", "\"${sniValue}\"").lines().map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            val content = assets.open("proxytest_strategies.list").bufferedReader().readText()
            content.replace("{sni}", "\"${sniValue}\"").lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    private fun copyLog() {
        val completeStrategies = strategies.filter { it.isCompleted }

        if (completeStrategies.isEmpty()) {
            Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
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

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("proxy_test_log", sb.toString())
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }
}
