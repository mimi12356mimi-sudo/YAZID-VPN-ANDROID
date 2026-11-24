
package com.yazid.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class YazidVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.yazid.vpn.START"
        const val ACTION_STOP = "com.yazid.vpn.STOP"
        const val EXTRA_CONFIG = "xray_config"

        private const val TAG = "YAZID_VPN"
        private const val CHANNEL_ID = "yazid_vpn_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Int {
            Log.i(TAG, "Xray started")
            return 0
        }

        override fun shutdown(): Int {
            Log.i(TAG, "Xray stopped")
            return 0
        }

        override fun onEmitStatus(code: Int, message: String): Int {
            Log.i(TAG, "Xray status: $code - $message")
            return 0
        }
    }

    override fun onCreate() {
        super.onCreate()

        try {
            Seq.setContext(applicationContext)
            Libv2ray.initCoreEnv(filesDir.absolutePath, "")
            coreController = Libv2ray.newCoreController(callback)
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Xray initialization failed", e)
            stopSelf()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification()
                )

                val config = intent.getStringExtra(EXTRA_CONFIG)

                if (config.isNullOrBlank()) {
                    Log.e(TAG, "Xray configuration is empty")
                    stopVpn()
                    return START_NOT_STICKY
                }

                startVpn(config)
            }
        }

        return START_NOT_STICKY
    }

    private fun startVpn(config: String) {
        try {
            if (coreController?.isRunning == true) {
                Log.i(TAG, "VPN is already running")
                return
            }

            val builder = Builder()
                .setSession("YAZID VPN")
                .setMtu(1500)
                .addAddress("10.111.0.1", 30)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)

            vpnInterface = builder.establish()
                ?: throw IllegalStateException("Failed to establish VPN interface")

            val fd = vpnInterface!!.fd

            val controller = coreController
                ?: throw IllegalStateException("Xray controller is unavailable")

            controller.startLoop(config, fd)

            if (!controller.isRunning) {
                throw IllegalStateException("Xray core failed to start")
            }

            Log.i(TAG, "YAZID VPN connected successfully")

        } catch (e: Exception) {
            Log.e(TAG, "VPN startup failed", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Xray shutdown error", e)
        }

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "VPN interface close error", e)
        }

        vpnInterface = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YAZID VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "YAZID VPN connection status"
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("YAZID VPN")
            .setContentText("VPN service is running")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Destroy error", e)
        }

        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }

        vpnInterface = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) =
        super.onBind(intent)
}
