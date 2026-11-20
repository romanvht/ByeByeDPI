package io.github.romanvht.byedpi.services

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.utility.createPauseNotification
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import io.github.romanvht.byedpi.utility.registerNotificationChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

object ServiceManager {
    private const val TAG = "ServiceManager"
    private const val PAUSE_NOTIFICATION_ID = 3
    private const val APPLIED_CONFIGURATION = "applied_configuration"
    private val scope = CoroutineScope(
        Dispatchers.Main.immediate + SupervisorJob() + CoroutineExceptionHandler {
            _, error -> Log.e(TAG, "Service command failed", error)
        }
    )
    private val commands = AtomicLong()
    private val mutex = Mutex()
    private var application: Application? = null
    private var session: Session? = null

    private class Session(val mode: Mode, val engine: NativeEngine, val restored: Boolean = false) {
        val configuration = CompletableDeferred<Configuration>()
        val configured = CompletableDeferred<Boolean>()
        val started = CompletableDeferred<Boolean>()
        val stopped = CompletableDeferred<Unit>()
        var stopping = false
        var job: Job? = null
    }

    /** True while a session started from this app is still coming up. */
    val isStarting: Boolean
        get() = session?.let { !it.restored && !it.stopping && !it.started.isCompleted } ?: false

    /** True while the running session is shutting down. */
    val isStopping: Boolean
        get() = session?.stopping == true

    /**
     * True when the settings were changed after the running session was configured,
     * so a reconnect is needed for them to take effect.
     */
    fun isConfigurationOutdated(context: Context): Boolean {
        val current = session ?: return false
        if (current.stopping) return false
        val preferences = context.getPreferences()
        val applied = preferences.getString(APPLIED_CONFIGURATION, null) ?: return false
        val wanted = runCatching {
            fingerprint(preferences.mode(), NativeEngine.configuration(context))
        }.getOrNull() ?: return false
        return applied != wanted
    }

    private fun fingerprint(mode: Mode, configuration: Configuration): String =
        "${mode.name}:${configuration.hashCode()}"

    fun start(context: Context, mode: Mode) {
        val app = context.applicationContext as Application
        scope.launch { waitStart(app, mode) }
    }

    suspend fun waitStart(context: Context, mode: Mode): Boolean {
        val app = context.applicationContext as Application
        val command = commands.incrementAndGet()
        return withContext(Dispatchers.Main.immediate) {
            startSession(app, mode, command)
        }
    }

    fun stop() {
        scope.launch { waitStop() }
    }

