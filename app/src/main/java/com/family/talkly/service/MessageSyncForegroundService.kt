package com.family.talkly.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.family.talkly.MainActivity
import com.family.talkly.R

class MessageSyncForegroundService : Service() {

    companion object {
        private const val TAG = "MessageSyncForegroundService"
        const val NOTIFICATION_ID = 3003
        const val CHANNEL_ID = "talkly_sync_channel"

        fun start(context: Context) {
            val intent = Intent(context, MessageSyncForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start MessageSyncForegroundService: ${e.localizedMessage}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MessageSyncForegroundService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop MessageSyncForegroundService: ${e.localizedMessage}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        ensureBackgroundSyncActive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        ensureBackgroundSyncActive()
        return START_STICKY
    }

    private fun ensureBackgroundSyncActive() {
        try {
            val sessionPrefs = getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
            val fallbackPrefs = getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: sessionPrefs.getString("user_uid", null)
                ?: fallbackPrefs.getString("user_uid", null)

            if (!uid.isNullOrBlank()) {
                val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(applicationContext)
                chatRepo.startRealtimeMessageSync(uid)

                val zegoManager = com.family.talkly.data.zego.ZegoCallEngineManager.getInstance(applicationContext)
                val userProfile = zegoManager.getLocalUserProfile()
                zegoManager.startRealtimeCallSync(userProfile, chatRepo)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error ensuring background sync active: ${e.localizedMessage}")
        }
    }

    private fun startForegroundWithNotification() {
        createChannelIfNeeded()

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Talkly")
            .setContentText("Staying connected for instant messages and calls")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentPendingIntent)
            .build()

        safeStartForeground(notification)
    }

    private fun safeStartForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Typed startForeground failed: ${e.localizedMessage}, trying untyped fallback")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Throwable) {
                Log.e(TAG, "Untyped startForeground also failed: ${e2.localizedMessage}")
            }
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val existing = manager?.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Talkly Background Sync",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Keeps Talkly connected so messages and calls arrive instantly"
                    setShowBadge(false)
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }
}
