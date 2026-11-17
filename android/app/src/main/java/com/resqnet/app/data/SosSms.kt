package com.resqnet.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.telephony.SmsManager
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.resqnet.app.MainActivity
import com.resqnet.app.R
import java.util.Locale

/** Shared screaming-alarm notification used by both FCM pushes and SOS SMS. */
object SosAlarm {
    const val CHANNEL_ID = "resqnet_sos_channel_high"

    fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val channel = NotificationChannel(
                CHANNEL_ID,
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
            manager.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context, manager)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alert_type", "sos")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_mark)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

/** Layer 2 — direct SMS fallback: victim's phone texts nearby responders with no internet. */
object SosSms {
    const val MARKER = "RESQMESH SOS:"

    fun buildMessage(name: String, latitude: Double, longitude: Double): String {
        val mapLink = "https://maps.google.com/?q=$latitude,$longitude"
        return "$MARKER $name needs help NOW! Location: $latitude,$longitude Map: $mapLink"
    }

    /** Sends the SOS SMS to every phone number and returns how many were dispatched. */
    fun send(context: Context, phones: List<String>, name: String, latitude: Double, longitude: Double): Int {
        val message = buildMessage(name, latitude, longitude)
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return 0
        var sent = 0
        val seen = mutableSetOf<String>()
        for (phone in phones) {
            val target = phone.trim()
            if (target.isEmpty() || !seen.add(target)) continue
            try {
                smsManager.sendTextMessage(target, null, message, null, null)
                sent++
            } catch (_: Exception) {
            }
        }
        return sent
    }
}

/** Receives SOS SMS on the responder's phone (works even when the internet is down). */
class SmsSosReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Modern Android strips SMS content from this broadcast for non-default
        // SMS apps (extras flag STRIPPED=1) — fall back to the SMS inbox.
        val pduBody = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.joinToString("") { it.displayMessageBody ?: "" }
            .orEmpty()
        val body = if (pduBody.contains(SosSms.MARKER)) pduBody else readLatestInbox(context).orEmpty()
        if (!body.contains(SosSms.MARKER)) return

        val requester = Regex("${SosSms.MARKER}\\s*(.+?)\\s+needs").find(body)?.groupValues?.get(1)?.take(40) ?: "Someone"
        val coordinates = Regex("(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)").find(body)?.groupValues
        val latitude = coordinates?.get(1)?.toDoubleOrNull() ?: return
        val longitude = coordinates?.get(2)?.toDoubleOrNull() ?: return

        EmergencyStore.post(
            EmergencyStore.EmergencyInfo(
                requester = requester,
                latitude = latitude,
                longitude = longitude,
                distanceKm = 0.0,
                receivedAt = System.currentTimeMillis()
            )
        )

        SosAlarm.show(
            context,
            "🚨 EMERGENCY SOS! (via SMS)",
            "$requester needs urgent help! 📍 Location: %.4f, %.4f".format(Locale.US, latitude, longitude)
        )
    }

    private fun readLatestInbox(context: Context): String? {
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                var checked = 0
                while (cursor.moveToNext() && checked < 10) {
                    checked++
                    val text = cursor.getString(0) ?: continue
                    if (text.contains(SosSms.MARKER)) return text
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
