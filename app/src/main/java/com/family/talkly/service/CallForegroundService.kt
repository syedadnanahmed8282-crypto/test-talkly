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
        const val ACTION_START_ACTIVE_CALL = "com.family.talkly.action.START_ACTIVE_CALL"
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
            val prefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
            val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: prefs.getString("user_uid", null)
                ?: fallbackPrefs.getString("user_uid", null) ?: ""
            val currentPhone = prefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", null) ?: ""
            val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)
            val callerSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(callerPhone)

            val isSelfCall = (currentUid.isNotBlank() && currentUid != "self" && callerUid == currentUid) ||
                    (currentPhone.isNotBlank() && callerPhone.isNotBlank() && callerPhone == currentPhone) ||
                    (currentSuffix.isNotBlank() && callerSuffix.isNotBlank() && callerSuffix == currentSuffix)

            if (isSelfCall) {
                Log.d(TAG, "CLIENT-SIDE GUARD: Refusing to start incoming call service for self-call (callerUid=$callerUid)")
                return
            }

            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_INCOMING_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_UID, callerUid)
                putExtra(EXTRA_CALLER_PHONE, callerPhone)
                putExtra(EXTRA_CALLER_AVATAR, callerAvatar)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_CALL_TYPE, callType)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start incoming call service: ${e.localizedMessage}")
            }
        }

        fun startActiveCallService(
            context: Context,
            callerName: String,
            callType: String,
            roomId: String
        ) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_ACTIVE_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALL_TYPE, callType)
                putExtra(EXTRA_ROOM_ID, roomId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start active call service: ${e.localizedMessage}")
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

                val prefs = getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
                val fallbackPrefs = getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    ?: prefs.getString("user_uid", null)
                    ?: fallbackPrefs.getString("user_uid", null) ?: ""
                val currentPhone = prefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", null) ?: ""
                val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)
                val callerSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(callerPhone)

                val isSelfCall = (currentUid.isNotBlank() && currentUid != "self" && callerUid == currentUid) ||
                        (currentPhone.isNotBlank() && callerPhone.isNotBlank() && callerPhone == currentPhone) ||
                        (currentSuffix.isNotBlank() && callerSuffix.isNotBlank() && callerSuffix == currentSuffix)

                if (isSelfCall) {
                    Log.d(TAG, "Ignoring ACTION_START_INCOMING_CALL for self-call (callerUid=$callerUid)")
                    stopSelf()
                    return START_NOT_STICKY
                }

                acquireFullWakeLock()
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
            ACTION_START_ACTIVE_CALL -> {
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Talkly User"
                val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "AUDIO"
                val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""

                stopRingtone()
                acquirePartialWakeLock()
                showActiveCallNotification(
                    callerName = callerName,
                    callType = callType,
                    roomId = roomId
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

    private fun acquireFullWakeLock() {
        try {
            releaseWakeLock()
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Talkly:IncomingCallWakeLock"
            )
            wakeLock?.acquire(30000) // 30 seconds max
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring full wake lock: ${e.localizedMessage}")
        }
    }

    private fun acquirePartialWakeLock() {
        try {
            releaseWakeLock()
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Talkly:ActiveCallWakeLock"
            )
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max for ongoing call
            Log.d(TAG, "Acquired PARTIAL_WAKE_LOCK for active call")
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring partial wake lock: ${e.localizedMessage}")
        }
    }

    private fun releaseWakeLock() {
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

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        } else {
            0
        }

        safeStartForeground(NOTIFICATION_ID, notificationBuilder.build(), foregroundType)
    }

    private fun safeStartForeground(notificationId: Int, notification: android.app.Notification, primaryType: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && primaryType != 0) {
                startForeground(notificationId, notification, primaryType)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed with type $primaryType: ${e.localizedMessage}, attempting fallback types")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(notificationId, notification)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback startForeground with MICROPHONE/DATA_SYNC failed: ${e2.localizedMessage}")
                try {
                    startForeground(notificationId, notification)
                } catch (e3: Exception) {
                    Log.e(TAG, "Untyped startForeground failed: ${e3.localizedMessage}")
                }
            }
        }
    }

    private fun showActiveCallNotification(
        callerName: String,
        callType: String,
        roomId: String
    ) {
        TalklyNotificationHelper.initNotificationChannels(this)

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_active_call", true)
            putExtra("room_id", roomId)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            roomId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, TalklyNotificationHelper.CHANNEL_CALLS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ongoing $callType Call")
            .setContentText("In call with $callerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setSound(null)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        } else {
            0
        }

        safeStartForeground(NOTIFICATION_ID, notificationBuilder.build(), foregroundType)
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
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfAndRingtone()
        super.onDestroy()
    }
}
