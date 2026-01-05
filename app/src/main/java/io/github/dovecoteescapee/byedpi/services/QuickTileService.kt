package io.github.dovecoteescapee.byedpi.services

import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import io.github.dovecoteescapee.byedpi.utility.mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    companion object {
        private const val TAG = "QuickTileService"
    }

    private var appTile: Tile? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        Log.i(TAG, "Tile added")
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.i(TAG, "Tile removed")
    }

    override fun onStartListening() {
        super.onStartListening()
        appTile = qsTile
        updateStatus(appStatusFlow.value.first)
        statusJob?.cancel()
        statusJob = serviceScope.launch {
            appStatusFlow.collect { (status) ->
                updateStatus(status)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        statusJob?.cancel()
        statusJob = null
        appTile = null
    }

    override fun onClick() {
        super.onClick()
        handleClick()
    }

    private fun handleClick() {
        val (status) = appStatusFlow.value

        when (status) {
            AppStatus.Halted -> {
                val mode = getPreferences().mode()

                if (mode == Mode.VPN && VpnService.prepare(this) != null) {
                    return
                }

                ServiceManager.start(this, mode)
                setState(Tile.STATE_ACTIVE)
            }
            AppStatus.Running -> {
                ServiceManager.stop(this)
                setState(Tile.STATE_INACTIVE)
            }
        }

        Log.i(TAG, "Toggle tile")
    }

    private fun updateStatus(status: AppStatus) {
        if (status == AppStatus.Running) {
            setState(Tile.STATE_ACTIVE)
        } else {
            setState(Tile.STATE_INACTIVE)
        }
    }

    private fun setState(newState: Int) {
        appTile?.apply {
            state = newState
            updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
