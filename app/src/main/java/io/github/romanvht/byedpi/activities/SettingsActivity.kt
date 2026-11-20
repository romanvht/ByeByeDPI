package io.github.romanvht.byedpi.activities

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppSettings
import io.github.romanvht.byedpi.fragments.ByeDpiCMDSettingsFragment
import io.github.romanvht.byedpi.fragments.ByeDpiUISettingsFragment
import io.github.romanvht.byedpi.fragments.MainSettingsFragment
import io.github.romanvht.byedpi.utility.SettingsUtils
import io.github.romanvht.byedpi.utility.SettingsUtils.Section
import java.io.File

class SettingsActivity : BaseActivity() {
    private var exportSections = Section.entries.toSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exportSections = savedInstanceState
            ?.getStringArrayList("export_sections")
            ?.mapNotNull { name -> Section.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: Section.entries.toSet()

        setContentView(R.layout.activity_settings)
        setupToolbar()

        if (savedInstanceState == null) {
            val fragment = when (intent.getStringExtra("open_fragment")) {
                "cmd" -> ByeDpiCMDSettingsFragment()
                "ui" -> ByeDpiUISettingsFragment()
                else -> MainSettingsFragment()
            }
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, fragment)
                .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(
            "export_sections",
            ArrayList(exportSections.map { it.name })
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    fun exportSettings() {
        showSectionSelectionDialog(
            title = R.string.export_settings,
            availableSections = Section.entries.toSet()
        ) { sections ->
            exportSections = sections
            openFile(FileActivity.MODE_CREATE)
        }
    }

    fun importSettings() {
        openFile(FileActivity.MODE_OPEN)
    }

    fun resetSettings() {
        SettingsUtils.resetSettings(this) {
            recreate()
        }
    }

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::handleFileResult
    )

    private fun handleFileResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK) return
        val data = result.data ?: return
        val mode = data.getStringExtra(FileActivity.EXTRA_MODE)
        val uri = data.data
        val path = data.getStringExtra(FileActivity.EXTRA_PATH)
        val file = if (path == null) null else File(path)

        when (mode) {
            FileActivity.MODE_OPEN -> {
                val settings = when {
                    uri != null -> SettingsUtils.readSettings(this, uri)
                    file != null -> SettingsUtils.readSettings(this, file)
                    else -> null
                } ?: return
                val availableSections = SettingsUtils.getAvailableSections(settings)
                if (availableSections.isNotEmpty()) {
                    showImportDialog(settings, availableSections)
                }
            }

            FileActivity.MODE_CREATE -> when {
                uri != null -> SettingsUtils.exportSettings(this, uri, exportSections)
                file != null -> SettingsUtils.exportSettings(this, file, exportSections)
            }
        }
    }

    private fun openFile(mode: String) {
        val intent = Intent(this, FileActivity::class.java)
        intent.putExtra(FileActivity.EXTRA_MODE, mode)
        intent.putExtra(FileActivity.EXTRA_TYPE, FileActivity.TYPE_SETTINGS)
        fileLauncher.launch(intent)
    }

    private fun showImportDialog(settings: AppSettings, availableSections: Set<Section>) {
        showSectionSelectionDialog(
            title = R.string.import_settings,
            availableSections = availableSections
        ) { sections ->
            SettingsUtils.importSettings(this, settings, sections) {
                recreate()
            }
        }
    }

    private fun showSectionSelectionDialog(
        @StringRes title: Int,
        availableSections: Set<Section>,
        onConfirmed: (Set<Section>) -> Unit
    ) {
        val sections = Section.entries.filter { it in availableSections }
        val checkedItems = BooleanArray(sections.size) { true }
        val labels = sections.map { getString(it.label) }.toTypedArray()

        lateinit var dialog: AlertDialog
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = checkedItems.any { it }
            }
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

            positiveButton.setOnClickListener {
                val selected = sections
                    .filterIndexed { index, _ -> checkedItems[index] }
                    .toSet()

                if (selected.isNotEmpty()) {
                    dialog.dismiss()
                    onConfirmed(selected)
                }
            }

            positiveButton.isEnabled = checkedItems.any { it }
        }

        dialog.show()
    }

    @get:StringRes
    private val Section.label: Int
        get() = when (this) {
            Section.SETTINGS -> R.string.backup_settings
            Section.HISTORY -> R.string.backup_command_history
            Section.APPS -> R.string.backup_selected_apps
            Section.DOMAIN_LISTS -> R.string.backup_domain_lists
        }

}
