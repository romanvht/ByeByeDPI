package io.github.romanvht.byedpi.services

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.receiver.ServiceActionReceiver
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    companion object {
        private const val TAG = "QuickTileService"
        private var instance: QuickTileService? = null

        fun updateTile() {
            instance?.updateStatus()
        }
    }

    private var appTile: Tile? = null

    override fun onStartListening() {
        super.onStartListening()

        instance = this
        appTile = qsTile

        ServiceManager.refresh(this)
        updateStatus()
    }

    override fun onStopListening() {
        super.onStopListening()

        instance = null
        appTile = null
    }

    override fun onClick() {
        super.onClick()
        ServiceManager.refresh(this)
        handleClick()
    }

    private fun handleClick() {
        val (status) = appStatus
        val mode = getPreferences().mode()

        when (status) {
            AppStatus.Halted -> startService(mode)
            AppStatus.Running -> stopService()
        }

        Log.i(TAG, "Tile clicked")
    }

    private fun startService(mode: Mode) {
        if (mode == Mode.VPN && VpnService.prepare(this) != null) {
            return
        }

        sendBroadcast(Intent(this, ServiceActionReceiver::class.java).setAction(START_ACTION)
            .putExtra(ServiceActionReceiver.EXTRA_MODE, mode.name))
    }

    private fun stopService() {
        sendBroadcast(Intent(this, ServiceActionReceiver::class.java).setAction(STOP_ACTION)
            .putExtra(ServiceActionReceiver.EXTRA_MODE, appStatus.second.name))
        setState(Tile.STATE_INACTIVE)
    }

    private fun updateStatus() {
        val (status) = appStatus

        val newState = when (status) {
            AppStatus.Running -> Tile.STATE_ACTIVE
            AppStatus.Halted -> Tile.STATE_INACTIVE
        }

        setState(newState)
    }

    private fun setState(state: Int) {
        appTile?.apply {
            this.state = state
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    if (state == Tile.STATE_ACTIVE) R.string.status_connected else R.string.status_disconnected
                )
            }
            updateTile()
        }
    }
}
