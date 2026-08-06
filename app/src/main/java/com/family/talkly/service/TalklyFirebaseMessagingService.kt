package com.family.talkly.service

import android.content.Context
import android.util.Log
import com.family.talkly.util.FcmTokenManager
import com.family.talkly.util.TalklyNotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TalklyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "Talkly_FCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received: $token")
        FcmTokenManager.syncFcmToken(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        // Acquire partial wake lock to guarantee CPU remains active while processing payload
        var wakeLock: android.os.PowerManager.WakeLock? = null
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = powerManager?.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "Talkly:FcmMessageWakeLock"
            )
            wakeLock?.acquire(5000) // 5 seconds max
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire FCM wake lock: ${e.localizedMessage}")
        }

        try {
            val data = remoteMessage.data
            if (data.isEmpty()) {
                remoteMessage.notification?.let { notification ->
                    val title = notification.title ?: "Talkly"
                    val body = notification.body ?: "New notification"
                    TalklyNotificationHelper.postIncomingMessageNotification(
                        context = applicationContext,
                        senderName = title,
                        messageText = body
                    )
                }
                return
            }

            val payloadType = data["type"] ?: data["eventType"] ?: ""
            Log.d(TAG, "FCM Payload type: $payloadType, data: $data")

            when {
                payloadType.equals("INCOMING_CALL", ignoreCase = true) ||
                payloadType.equals("call", ignoreCase = true) ||
                data.containsKey("roomID") ||
                data.containsKey("callerUid") -> {

                    val callerName = data["callerName"] ?: "Talkly User"
                    val callerUid = data["callerUid"] ?: ""
                    val callerPhone = data["callerPhone"] ?: ""
                    val callerAvatar = data["callerAvatarUrl"] ?: data["callerAvatar"] ?: ""
                    val roomId = data["roomID"] ?: data["roomId"] ?: ""
                    val callType = data["callType"] ?: "VIDEO"
                    val callStatus = data["status"] ?: "RINGING"

                    // Client-Side Call Receiver Validation: Discard self-call event
                    val prefs = applicationContext.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
                    val fallbackPrefs = applicationContext.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
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
                        Log.d(TAG, "Ignoring self-call FCM push notification (callerUid=$callerUid matches current user)")
                        return
                    }

                    when (callStatus.uppercase()) {
                        "RINGING" -> {
                            CallForegroundService.startCallService(
                                context = applicationContext,
                                callerName = callerName,
                                callerUid = callerUid,
                                callerPhone = callerPhone,
                                callerAvatar = callerAvatar,
                                roomId = roomId,
                                callType = callType
                            )
                        }
                        "ENDED", "DECLINED", "CANCELLED", "TIMED_OUT" -> {
                            CallForegroundService.stopCallService(applicationContext)
                        }
                    }
                }

                payloadType.equals("CHAT_MESSAGE", ignoreCase = true) ||
                payloadType.equals("message", ignoreCase = true) ||
                data.containsKey("messageText") ||
                data.containsKey("chatMemberId") -> {

                    val senderName = data["senderName"] ?: "Talkly Message"
                    val messageText = data["messageText"] ?: "New message"
                    val chatMemberId = data["chatMemberId"] ?: data["senderUid"] ?: ""
                    val messageId = data["messageId"] ?: data["id"] ?: ""

                    TalklyNotificationHelper.postIncomingMessageNotification(
                        context = applicationContext,
                        senderName = senderName,
                        messageText = messageText,
                        chatMemberId = chatMemberId,
                        messageId = messageId
                    )
                }

                else -> {
                    // Fallback for general notification
                    val title = data["title"] ?: data["senderName"] ?: "Talkly"
                    val body = data["body"] ?: data["messageText"] ?: "New message"
                    TalklyNotificationHelper.postIncomingMessageNotification(
                        context = applicationContext,
                        senderName = title,
                        messageText = body
                    )
                }
            }
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing FCM wake lock: ${e.localizedMessage}")
            }
        }
    }
}
