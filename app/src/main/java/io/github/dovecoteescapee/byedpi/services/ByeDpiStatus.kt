package io.github.dovecoteescapee.byedpi.services

import io.github.dovecoteescapee.byedpi.data.AppStatus
import io.github.dovecoteescapee.byedpi.data.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Volatile
var appStatus = AppStatus.Halted to Mode.VPN
    private set

private val appStatusState = MutableStateFlow(appStatus)
val appStatusFlow: StateFlow<Pair<AppStatus, Mode>> = appStatusState.asStateFlow()

fun setStatus(status: AppStatus, mode: Mode) {
    val updated = status to mode
    appStatus = updated
    appStatusState.value = updated
}
