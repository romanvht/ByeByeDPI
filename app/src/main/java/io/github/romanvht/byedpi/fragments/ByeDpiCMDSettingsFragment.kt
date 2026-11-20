package io.github.romanvht.byedpi.fragments

import android.content.Intent
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Command
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.ClipboardUtils
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.findPreferenceNotNull
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import io.github.romanvht.byedpi.views.BindablePreference

class ByeDpiCMDSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var cmdHistoryUtils: HistoryUtils
    private lateinit var editTextPreference: EditTextPreference
    private lateinit var historyHeader: BindablePreference
    private val historyPreferences = mutableListOf<Preference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.byedpi_cmd_settings, rootKey)

        cmdHistoryUtils = HistoryUtils(requireContext())

        editTextPreference = findPreferenceNotNull("byedpi_cmd_args")
        editTextPreference.text = requireContext().getPreferences().getCmdArgs()
        editTextPreference.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            val text = preference.text
            if (text.isNullOrBlank()) {
                getString(R.string.main_strategy_empty)
            } else {
                BidiFormatter.getInstance().unicodeWrap(text, TextDirectionHeuristicsCompat.LTR)
            }
        }
        editTextPreference.setOnBindEditTextListener { editText ->
            editText.typeface = Typeface.MONOSPACE
            editText.textDirection = View.TEXT_DIRECTION_LTR
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editText.setSelection(editText.text.length)
        }

        editTextPreference.setOnPreferenceChangeListener { _, newValue ->
            val newCommand = newValue.toString()

            if (newCommand.isNotBlank()) {
                cmdHistoryUtils.addCommand(newCommand)
                restartService()
            }

            updateHistoryItems()
            true
        }

        findPreferenceNotNull<BindablePreference>("cmd_editor_buttons").onBind = { holder ->
            holder.findViewById(R.id.btn_clear)?.setOnClickListener {
                editTextPreference.text = ""
                updateHistoryItems()
            }

            holder.findViewById(R.id.btn_paste)?.setOnClickListener {
                val text = ClipboardUtils.paste(requireContext())?.trim()
                if (!text.isNullOrBlank()) {
                    editTextPreference.text = text
                    cmdHistoryUtils.addCommand(text)
                    updateHistoryItems()
                    restartService()
                } else {
                    Toast.makeText(requireContext(), R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
                }
            }

            holder.findViewById(R.id.btn_help)?.setOnClickListener { openDocumentation() }
        }

        historyHeader = findPreferenceNotNull("cmd_history_header")
        historyHeader.onBind = { holder ->
            holder.findViewById(R.id.btn_clear_history)?.setOnClickListener { showHistoryClearDialog() }
        }

        updateHistoryItems()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.main_editor)
    }

    private fun updateHistoryItems() {
        if (listView !== null) {
            listView.itemAnimator = null
        }

        historyPreferences.forEach { preference ->
            preferenceScreen.removePreference(preference)
        }

        historyPreferences.clear()
        val history = cmdHistoryUtils.getHistory()
        historyHeader.isVisible = history.isNotEmpty()

        history.sortedWith(compareByDescending<Command> { it.pinned }.thenBy { history.indexOf(it) })
            .forEach { command ->
                val preference = createPreference(command)
                historyPreferences.add(preference)
                preferenceScreen.addPreference(preference)
            }
    }

    private fun createPreference(command: Command) =
        object : Preference(requireContext()) {
            override fun onBindViewHolder(holder: PreferenceViewHolder) {
                super.onBindViewHolder(holder)

                val nameView = holder.itemView.findViewById<TextView>(R.id.command_name)
                val appliedBadge = holder.itemView.findViewById<View>(R.id.applied_badge)
                val pinIcon = holder.itemView.findViewById<ImageView>(R.id.pin_icon)
                val headerRow = holder.itemView.findViewById<View>(R.id.header_row)
                val commandName = command.name?.takeIf { it.isNotBlank() }
                val applied = command.text == editTextPreference.text

                nameView.visibility = if (commandName != null) View.VISIBLE else View.INVISIBLE
                nameView.text = commandName
                appliedBadge.visibility = if (applied) View.VISIBLE else View.GONE
                pinIcon.visibility = if (command.pinned) View.VISIBLE else View.GONE
                headerRow.visibility = if (commandName != null || applied || command.pinned) View.VISIBLE else View.GONE
            }
        }.apply {
            title = command.text
            layoutResource = R.layout.history_item
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                showActionDialog(command)
                true
            }
        }

    private fun showHistoryClearDialog() {
        val options = arrayOf(
            getString(R.string.cmd_history_delete_unpinned),
            getString(R.string.cmd_history_delete_all),
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cmd_history_clear)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> deleteUnpinnedHistory()
                    1 -> confirmDeleteAllHistory()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAllHistory() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cmd_history_delete_all)
            .setMessage(R.string.action_cannot_undo)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                cmdHistoryUtils.clearAllHistory()
                updateHistoryItems()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteUnpinnedHistory() {
        cmdHistoryUtils.clearUnpinnedHistory()
        updateHistoryItems()
    }

    private fun showActionDialog(command: Command) {
        val options = arrayOf(
            getString(R.string.action_apply),
            if (command.pinned) getString(R.string.cmd_history_unpin) else getString(R.string.cmd_history_pin),
            getString(R.string.cmd_history_rename),
            getString(R.string.action_edit),
            getString(R.string.action_copy),
            getString(R.string.action_delete)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(command.name?.takeIf { it.isNotBlank() } ?: getString(R.string.cmd_history_menu))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyCommand(command.text)
                    1 -> if (command.pinned) unpinCommand(command.text) else pinCommand(command.text)
                    2 -> showRenameDialog(command)
                    3 -> showEditDialog(command)
                    4 -> ClipboardUtils.copy(requireContext(), command.text, "command")
                    5 -> deleteCommand(command.text)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dialogInput(text: String?, code: Boolean): Pair<LinearLayout, EditText> {
        val input = EditText(requireContext()).apply {
            setText(text)
            if (code) {
                typeface = Typeface.MONOSPACE
                textDirection = View.TEXT_DIRECTION_LTR
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } else {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine()
                hint = getString(R.string.cmd_history_name_hint)
            }
            setSelection(this.text.length)
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        return container to input
    }

    private fun showRenameDialog(command: Command) {
        val (container, input) = dialogInput(command.name, code = false)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cmd_history_rename)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName != command.name) {
                    cmdHistoryUtils.renameCommand(command.text, newName)
                    updateHistoryItems()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(command: Command) {
        val (container, input) = dialogInput(command.text, code = true)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_edit)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotBlank() && newText != command.text) {
                    cmdHistoryUtils.editCommand(command.text, newText)
                    if (editTextPreference.text == command.text) applyCommand(newText)
                    updateHistoryItems()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
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

    private fun openDocumentation() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, getString(R.string.byedpi_docs).toUri()))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
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