    suspend fun waitStop() {
        commands.incrementAndGet()
        withContext(Dispatchers.Main.immediate + NonCancellable) {
            application?.let {
                restore(it)
                (it.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(PAUSE_NOTIFICATION_ID)
            }
            val current = session ?: return@withContext
            stopSession(current)
        }
    }

    fun restart(context: Context, mode: Mode) {
        val app = context.applicationContext as Application
        val command = commands.incrementAndGet()
        scope.launch {
            if (TestService.isRunning) return@launch
            restore(app)
            session?.let { stopSession(it) }
            if (commands.get() == command) startSession(app, mode, command)
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext as Application
        scope.launch { restore(app) }
    }

    suspend fun handleAction(context: Context, action: String, mode: Mode, sourcePid: Int = 0) {
        val app = context.applicationContext as Application
        withContext(Dispatchers.Main.immediate) {
            if (TestService.isRunning) return@withContext
            restore(app)
            val activePid = session?.engine?.processId?.takeIf { it > 0 } ?: NativeEngine.running(app)?.second
            if (sourcePid > 0 && sourcePid != activePid) return@withContext
            if (sourcePid > 0 && (action == STOP_ACTION || action == PAUSE_ACTION) && session?.mode != mode) {
                return@withContext
            }
            when (action) {
                START_ACTION, RESUME_ACTION, RESTART_ACTION -> {
                    if (mode != Mode.VPN || VpnService.prepare(app) == null) {
                        val configured = CompletableDeferred<Boolean>()
                        val command = commands.incrementAndGet()
                        val startup = scope.launch {
                            try {
                                if (action == RESTART_ACTION) {
                                    session?.let { stopSession(it) }
                                }
                                startSession(app, mode, command, configured)
                            } finally {
                                configured.complete(false)
                            }
                        }
                        if (withTimeoutOrNull(8_000) { configured.await() } != true) {
                            startup.cancel()
                            if (commands.get() == command) {
                                session?.let {
                                    stopSession(it, await = false)
                                    it.engine.abort()
                                }
                                waitStop()
                            }
                        }
                    } else if (session?.mode == mode) {
                        waitStop()
                    }
                }
                STOP_ACTION, PAUSE_ACTION -> {
                    val activeMode = session?.mode ?: mode
                    waitStop()
                    if (session != null || TestService.isRunning) return@withContext
                    if (action == PAUSE_ACTION) {
                        val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val channel = if (activeMode == Mode.VPN) "ByeDPIVpn" else "ByeDPI Proxy"
                        val name = if (activeMode == Mode.VPN) R.string.vpn_channel_name else R.string.proxy_channel_name
                        registerNotificationChannel(app, channel, name)
                        notifications.notify(PAUSE_NOTIFICATION_ID, createPauseNotification(
                            app, channel, R.string.notification_title, R.string.service_paused_text, activeMode,
                        ))
                    }
                }
            }
        }
    }

    private suspend fun startSession(
        app: Application,
        mode: Mode,
        command: Long,
        configured: CompletableDeferred<Boolean>? = null,
    ): Boolean = mutex.withLock {
        if (TestService.isRunning || commands.get() != command) return@withLock false
        restore(app)
        session?.let { previous ->
            if (!previous.stopping && previous.mode == mode) {
                configure(previous, app)
                configured?.complete(previous.configured.await())
                return@withLock previous.started.await()
            }
            stopSession(previous)
        }
        if (TestService.isRunning || commands.get() != command) return@withLock false

        val current = Session(mode, NativeEngine(app, mode))
        session = current
        configure(current, app)
        publish(mode, AppStatus.Running, STARTED_BROADCAST)
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(PAUSE_NOTIFICATION_ID)
        monitor(current, launchService = true)
        try {
            configured?.complete(current.configured.await())
            current.started.await()
        } catch (e: CancellationException) {
            stopSession(current, await = false)
            throw e
        }
    }

    private suspend fun stopSession(current: Session, await: Boolean = true) {
        if (!current.stopping) {
            current.stopping = true
            current.started.complete(false)
            current.job?.cancel()
        }
        if (await) current.stopped.await()
    }

    private fun configure(current: Session, app: Application) {
        if (current.configuration.isCompleted || current.started.isCompleted) return
        try {
            val configuration = NativeEngine.configuration(app)
            app.getPreferences().edit { putString(APPLIED_CONFIGURATION, fingerprint(current.mode, configuration)) }
            current.configuration.complete(configuration)
        } catch (e: Exception) {
            current.configuration.completeExceptionally(e)
        }
    }

    private fun restore(app: Application) {
        application = app
        if (session != null || TestService.isRunning) return
        val running = NativeEngine.running(app)
        if (running == null) {
            app.stopService(Intent(app, ByeDpiVpnService::class.java))
            app.stopService(Intent(app, ByeDpiProxyService::class.java))
            val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifications.cancel(ByeDpiVpnService.FOREGROUND_SERVICE_ID)
            notifications.cancel(ByeDpiProxyService.FOREGROUND_SERVICE_ID)
            if (appStatus.first != AppStatus.Halted) publish(appStatus.second, AppStatus.Halted, STOPPED_BROADCAST)
            return
        }
        val current = Session(running.first, NativeEngine(app, running.first, running.second), restored = true)
        session = current
        publish(current.mode, AppStatus.Running, STARTED_BROADCAST)
        monitor(current, launchService = false)
    }

    private fun monitor(current: Session, launchService: Boolean) {
        current.engine.onStopping = {
            if (session === current && !current.stopping) stop()
        }
        current.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var failed = false
            try {
                current.engine.connect(launchService)
                coroutineScope {
                    launch {
                        current.engine.awaitConfigured()
                        current.configured.complete(true)
                    }
                    val ready = async { current.engine.awaitStarted() }
                    try {
                        if (current.engine.needsConfiguration) {
                            select {
                                ready.onAwait { }
                                current.configuration.onAwait { current.engine.start(it) }
                            }
                        } else {
                            ready.await()
                        }
                    } finally {
                        ready.cancel()
                    }
                }
                if (!current.stopping) {
                    publish(current.mode, AppStatus.Running, STARTED_BROADCAST)
                    current.started.complete(true)
                }
                current.engine.awaitExit()
            } catch (_: CancellationException) {
                failed = !current.stopping
            } catch (e: Exception) {
                failed = !current.stopping
                if (failed) Log.e(TAG, "Native service failed", e)
            } finally {
                current.configured.complete(false)
                withContext(NonCancellable) {
                    try {
                        current.engine.stop()
                        finish(current, failed && !current.stopping)
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not confirm native process exit", e)
                        current.started.complete(false)
                        current.stopped.completeExceptionally(e)
                        publish(current.mode, AppStatus.Halted, FAILED_BROADCAST)
                        scope.launch {
                            current.engine.awaitStopped()
                            finish(current, failed = true)
                        }
                    }
                }
            }
        }
    }

    private fun finish(current: Session, failed: Boolean) {
        current.engine.onStopping = null
        if (session === current) {
            session = null
            publish(current.mode, AppStatus.Halted, if (failed) FAILED_BROADCAST else STOPPED_BROADCAST)
        }
        current.started.complete(false)
        current.stopped.complete(Unit)
    }

    private fun publish(mode: Mode, status: AppStatus, action: String) {
        setStatus(status, mode)
        val sender = if (mode == Mode.VPN) Sender.VPN else Sender.Proxy
        application?.let {
            it.sendBroadcast(Intent(action).setPackage(it.packageName).putExtra(SENDER, sender.ordinal))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) QuickTileService.updateTile()
    }
}
