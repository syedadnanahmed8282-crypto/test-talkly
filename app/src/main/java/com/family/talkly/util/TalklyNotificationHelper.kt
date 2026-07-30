package com.family.talkly.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.family.talkly.MainActivity
import com.family.talkly.R

object TalklyNotificationHelper {

    const val CHANNEL_MESSAGES_ID = "talkly_messages_channel"
    const val CHANNEL_CALLS_ID = "talkly_calls_channel"
    private const val TAG = "Talkly_NotificationHelper"

    /**
     * Initializes notification channels with the user's system default notification and ringtone sounds.
     */
    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Incoming Message Notification Channel strictly using system default notification tone
            val defaultNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributesNotification = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming chat message notifications"
                setSound(defaultNotificationUri, audioAttributesNotification)
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(messagesChannel)

            // 2. Incoming Call Notification Channel strictly using system default phone ringtone
            val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributesRingtone = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val callsChannel = NotificationChannel(
                CHANNEL_CALLS_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming video and voice call notifications"
                setSound(defaultRingtoneUri, audioAttributesRingtone)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(callsChannel)

            Log.d(TAG, "Notification channels initialized with system default sounds.")
        }
    }

    /**
     * Plays the user's system default notification tone for instant feedback.
     */
    fun playSystemNotificationSound(context: Context) {
        try {
            val defaultNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (defaultNotificationUri != null) {
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, defaultNotificationUri)
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error playing default notification sound: ${e.localizedMessage}")
        }
    }

    /**
     * Posts a notification for incoming chat messages using the Messages channel and system default tone.
     */
    fun postIncomingMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        chatMemberId: String = ""
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_chat_member_id", chatMemberId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                chatMemberId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val defaultNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(defaultNotificationUri)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(chatMemberId.hashCode(), builder.build())

            // Play system default notification tone
            playSystemNotificationSound(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting message notification: ${e.localizedMessage}")
        }
    }
}
