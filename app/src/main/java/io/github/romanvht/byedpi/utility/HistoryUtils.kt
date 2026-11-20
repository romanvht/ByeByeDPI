package io.github.romanvht.byedpi.utility

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.romanvht.byedpi.data.Command
import com.google.gson.Gson
import androidx.core.content.edit

class HistoryUtils(context: Context) {

    private val context = context.applicationContext
    private val sharedPreferences: SharedPreferences = context.getPreferences()
    private val historyKey = "byedpi_command_history"
    private val maxHistorySize = 40

    fun addCommand(command: String) {
        if (command.isBlank()) return

        val history = getHistory().toMutableList()
        val unpinned = history.filter { !it.pinned }
        val search = history.find { it.text == command }

        if (search == null) {
            history.add(0, Command(command))
            if (history.size > maxHistorySize) {
                if (unpinned.isNotEmpty()) {
                    history.remove(unpinned.last())
                }
            }
        }

        saveHistory(history)
    }

    fun pinCommand(command: String) {
        val history = getHistory().toMutableList()
        history.find { it.text == command }?.pinned = true
        saveHistory(history)
    }

    fun unpinCommand(command: String) {
        val history = getHistory().toMutableList()
        history.find { it.text == command }?.pinned = false
        saveHistory(history)
    }

    fun deleteCommand(command: String) {
        val history = getHistory().toMutableList()
        history.removeAll { it.text == command }
        saveHistory(history)
    }

    fun renameCommand(command: String, newName: String) {
        val history = getHistory().toMutableList()
        history.find { it.text == command }?.name = newName
        saveHistory(history)
    }

    fun editCommand(command: String, newText: String) {
        val history = getHistory().toMutableList()
        history.find { it.text == command }?.text = newText
        saveHistory(history)
    }

    fun getHistory(): List<Command> {
        val historyJson = sharedPreferences.getString(historyKey, null) ?: return emptyList()
        return try {
            Gson().fromJson(historyJson, Array<Command>::class.java)
                ?.filter { @Suppress("SENSELESS_COMPARISON") (it != null && it.text != null) }
                .orEmpty()
        } catch (e: Exception) {
            Log.e("HistoryUtils", "Failed to read saved strategies", e)
            emptyList()
        }
    }

    fun getPinnedHistory(): List<Command> {
        return getHistory().filter { it.pinned }
    }

    fun saveHistory(history: List<Command>) {
        val historyJson = Gson().toJson(history)
        sharedPreferences.edit { putString(historyKey, historyJson) }
        ShortcutUtils.update(context)
    }

    fun clearAllHistory() {
        saveHistory(emptyList())
    }

    fun clearUnpinnedHistory() {
        val history = getHistory().filter { it.pinned }
        saveHistory(history)
    }
}
