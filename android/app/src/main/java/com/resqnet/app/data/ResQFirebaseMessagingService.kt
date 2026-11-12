package com.resqnet.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.resqnet.app.MainActivity
import com.resqnet.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** The most recent emergency received by this device's responder. */
object EmergencyStore {
    data class EmergencyInfo(
        val requester: String,
        val latitude: Double,
        val longitude: Double,
        val distanceKm: Double,
        val receivedAt: Long
    )

    private val _active = MutableStateFlow<EmergencyInfo?>(null)
    val active: StateFlow<EmergencyInfo?> = _active

    fun post(info: EmergencyInfo) { _active.value = info }
    fun clear() { _active.value = null }
}

class ResQFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val store = SessionStore(applicationContext)
        store.saveFcmToken(token)
        val authToken = store.getAuthToken()
        if (authToken != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ResQApi.updateFcmToken(token, authToken)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val title = remoteMessage.notification?.title ?: data["title"] ?: "ResQNet SOS Alert"
        val message = remoteMessage.notification?.body ?: data["body"] ?: "Emergency assistance requested nearby!"
        val type = data["type"] ?: "sos"

        val emergencyLat = data["emergency_lat"]?.toDoubleOrNull()
        val emergencyLon = data["emergency_lon"]?.toDoubleOrNull()
        val requester = data["requester"] ?: "Someone"
        val distanceKm = data["distance_km"]?.toDoubleOrNull() ?: 0.0

        val locationLine = if (emergencyLat != null && emergencyLon != null) {
            "📍 Emergency location: %.4f, %.4f".format(Locale.US, emergencyLat, emergencyLon)
        } else null
        val fullMessage = listOfNotNull(message, locationLine).joinToString("\n")

        if (emergencyLat != null && emergencyLon != null) {
            EmergencyStore.post(
                EmergencyStore.EmergencyInfo(
                    requester = requester,
                    latitude = emergencyLat,
                    longitude = emergencyLon,
                    distanceKm = distanceKm,
                    receivedAt = System.currentTimeMillis()
                )
            )
        }

        showHighPriorityAlert(title, fullMessage, type)
    }

    private fun showHighPriorityAlert(title: String, message: String, type: String) {
        val channelId = "resqnet_sos_channel_high"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val channel = NotificationChannel(
                channelId,
                "ResQNet High-Priority Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical SOS alerts and responder emergency dispatch"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setSound(
                    alarmSound, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setBypassDnd(true)
                }
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alert_type", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_logo_mark)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
