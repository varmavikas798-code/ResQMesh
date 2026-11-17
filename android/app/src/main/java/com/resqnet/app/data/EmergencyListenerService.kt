package com.resqnet.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.resqnet.app.R

/**
 * Keeps the app process alive so emergency alarms are never deferred:
 * - SMS SOS receiver keeps working when the app is in the background
 * - FCM push service keeps working
 * - Bluetooth mesh scanning keeps running continuously
 */
class EmergencyListenerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        SosMesh.startListening(this)
        return START_STICKY
    }

    private fun startInForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency listener",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Keeps ResQNet ready to receive emergency SOS alerts" }
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_mark)
            .setContentTitle("ResQNet emergency listener active")
            .setContentText("Watching for SOS alerts from nearby people in danger")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "resqnet_listener_channel"
        const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, EmergencyListenerService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
