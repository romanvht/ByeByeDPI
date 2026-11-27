package io.github.romanvht.byedpi.fragments

import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.SiteResultAdapter
import io.github.romanvht.byedpi.utility.findPreferenceNotNull
import androidx.appcompat.app.AlertDialog
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Command
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.TestState
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.launch

class ByeDpiCMDSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var cmdHistoryUtils: HistoryUtils
    private lateinit var editTextPreference: EditTextPreference
    private lateinit var historyHeader: HistoryHeaderPreference
    private val historyPreferences = mutableListOf<Preference>()
    private val testPreferences = mutableMapOf<String, CommandPreference>()
    private val testSignatures = mutableMapOf<String, String>()
    private val expandedCommands = mutableSetOf<String>()
    private var lastTestRunning = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.byedpi_cmd_settings, rootKey)

        cmdHistoryUtils = HistoryUtils(requireContext())

        editTextPreference = findPreferenceNotNull("byedpi_cmd_args")
        editTextPreference.text = requireContext().getPreferences().getCmdArgs()

        historyHeader = HistoryHeaderPreference()
        preferenceScreen.addPreference(historyHeader)

        editTextPreference.setOnPreferenceChangeListener { _, newValue ->
            val newCommand = newValue.toString()

            if (newCommand.isNotBlank()) {
                cmdHistoryUtils.addCommand(newCommand)
                restartService()
            }

            updateHistoryItems()
            true
        }

        updateHistoryItems()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.post {
            view.findViewById<View>(R.id.btn_clear)?.setOnClickListener {
                editTextPreference.text = ""
                updateHistoryItems()
            }

            view.findViewById<View>(R.id.btn_paste)?.setOnClickListener {
                val text = ClipboardUtils.paste(requireContext())
                if (!text.isNullOrBlank()) {
                    editTextPreference.text = text
                    cmdHistoryUtils.addCommand(text)
                    updateHistoryItems()
                }
            }
        }

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TestService.state.collect { state ->
                    updateTestState(state)
                    if (!state.isLoaded && !state.isRunning) TestService.loadResults(appContext)
                }
            }
        }
    }

    private fun updateTestState(state: TestState) {
        val results = (state.carriedStrategies + state.strategies).associateBy { it.command }

        testPreferences.forEach { (command, preference) ->
            val result = results[command]
            val signature = listOf(
                result?.successCount,
                result?.totalRequests,
                result?.isCompleted,
                result?.siteResults?.size,
                command in expandedCommands,
            ).joinToString()

            if (testSignatures.put(command, signature) != signature) preference.refresh()
        }

        if (lastTestRunning != TestService.isRunning) {
            lastTestRunning = TestService.isRunning
            historyHeader.refresh()
        }
    }

    private fun updateHistoryItems() {
        if (listView !== null) {
            listView.itemAnimator = null
        }

        historyPreferences.forEach { preference ->
            preferenceScreen.removePreference(preference)
        }

        historyPreferences.clear()
        testPreferences.clear()
        testSignatures.clear()
        val history = cmdHistoryUtils.getHistory()
        historyHeader.isVisible = history.isNotEmpty()

        if (history.isNotEmpty()) {
            history.sortedWith(compareByDescending<Command> { it.pinned }.thenBy { history.indexOf(it) })
                .forEach { command ->
                    val preference = createPreference(command)
                    historyPreferences.add(preference)
                    testPreferences[command.text] = preference
                    preferenceScreen.addPreference(preference)
                }
        }
    }

    private fun createPreference(command: Command) = CommandPreference(command)

    private inner class HistoryHeaderPreference : Preference(requireContext()) {
        init {
            key = "cmd_history_header"
            title = getString(R.string.cmd_history_title)
            summary = getString(R.string.cmd_history_title_summary)
            layoutResource = R.layout.history_category
            setOnPreferenceClickListener {
                showHistoryClearDialog()
                true
            }
        }

        fun refresh() = notifyChanged()

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)

            val testAllButton = holder.itemView.findViewById<MaterialButton>(R.id.cmd_history_test_all) ?: return
            testAllButton.setText(if (TestService.isRunning) android.R.string.cancel else R.string.cmd_history_test_all)
            testAllButton.setOnClickListener {
                if (TestService.isRunning) TestService.stop() else testAllCommands()
            }
        }
    }

    private inner class CommandPreference(private val command: Command) : Preference(requireContext()) {
        init {
            title = command.text
            layoutResource = R.layout.history_item
            setOnPreferenceClickListener {
                showActionDialog(command)
                true
            }
        }

        fun refresh() = notifyChanged()

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)

            val nameView = holder.itemView.findViewById<TextView>(R.id.command_name)
            val summaryView = holder.itemView.findViewById<TextView>(android.R.id.summary)
            val pinIcon = holder.itemView.findViewById<ImageView>(R.id.pin_icon)
            val commandName = command.name

            if (!commandName.isNullOrBlank()) {
                nameView.visibility = View.VISIBLE
                nameView.text = command.name
            } else {
                nameView.visibility = View.GONE
            }

            val summaryText = buildSummary(command)
            if (summaryText.isNotBlank()) {
                summaryView.visibility = View.VISIBLE
                summaryView.text = summaryText
            } else {
                summaryView.visibility = View.GONE
            }

            pinIcon.visibility = if (command.pinned) View.VISIBLE else View.GONE

            bindTestState(holder.itemView, command)
        }
    }

    private fun bindTestState(itemView: View, command: Command) {
        val state = TestService.state.value
        val strategy = state.strategies.find { it.command == command.text }
            ?: state.carriedStrategies.find { it.command == command.text }

        val progressLayout = itemView.findViewById<View>(R.id.test_progress_layout)
        val detailsButton = itemView.findViewById<View>(R.id.test_details_button)
        val sitesRecyclerView = itemView.findViewById<RecyclerView>(R.id.test_sites_recycler)

        if (strategy == null || strategy.totalRequests <= 0) {
            progressLayout.isVisible = false
            detailsButton.isVisible = false
            sitesRecyclerView.isVisible = false
            return
        }

        val progressBar = itemView.findViewById<ProgressBar>(R.id.test_progress_bar)
        val progressText = itemView.findViewById<TextView>(R.id.test_progress_text)
        val detailsText = itemView.findViewById<TextView>(R.id.test_details_text)
        val detailsIcon = itemView.findViewById<ImageView>(R.id.test_details_icon)
        val expanded = strategy.command in expandedCommands

        progressLayout.isVisible = true
        progressBar.isIndeterminate = !strategy.isCompleted
        progressBar.max = strategy.totalRequests
        progressBar.progress = strategy.successCount
        progressText.text = "${strategy.successCount}/${strategy.totalRequests}"

        detailsButton.isVisible = strategy.siteResults.isNotEmpty()
        detailsText.setText(if (expanded) R.string.test_hide_details else R.string.test_show_details)
        detailsIcon.rotation = if (expanded) 180f else 0f
        sitesRecyclerView.isVisible = expanded

        if (expanded) {
            val adapter = sitesRecyclerView.adapter as? SiteResultAdapter ?: SiteResultAdapter().also {
                sitesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                sitesRecyclerView.adapter = it
            }
            adapter.updateSites(strategy.siteResults)
        }

        detailsButton.setOnClickListener {
            if (!expandedCommands.add(strategy.command)) expandedCommands.remove(strategy.command)
            testPreferences[strategy.command]?.refresh()
        }
    }

    private fun buildSummary(command: Command): String {
        val parts = mutableListOf<String>()

        if (command.text == editTextPreference.text) {
            parts.add(getString(R.string.cmd_history_applied))
        }

        return parts.joinToString(" • ")
    }

    private fun showHistoryClearDialog() {
        val options = arrayOf(
            getString(R.string.cmd_history_delete_unpinned),
            getString(R.string.cmd_history_delete_all),
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cmd_history_menu))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> deleteUnpinnedHistory()
                    1 -> deleteAllHistory()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun deleteAllHistory() {
        cmdHistoryUtils.clearAllHistory()
        updateHistoryItems()
    }

    private fun deleteUnpinnedHistory() {
        cmdHistoryUtils.clearUnpinnedHistory()
        updateHistoryItems()
    }

    private fun showActionDialog(command: Command) {
        val options = arrayOf(
            getString(R.string.cmd_history_test),
            getString(R.string.cmd_history_apply),
            if (command.pinned) getString(R.string.cmd_history_unpin) else getString(R.string.cmd_history_pin),
            getString(R.string.cmd_history_rename),
            getString(R.string.cmd_history_edit),
            getString(R.string.cmd_history_copy),
            getString(R.string.cmd_history_delete)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cmd_history_menu))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> testCommands(listOf(command.text), appendResults = true)
                    1 -> applyCommand(command.text)
                    2 -> if (command.pinned) unpinCommand(command.text) else pinCommand(command.text)
                    3 -> showRenameDialog(command)
                    4 -> showEditDialog(command)
                    5 -> ClipboardUtils.copy(requireContext(), command.text, "command")
                    6 -> deleteCommand(command.text)
                }
            }
            .show()
    }

    private fun testAllCommands() {
        val commands = cmdHistoryUtils.getHistory().map { it.text }
        if (commands.isNotEmpty()) testCommands(commands)
    }

    private fun testCommands(commands: List<String>, appendResults: Boolean = false) {
        if (TestService.isRunning) {
            Toast.makeText(requireContext(), R.string.test_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        TestService.start(requireContext(), commands, appendResults)
    }

    private fun showRenameDialog(command: Command) {
        val input = EditText(requireContext()).apply {
            setText(command.name)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cmd_history_rename))
            .setView(container)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val newName = input.text.toString()
                if (newName != command.name) {
                    cmdHistoryUtils.renameCommand(command.text, newName)
                    updateHistoryItems()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun showEditDialog(command: Command) {
        val input = EditText(requireContext()).apply {
            setText(command.text)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cmd_history_edit))
            .setView(container)
            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                val newText = input.text.toString()
                if (newText.isNotBlank() && newText != command.text) {
                    cmdHistoryUtils.editCommand(command.text, newText)
                    if (editTextPreference.text == command.text) applyCommand(newText)
                    updateHistoryItems()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    private fun applyCommand(command: String) {
        editTextPreference.text = command
        updateHistoryItems()
        restartService()
    }

    private fun pinCommand(command: String) {
        cmdHistoryUtils.pinCommand(command)
        updateHistoryItems()
    }

    private fun unpinCommand(command: String) {
        cmdHistoryUtils.unpinCommand(command)
        updateHistoryItems()
    }

    private fun deleteCommand(command: String) {
        cmdHistoryUtils.deleteCommand(command)
        updateHistoryItems()
    }

    private fun restartService() {
        if (appStatus.first == AppStatus.Running) {
            val ctx = context ?: return
            val mode = ctx.getPreferences().mode()
            if (mode == Mode.VPN && VpnService.prepare(ctx) != null) return

            ServiceManager.restart(ctx, mode)
            Toast.makeText(ctx, R.string.service_restart, Toast.LENGTH_SHORT).show()
        }
    }
}
