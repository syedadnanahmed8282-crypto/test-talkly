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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    private var syncJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        observeAuthState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        observeAuthState()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        serviceScope.cancel()
    }

    private fun observeAuthState() {
        if (syncJob?.isActive == true) return
        syncJob = serviceScope.launch {
            try {
                val authManager = com.family.talkly.data.auth.AuthManager.getInstance(applicationContext)
                authManager.authState.collect { state ->
                    when (state) {
                        is com.family.talkly.data.auth.AuthState.Authenticated -> {
                            val uid = state.profile.uid
                            if (uid.isNotBlank() && uid != "self") {
                                Log.d(TAG, "Auth verified -> activating background sync for UID: $uid")
                                val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(applicationContext)
                                chatRepo.startRealtimeMessageSync(uid)

                                val zegoManager = com.family.talkly.data.zego.ZegoCallEngineManager.getInstance(applicationContext)
                                zegoManager.startRealtimeCallSync(state.profile, chatRepo)
                            }
                        }
                        is com.family.talkly.data.auth.AuthState.Unauthenticated -> {
                            Log.d(TAG, "Unauthenticated state observed -> skipping realtime background channels")
                        }
                        else -> {
                            Log.d(TAG, "Auth state is $state, waiting for authoritative session...")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in observeAuthState: ${e.localizedMessage}")
            }
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
            .setContentTitle("Payra")
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
                    "Payra Background Sync",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Keeps Payra connected so messages and calls arrive instantly"
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
