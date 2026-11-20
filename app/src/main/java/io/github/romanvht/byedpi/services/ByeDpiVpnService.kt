package io.github.romanvht.byedpi.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.MainActivity
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.receiver.ServiceActionReceiver
import io.github.romanvht.byedpi.utility.*

@SuppressLint("VpnServicePolicy")
class ByeDpiVpnService : VpnService() {
    companion object {
        internal const val FOREGROUND_SERVICE_ID = 1
        private const val PAUSE_NOTIFICATION_ID = 3
        private const val NOTIFICATION_CHANNEL_ID = "ByeDPIVpn"
        private const val TAG = "ByeDpiVpnService"
    }

    private val session by lazy { NativeSession(this, ::createTunnel) }

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(this, NOTIFICATION_CHANNEL_ID, R.string.vpn_channel_name)
    }

    override fun onDestroy() {
        super.onDestroy()
        session.destroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        val systemBinder = super.onBind(intent)
        return when (intent.action) {
            SERVICE_INTERFACE -> systemBinder
            EngineProtocol.CONTROL_ACTION -> session.binder
            else -> null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            START_ACTION -> {
                startForeground()
                session.onStarted()
            }
            null, SERVICE_INTERFACE, RESUME_ACTION -> {
                startForeground()
                session.onStarted()
                sendControl(START_ACTION)
            }
            STOP_ACTION -> {
                sendControl(STOP_ACTION)
                session.stop()
            }
            PAUSE_ACTION -> {
                sendControl(PAUSE_ACTION)
                session.stop()
            }
            else -> session.stop()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        sendControl(STOP_ACTION)
        session.stop()
    }

    private fun startForeground() {
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.cancel(PAUSE_NOTIFICATION_ID)
        val notification: Notification = createConnectionNotification(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.notification_title,
            R.string.vpn_notification_content,
            Mode.VPN,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(FOREGROUND_SERVICE_ID, notification)
        }
    }

    private fun createTunnel(configuration: Configuration): ParcelFileDescriptor {
        Log.d(TAG, "DNS: ${configuration.dns}")
        val builder = Builder()
        builder.setSession(getString(R.string.app_name))
        builder.setConfigureIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )

        builder.addAddress("10.10.10.10", 32)
            .addRoute("0.0.0.0", 0)

        if (configuration.ipv6) {
            builder.addAddress("fd00::1", 128)
                .addRoute("::", 0)
        }

        if (configuration.dns.isNotBlank()) {
            builder.addDnsServer(configuration.dns)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        when (configuration.appListType) {
            "blacklist" -> {
                for (packageName in configuration.apps) {
                    try {
                        builder.addDisallowedApplication(packageName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not exclude app $packageName from the VPN", e)
                    }
                }

                builder.addDisallowedApplication(applicationContext.packageName)
            }

            "whitelist" -> {
                for (packageName in configuration.apps) {
                    try {
                        builder.addAllowedApplication(packageName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not add app $packageName to the VPN", e)
                    }
                }
            }

            "disable" -> {
                builder.addDisallowedApplication(applicationContext.packageName)
            }
        }

        return builder.establish() ?: throw IllegalStateException("VPN connection failed")
    }

    private fun sendControl(action: String) {
        sendBroadcast(Intent(this, ServiceActionReceiver::class.java)
            .setAction(action)
            .putExtra("pid", Process.myPid())
            .putExtra(ServiceActionReceiver.EXTRA_MODE, Mode.VPN.name))
    }
}
