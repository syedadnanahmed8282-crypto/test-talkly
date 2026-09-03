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
                    val rawTitle = notification.title
                    val body = notification.body ?: "New notification"
                    val resolvedTitle = TalklyNotificationHelper.resolveSenderDisplayName(
                        context = applicationContext,
                        candidateSenderName = rawTitle
                    )
                    val messageId = remoteMessage.messageId ?: ""
                    TalklyNotificationHelper.postIncomingMessageNotification(
                        context = applicationContext,
                        senderName = resolvedTitle,
                        messageText = body,
                        messageId = messageId,
                        timestamp = remoteMessage.sentTime
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
                    val callerUid = data["caller_id"] ?: data["callerId"] ?: data["callerUid"] ?: data["caller_uid"] ?: ""
                    val callerPhone = data["callerPhone"] ?: data["caller_phone"] ?: ""
                    val callerAvatar = data["callerAvatarUrl"] ?: data["callerAvatar"] ?: ""
                    val roomId = data["roomID"] ?: data["roomId"] ?: ""
                    val callType = data["callType"] ?: "VIDEO"
                    val callStatus = data["status"] ?: "RINGING"

                    // Client-Side Call Receiver Validation: Discard self-call event
                    val prefs = applicationContext.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
                    val fallbackPrefs = applicationContext.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
                    val currentUid = com.family.talkly.data.supabase.SupabaseClientProvider.auth.currentUserOrNull()?.id
                        ?: prefs.getString("user_uid", null)
                        ?: fallbackPrefs.getString("user_uid", null) ?: ""
                    val currentPhone = prefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", null) ?: ""
                    val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)
                    val callerSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(callerPhone)

                    val isSelfCall = (currentUid.isNotBlank() && currentUid != "self" && callerUid == currentUid) ||
                            (currentPhone.isNotBlank() && callerPhone.isNotBlank() && callerPhone == currentPhone) ||
                            (currentSuffix.isNotBlank() && callerSuffix.isNotBlank() && callerSuffix == currentSuffix)

                    if (isSelfCall) {
                        Log.d(TAG, "CLIENT-SIDE GUARD: Discarding self-call FCM push notification (callerUid=$callerUid, currentUid=$currentUid)")
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
                        "ENDED", "DECLINED", "CANCELLED", "TIMED_OUT", "ACCEPTED", "ANSWERED", "PEER_ANSWERED" -> {
                            CallForegroundService.stopCallService(applicationContext)
                        }
                    }
                }

                payloadType.equals("CHAT_MESSAGE", ignoreCase = true) ||
                payloadType.equals("message", ignoreCase = true) ||
                data.containsKey("messageText") ||
                data.containsKey("chatMemberId") -> {

                    val rawSenderName = data["senderName"] ?: data["sender_name"] ?: ""
                    val messageText = data["messageText"] ?: data["text"] ?: data["content"] ?: data["body"] ?: "New message"
                    val senderUid = data["senderUid"] ?: data["sender_id"] ?: data["sender_uid"] ?: ""
                    val senderPhone = data["senderPhone"] ?: data["sender_phone"] ?: ""
                    val chatMemberId = data["chatMemberId"] ?: senderUid
                    val conversationId = data["conversationId"] ?: data["conversation_id"] ?: ""
                    val messageId = data["messageId"] ?: data["id"] ?: data["message_id"] ?: remoteMessage.messageId ?: ""
                    val timestamp = try {
                        data["timestamp"]?.toLongOrNull() ?: remoteMessage.sentTime
                    } catch (e: Exception) {
                        remoteMessage.sentTime
                    }

                    val senderName = TalklyNotificationHelper.resolveSenderDisplayName(
                        context = applicationContext,
                        candidateSenderName = rawSenderName,
                        chatMemberId = chatMemberId,
                        senderUid = senderUid,
                        senderPhone = senderPhone
                    )

                    TalklyNotificationHelper.postIncomingMessageNotification(
                        context = applicationContext,
                        senderName = senderName,
                        messageText = messageText,
                        chatMemberId = chatMemberId,
                        senderUid = senderUid,
                        senderPhone = senderPhone,
                        conversationId = conversationId,
                        messageId = messageId,
                        timestamp = timestamp
                    )
                }

                else -> {
                    // Fallback for general notification
                    val rawTitle = data["title"] ?: data["senderName"] ?: data["sender_name"] ?: ""
                    val body = data["body"] ?: data["messageText"] ?: "New message"
                    val senderUid = data["senderUid"] ?: data["sender_id"] ?: data["sender_uid"] ?: ""
                    val senderPhone = data["senderPhone"] ?: data["sender_phone"] ?: ""
                    val chatMemberId = data["chatMemberId"] ?: senderUid
                    val messageId = data["messageId"] ?: data["id"] ?: data["message_id"] ?: remoteMessage.messageId ?: ""
                    val timestamp = try {
                        data["timestamp"]?.toLongOrNull() ?: remoteMessage.sentTime
                    } catch (e: Exception) {
                        remoteMessage.sentTime
                    }

                    val resolvedTitle = TalklyNotificationHelper.resolveSenderDisplayName(
                        context = applicationContext,
                        candidateSenderName = rawTitle,
                        chatMemberId = chatMemberId,
                        senderUid = senderUid,
                        senderPhone = senderPhone
                    )

                    TalklyNotificationHelper.postIncomingMessageNotification(
                        context = applicationContext,
                        senderName = resolvedTitle,
                        messageText = body,
                        chatMemberId = chatMemberId,
                        senderUid = senderUid,
                        senderPhone = senderPhone,
                        messageId = messageId,
                        timestamp = timestamp
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
