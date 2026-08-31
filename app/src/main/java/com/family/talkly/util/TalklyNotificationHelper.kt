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
    const val CHANNEL_ONGOING_CALLS_ID = "talkly_ongoing_calls_channel"
    const val SUMMARY_NOTIFICATION_ID = 99999
    private const val TAG = "Talkly_NotificationHelper"

    data class ActiveChatInfo(
        val memberId: String,
        val firebaseUid: String? = null,
        val phone: String? = null,
        val phoneSuffix: String? = null,
        var isResumed: Boolean = true
    )

    @Volatile
    private var currentActiveChat: ActiveChatInfo? = null

    var activeChatMemberId: String?
        get() = if (currentActiveChat?.isResumed == true) currentActiveChat?.memberId else null
        set(value) {
            currentActiveChat = if (value != null) {
                ActiveChatInfo(memberId = value, isResumed = true)
            } else {
                null
            }
        }

    fun setActiveChat(
        memberId: String,
        firebaseUid: String? = null,
        phone: String? = null,
        isResumed: Boolean = true
    ) {
        val suffix = phone?.let { PhoneUtils.extractPhoneSuffix(it) }?.takeIf { it.isNotBlank() }
        currentActiveChat = ActiveChatInfo(
            memberId = memberId,
            firebaseUid = firebaseUid?.takeIf { it.isNotBlank() },
            phone = phone?.takeIf { it.isNotBlank() },
            phoneSuffix = suffix,
            isResumed = isResumed
        )
        Log.d(TAG, "Active chat set: memberId=$memberId, uid=$firebaseUid, phone=$phone, isResumed=$isResumed")
    }

    fun updateActiveChatLifecycle(isResumed: Boolean) {
        currentActiveChat?.let {
            it.isResumed = isResumed
            Log.d(TAG, "Active chat lifecycle updated: memberId=${it.memberId}, isResumed=$isResumed")
        }
    }

    fun clearActiveChat(memberId: String? = null) {
        if (memberId == null || currentActiveChat?.memberId == memberId || currentActiveChat?.firebaseUid == memberId) {
            Log.d(TAG, "Active chat cleared (was: ${currentActiveChat?.memberId})")
            currentActiveChat = null
        }
    }

    fun isConversationActive(
        candidateMemberId: String = "",
        candidateSenderUid: String = "",
        candidateSenderPhone: String = "",
        candidateConversationId: String = "",
        context: Context? = null
    ): Boolean {
        val active = currentActiveChat ?: return false
        if (!active.isResumed) {
            return false
        }

        val incomingIds = mutableSetOf<String>()
        if (candidateMemberId.isNotBlank()) {
            incomingIds.add(candidateMemberId)
            val s = PhoneUtils.extractPhoneSuffix(candidateMemberId)
            if (s.isNotBlank()) incomingIds.add(s)
        }
        if (candidateSenderUid.isNotBlank()) {
            incomingIds.add(candidateSenderUid)
            val s = PhoneUtils.extractPhoneSuffix(candidateSenderUid)
            if (s.isNotBlank()) incomingIds.add(s)
        }
        if (candidateSenderPhone.isNotBlank()) {
            incomingIds.add(candidateSenderPhone)
            val s = PhoneUtils.extractPhoneSuffix(candidateSenderPhone)
            if (s.isNotBlank()) incomingIds.add(s)
        }

        // Direct matching with active chat properties
        if (incomingIds.contains(active.memberId)) return true
        if (!active.firebaseUid.isNullOrBlank() && incomingIds.contains(active.firebaseUid)) return true
        if (!active.phone.isNullOrBlank() && incomingIds.contains(active.phone)) return true
        if (!active.phoneSuffix.isNullOrBlank() && incomingIds.contains(active.phoneSuffix)) return true

        // Suffix-level comparison
        if (!active.phoneSuffix.isNullOrBlank()) {
            for (id in incomingIds) {
                val suffix = PhoneUtils.extractPhoneSuffix(id)
                if (suffix.isNotBlank() && suffix == active.phoneSuffix) {
                    return true
                }
            }
        }

        // Check against known repository contacts if context is provided
        if (context != null) {
            try {
                val members = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context).familyMembers.value
                val matchedMember = members.firstOrNull { m ->
                    incomingIds.contains(m.id) ||
                    (!m.firebaseUid.isNullOrBlank() && incomingIds.contains(m.firebaseUid)) ||
                    (m.phone.isNotBlank() && incomingIds.contains(m.phone)) ||
                    (m.phone.isNotBlank() && incomingIds.contains(PhoneUtils.extractPhoneSuffix(m.phone)))
                }
                if (matchedMember != null) {
                    if (matchedMember.id == active.memberId) return true
                    if (!matchedMember.firebaseUid.isNullOrBlank() && matchedMember.firebaseUid == active.firebaseUid) return true
                    val mSuffix = PhoneUtils.extractPhoneSuffix(matchedMember.phone)
                    if (mSuffix.isNotBlank() && mSuffix == active.phoneSuffix) return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Note: checking contacts repository: ${e.localizedMessage}")
            }
        }

        return false
    }

    private val inMemoryProcessedMessageIds = HashSet<String>()

    @Synchronized
    fun isMessageProcessed(context: Context, messageId: String): Boolean {
        if (messageId.isBlank()) return false
        if (inMemoryProcessedMessageIds.contains(messageId)) return true
        val prefs = context.getSharedPreferences("talkly_processed_notifications", Context.MODE_PRIVATE)
        val processed = prefs.getBoolean("msg_$messageId", false)
        if (processed) {
            inMemoryProcessedMessageIds.add(messageId)
        }
        return processed
    }

    @Synchronized
    fun markMessageProcessed(context: Context, messageId: String) {
        if (messageId.isBlank()) return
        inMemoryProcessedMessageIds.add(messageId)
        try {
            val prefs = context.getSharedPreferences("talkly_processed_notifications", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("msg_$messageId", true).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist processed message ID $messageId: ${e.localizedMessage}")
        }
    }

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

            // 3. Ongoing Call Notification Channel strictly silent
            val ongoingCallsChannel = NotificationChannel(
                CHANNEL_ONGOING_CALLS_ID,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing active call status notifications"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(ongoingCallsChannel)

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
     * Posts a SINGLE aggregated summary notification for batch missed messages.
     */
    fun postSummaryNotification(
        context: Context,
        totalUnreadCount: Int,
        chatCount: Int
    ) {
        if (totalUnreadCount <= 0) return
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                SUMMARY_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val defaultNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val title = "Talkly Messages"
            val text = if (chatCount > 1) {
                "$totalUnreadCount new messages from $chatCount chats"
            } else {
                "$totalUnreadCount new messages"
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(defaultNotificationUri)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(SUMMARY_NOTIFICATION_ID, builder.build())

            playSystemNotificationSound(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting summary notification: ${e.localizedMessage}")
        }
    }

    /**
     * Posts a notification for incoming chat messages using the Messages channel and system default tone.
     */
    fun postIncomingMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        chatMemberId: String = "",
        senderUid: String = "",
        senderPhone: String = "",
        conversationId: String = "",
        messageId: String = ""
    ) {
        if (isConversationActive(
                candidateMemberId = chatMemberId,
                candidateSenderUid = senderUid,
                candidateSenderPhone = senderPhone,
                candidateConversationId = conversationId,
                context = context
            )
        ) {
            Log.d(TAG, "Chat ($chatMemberId / $senderUid / $senderPhone) is active in foreground. Suppressing notification alert.")
            if (messageId.isNotBlank()) markMessageProcessed(context, messageId)
            return
        }

        if (messageId.isNotBlank() && isMessageProcessed(context, messageId)) {
            Log.d(TAG, "Message $messageId already processed. Suppressing duplicate alert.")
            return
        }

        if (messageId.isNotBlank()) {
            markMessageProcessed(context, messageId)
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_chat_member_id", chatMemberId.ifBlank { senderUid })
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

    /**
     * Cancels notifications for a specific chat ID when the user enters the conversation screen.
     */
    fun cancelNotificationsForChat(context: Context, chatMemberId: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (chatMemberId.isNotBlank()) {
                notificationManager?.cancel(chatMemberId.hashCode())
            }
            notificationManager?.cancel(SUMMARY_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification for chat $chatMemberId: ${e.localizedMessage}")
        }
    }
}
