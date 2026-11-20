package io.github.romanvht.byedpi.activities

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.RESTART_ACTION
import io.github.romanvht.byedpi.data.START_ACTION
import io.github.romanvht.byedpi.data.STOP_ACTION
import io.github.romanvht.byedpi.receiver.ServiceActionReceiver
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode

class ToggleActivity : Activity() {

    companion object {
        private const val TAG = "ToggleServiceActivity"
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceManager.refresh(this)

        prefs = getPreferences()
        val strategy = intent.getStringExtra("strategy")
        val updated = updateStrategy(strategy)

        val onlyUpdate = intent.getBooleanExtra("only_update", false)
        val onlyStart = intent.getBooleanExtra("only_start", false)
        val onlyStop = intent.getBooleanExtra("only_stop", false)

        when {
            onlyUpdate -> {
                Log.i(TAG, "Only update strategy")
            }
            onlyStart -> {
                val (status) = appStatus
                if (status == AppStatus.Halted) {
                    startService()
                } else {
                    Log.i(TAG, "Service already running")
                }
            }
            onlyStop -> {
                val (status) = appStatus
                if (status == AppStatus.Running) {
                    stopService()
                } else {
                    Log.i(TAG, "Service already stopped")
                }
            }
            else -> {
                toggleService(updated)
            }
        }

        finish()
    }

    private fun startService() {
        val mode = prefs.mode()

        if (mode == Mode.VPN && VpnService.prepare(this) != null) {
            return
        }

        sendServiceAction(START_ACTION, mode)
        Log.i(TAG, "Toggle service start")
    }

    private fun restartService() {
        val mode = prefs.mode()

        if (mode == Mode.VPN && VpnService.prepare(this) != null) {
            return
        }

        sendServiceAction(RESTART_ACTION, mode)
        Log.i(TAG, "Toggle service start")
    }

    private fun stopService() {
        sendServiceAction(STOP_ACTION, appStatus.second)
        Log.i(TAG, "Toggle service stop")
    }

    private fun sendServiceAction(action: String, mode: Mode) {
        sendBroadcast(Intent(this, ServiceActionReceiver::class.java).setAction(action)
            .putExtra(ServiceActionReceiver.EXTRA_MODE, mode.name))
    }

    private fun toggleService(restart: Boolean) {
        val (status) = appStatus
        when (status) {
            AppStatus.Halted -> {
                startService()
            }
            AppStatus.Running -> {
                if (restart) {
                    restartService()
                } else {
                    stopService()
                }
            }
        }
    }

    /**
     * This activity is exported (home screen shortcuts, automation apps), so only strategies that the
     * user saved in the app can be selected here. Other apps cannot inject arbitrary arguments.
     */
    private fun updateStrategy(strategy: String?): Boolean {
        val current = prefs.getCmdArgs()
        if (strategy == null || strategy == current) return false

        if (HistoryUtils(this).getHistory().none { it.text == strategy }) {
            Log.w(TAG, "Ignoring a strategy that is not saved in the app")
            return false
        }

        prefs.edit(commit = true) {
            putString("byedpi_cmd_args", strategy)
            putBoolean("byedpi_enable_cmd_settings", true)
        }
        Log.i(TAG, "Strategy updated to: $strategy")
        return true
    }
}
