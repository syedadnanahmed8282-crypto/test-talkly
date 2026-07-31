package com.family.talkly.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.family.talkly.MainActivity
import com.family.talkly.R
import com.family.talkly.util.TalklyNotificationHelper
import com.google.firebase.firestore.FirebaseFirestore

class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START_INCOMING_CALL = "com.family.talkly.action.START_INCOMING_CALL"
        const val ACTION_STOP_INCOMING_CALL = "com.family.talkly.action.STOP_INCOMING_CALL"
        const val ACTION_DECLINE_CALL = "com.family.talkly.action.DECLINE_CALL"

        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_UID = "caller_uid"
        const val EXTRA_CALLER_PHONE = "caller_phone"
        const val EXTRA_CALLER_AVATAR = "caller_avatar"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_CALL_TYPE = "call_type"

        fun startCallService(
            context: Context,
            callerName: String,
            callerUid: String,
            callerPhone: String,
            callerAvatar: String,
            roomId: String,
            callType: String
        ) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_INCOMING_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_UID, callerUid)
                putExtra(EXTRA_CALLER_PHONE, callerPhone)
                putExtra(EXTRA_CALLER_AVATAR, callerAvatar)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_CALL_TYPE, callType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopCallService(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP_INCOMING_CALL
            }
            context.startService(intent)
        }
    }

    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_INCOMING_CALL -> {
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Talkly User"
                val callerUid = intent.getStringExtra(EXTRA_CALLER_UID) ?: ""
                val callerPhone = intent.getStringExtra(EXTRA_CALLER_PHONE) ?: ""
                val callerAvatar = intent.getStringExtra(EXTRA_CALLER_AVATAR) ?: ""
                val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
                val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "VIDEO"

                acquireWakeLock()
                startRingtone()
                showIncomingCallNotification(
                    callerName = callerName,
                    callerUid = callerUid,
                    callerPhone = callerPhone,
                    callerAvatar = callerAvatar,
                    roomId = roomId,
                    callType = callType
                )
            }
            ACTION_DECLINE_CALL -> {
                val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
                if (roomId.isNotBlank()) {
                    declineCallInFirestore(roomId)
                }
                stopSelfAndRingtone()
            }
            ACTION_STOP_INCOMING_CALL -> {
                stopSelfAndRingtone()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Talkly:IncomingCallWakeLock"
            )
            wakeLock?.acquire(30000) // 30 seconds max
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring wake lock: ${e.localizedMessage}")
        }
    }

    private fun startRingtone() {
        try {
            stopRingtone()
            val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (defaultRingtoneUri != null) {
                ringtone = RingtoneManager.getRingtone(applicationContext, defaultRingtoneUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone?.isLooping = true
                }
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting system ringtone: ${e.localizedMessage}")
        }
    }

    private fun stopRingtone() {
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            ringtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone: ${e.localizedMessage}")
        }
    }

    private fun showIncomingCallNotification(
        callerName: String,
        callerUid: String,
        callerPhone: String,
        callerAvatar: String,
        roomId: String,
        callType: String
    ) {
        TalklyNotificationHelper.initNotificationChannels(this)

        // Intent for Answer / Fullscreen Call Intent
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_incoming_call", true)
            putExtra("caller_name", callerName)
            putExtra("caller_uid", callerUid)
            putExtra("caller_phone", callerPhone)
            putExtra("caller_avatar", callerAvatar)
            putExtra("room_id", roomId)
            putExtra("call_type", callType)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            roomId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Intent
        val declineIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_ROOM_ID, roomId)
        }

        val declinePendingIntent = PendingIntent.getService(
            this,
            (roomId + "_decline").hashCode(),
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val notificationBuilder = NotificationCompat.Builder(this, TalklyNotificationHelper.CHANNEL_CALLS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming $callType Call")
            .setContentText("$callerName is calling you")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(defaultRingtoneUri)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Answer", fullScreenPendingIntent)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun declineCallInFirestore(roomId: String) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("active_calls").document(roomId)
                .update("status", "DECLINED")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating decline call status in Firestore: ${e.localizedMessage}")
        }
    }

    private fun stopSelfAndRingtone() {
        stopRingtone()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.localizedMessage}")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfAndRingtone()
        super.onDestroy()
    }
}
