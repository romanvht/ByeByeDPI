package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.PermissionUtils
import io.github.romanvht.byedpi.utility.StorageUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class FileActivity : BaseActivity() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_TYPE = "type"
        const val EXTRA_PATH = "path"
        const val MODE_OPEN = "open"
        const val MODE_CREATE = "create"
        const val TYPE_SETTINGS = "settings"
        const val TYPE_LOGS = "logs"

        private const val STATE_CURRENT_DIRECTORY = "current_directory"
        private const val STATE_EXTERNAL_REQUEST = "external_request_active"
        private const val TAG = "FileActivity"
    }

    private lateinit var pathView: TextView
    private lateinit var listView: ListView
    private lateinit var savePanel: LinearLayout
    private lateinit var fileNameView: EditText
    private var currentDirectory: File? = null
    private var entries = emptyList<Entry>()
    private var externalRequestActive = false
    private val mode by lazy { intent.getStringExtra(EXTRA_MODE) ?: MODE_OPEN }
    private val type by lazy { intent.getStringExtra(EXTRA_TYPE) ?: TYPE_SETTINGS }
    private val mimeType get() = if (type == TYPE_LOGS) "text/plain" else "application/json"
    private val extension get() = if (type == TYPE_LOGS) "log" else "json"
    private val defaultFileName by lazy {
        val format = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        val prefix = if (type == TYPE_LOGS) "sidestep_logs" else "sidestep_settings"
        "${prefix}_${format.format(System.currentTimeMillis())}.$extension"
    }
    private val roots by lazy { StorageUtils.getRoots(this) }

    private val systemFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        externalRequestActive = false
        val resultIntent = result.data
        if (result.resultCode == RESULT_OK && resultIntent?.data != null) {
            val response = Intent()
            response.data = resultIntent.data
            response.flags = resultIntent.flags
            response.putExtra(EXTRA_MODE, mode)
            setResult(RESULT_OK, response)
        }
        finish()
    }

    private val storageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        externalRequestActive = false
        continueAfterStorageRequest()
    }

    private val legacyStorageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        externalRequestActive = false
        continueAfterStorageRequest()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file)
        setupToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val title = when {
            type == TYPE_LOGS -> R.string.save_logs
            mode == MODE_CREATE -> R.string.export_settings
            else -> R.string.import_settings
        }
        supportActionBar?.setTitle(title)

        pathView = findViewById(R.id.current_path)
        listView = findViewById(R.id.files)
        savePanel = findViewById(R.id.save_panel)
        fileNameView = findViewById(R.id.file_name)
        externalRequestActive = savedInstanceState?.getBoolean(STATE_EXTERNAL_REQUEST) ?: false

        savePanel.visibility = if (mode == MODE_CREATE) View.VISIBLE else View.GONE
        fileNameView.setText(defaultFileName)
        findViewById<Button>(R.id.save_file).setOnClickListener { saveFile() }
        listView.setOnItemClickListener { _, _, position, _ -> open(entries[position]) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateUp()
            }
        })

        if (!externalRequestActive) {
            when {
                PermissionUtils.hasStorageAccess(this) -> showInitialDirectory(savedInstanceState)
                hasSystemFilePicker() -> openSystemFilePicker()
                else -> showStorageAccessDialog()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CURRENT_DIRECTORY, currentDirectory?.absolutePath)
        outState.putBoolean(STATE_EXTERNAL_REQUEST, externalRequestActive)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_file, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_close -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        navigateUp()
        return true
    }

    private fun navigateUp() {
        val directory = currentDirectory
        if (directory == null) {
            finish()
            return
        }

        val parent = directory.parentFile
        if (parent != null && isInsideRoots(parent)) {
            showDirectory(parent)
        } else {
            showRoots()
        }
    }

    private fun showRoots() {
        currentDirectory = null
        pathView.setText(R.string.storage_locations)
        savePanel.visibility = View.GONE
        val rootEntries = mutableListOf<Entry>()
        for (root in roots) {
            rootEntries.add(Entry(root, rootName(root)))
        }
        entries = rootEntries
        updateList()
    }

    private fun showInitialDirectory(savedInstanceState: Bundle?) {
        var directory: File? = null
        val savedPath = savedInstanceState?.getString(STATE_CURRENT_DIRECTORY)
        if (savedPath != null) {
            val savedDirectory = File(savedPath)
            if (savedDirectory.isDirectory && isInsideRoots(savedDirectory)) {
                directory = savedDirectory
            }
        }

        if (directory == null) {
            directory = findInternalStorage()
        }
        if (directory == null && roots.isNotEmpty()) {
            directory = roots[0]
        }

        if (directory != null) showDirectory(directory) else showRoots()
    }

    private fun findInternalStorage(): File? {
        val internalStoragePath = Environment.getExternalStorageDirectory().absolutePath
        for (root in roots) {
            if (root.absolutePath == internalStoragePath) return root
        }
        return null
    }

    private fun isInsideRoots(file: File): Boolean {
        for (root in roots) {
            if (StorageUtils.isInside(file, root)) return true
        }
        return false
    }

    private fun hasSystemFilePicker(): Boolean {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return false
        return packageManager.resolveActivity(systemFileIntent(), PackageManager.MATCH_DEFAULT_ONLY) != null
    }

    private fun openSystemFilePicker() {
        try {
            externalRequestActive = true
            systemFileLauncher.launch(systemFileIntent())
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to open system file picker", exception)
            externalRequestActive = false
            showStorageAccessDialog()
        }
    }

    private fun systemFileIntent(): Intent {
        val action = if (mode == MODE_CREATE) {
            Intent.ACTION_CREATE_DOCUMENT
        } else {
            Intent.ACTION_OPEN_DOCUMENT
        }
        val intent = Intent(action)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = mimeType
        if (mode == MODE_CREATE) {
            intent.putExtra(Intent.EXTRA_TITLE, defaultFileName)
        }
        return intent
    }

    private fun showStorageAccessDialog() {
        val dialog = AlertDialog.Builder(this)
            .setMessage(R.string.file_picker_unavailable)
            .setPositiveButton(R.string.grant_access) { _, _ -> requestStorageAccess() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .create()
        dialog.setOnCancelListener { finish() }
        dialog.show()
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            externalRequestActive = true
            try {
                storageAccessLauncher.launch(PermissionUtils.storageAccessIntent(this))
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to open app storage access settings", exception)
                try {
                    storageAccessLauncher.launch(PermissionUtils.storageAccessFallbackIntent())
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "Failed to open storage access settings", fallbackException)
                    externalRequestActive = false
                    Toast.makeText(
                        this,
                        R.string.storage_access_not_granted,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        } else {
            externalRequestActive = true
            legacyStorageAccessLauncher.launch(PermissionUtils.getLegacyStoragePermissions())
        }
    }

    private fun continueAfterStorageRequest() {
        if (PermissionUtils.hasStorageAccess(this)) {
            showInitialDirectory(null)
        } else {
            Toast.makeText(this, R.string.storage_access_not_granted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showDirectory(directory: File) {
        val children = try {
            directory.listFiles() ?: emptyArray()
        } catch (_: SecurityException) {
            emptyArray()
        }

        currentDirectory = directory
        pathView.text = directory.absolutePath
        savePanel.visibility = if (mode == MODE_CREATE) View.VISIBLE else View.GONE

        val visibleFiles = mutableListOf<File>()
        for (file in children) {
            val isSupportedFile = mode == MODE_OPEN && file.extension.equals(extension, true)
            if (file.isDirectory || isSupportedFile) {
                visibleFiles.add(file)
            }
        }
        visibleFiles.sortWith(FileComparator)

        val fileEntries = mutableListOf<Entry>()
        for (file in visibleFiles) {
            fileEntries.add(Entry(file, file.name))
        }
        entries = fileEntries
        updateList()
    }

    private fun updateList() {
        listView.adapter = object : ArrayAdapter<Entry>(
            this,
            android.R.layout.simple_list_item_1,
            entries
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val entry = entries[position]
                val density = resources.displayMetrics.density
                val horizontalPadding = (16 * density).toInt()
                val verticalPadding = (8 * density).toInt()
                view.text = entry.label
                view.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    if (entry.file.isDirectory) R.drawable.ic_folder else R.drawable.ic_description,
                    0,
                    0,
                    0
                )
                view.compoundDrawablePadding = horizontalPadding
                view.setPadding(
                    horizontalPadding,
                    verticalPadding,
                    horizontalPadding,
                    verticalPadding
                )
                return view
            }
        }
    }

    private fun open(entry: Entry) {
        if (entry.file.isDirectory) {
            if (isInsideRoots(entry.file)) {
                showDirectory(entry.file)
            }
        } else {
            returnPath(entry.file)
        }
    }

    private fun saveFile() {
        val directory = currentDirectory ?: return
        if (!directory.canWrite()) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val input = fileNameView.text.toString().trim()
        if (input.isBlank() || input.contains('/') || input.contains('\\')) {
            Toast.makeText(this, R.string.invalid_file_name, Toast.LENGTH_SHORT).show()
            return
        }

        val name = if (input.endsWith(".$extension", true)) input else "$input.$extension"
        val file = File(directory, name)
        if (file.exists()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.replace_file_confirmation)
                .setPositiveButton(R.string.replace) { _, _ -> returnPath(file) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            returnPath(file)
        }
    }

    private fun returnPath(file: File) {
        val response = Intent()
        response.putExtra(EXTRA_MODE, mode)
        response.putExtra(EXTRA_PATH, file.absolutePath)
        setResult(RESULT_OK, response)
        finish()
    }

    private fun rootName(root: File): String {
        return if (root.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
            getString(R.string.internal_storage)
        } else {
            root.name.ifBlank { root.absolutePath }
        }
    }

    private object FileComparator : Comparator<File> {
        override fun compare(first: File, second: File): Int {
            if (first.isDirectory != second.isDirectory) {
                return if (first.isDirectory) -1 else 1
            }
            return first.name.compareTo(second.name, ignoreCase = true)
        }
    }

    private data class Entry(
        val file: File,
        val label: String
    )
}
