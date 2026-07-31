package com.family.talkly.service

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

                TalklyNotificationHelper.postIncomingMessageNotification(
                    context = applicationContext,
                    senderName = senderName,
                    messageText = messageText,
                    chatMemberId = chatMemberId
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
    }
}
