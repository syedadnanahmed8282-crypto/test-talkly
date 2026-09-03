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

    private const val PREFS_ACTIVE_NOTIFICATIONS = "talkly_active_notifications_tracker"

    /**
     * Resolves the real display name of the sender.
     * Uses candidate name if present and valid. Never hardcodes or returns "Member".
     * Falls back to app/repository contact details if available.
     */
    fun resolveSenderDisplayName(
        context: Context,
        candidateSenderName: String?,
        chatMemberId: String = "",
        senderUid: String = "",
        senderPhone: String = ""
    ): String {
        val clean = candidateSenderName?.trim().orEmpty()
        if (clean.isNotBlank() &&
            !clean.equals("Member", ignoreCase = true) &&
            !clean.equals("Talkly User", ignoreCase = true) &&
            !clean.equals("Talkly Message", ignoreCase = true)
        ) {
            return clean
        }

        // Try resolving from local repository contacts
        try {
            val repo = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context)
            val members = repo.familyMembers.value
            val candidateSuffix = PhoneUtils.extractPhoneSuffix(if (senderPhone.isNotBlank()) senderPhone else chatMemberId)

            val matched = members.firstOrNull { m ->
                (chatMemberId.isNotBlank() && (m.id == chatMemberId || m.firebaseUid == chatMemberId)) ||
                (senderUid.isNotBlank() && (m.id == senderUid || m.firebaseUid == senderUid)) ||
                (senderPhone.isNotBlank() && m.phone.isNotBlank() && m.phone == senderPhone) ||
                (candidateSuffix.isNotBlank() && m.phone.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == candidateSuffix)
            }
            if (matched != null && matched.name.isNotBlank() && !matched.name.equals("Member", ignoreCase = true) && !matched.name.equals("Talkly User", ignoreCase = true)) {
                return matched.name
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving contact name from repository: ${e.localizedMessage}")
        }

        if (clean.isNotBlank() && !clean.equals("Member", ignoreCase = true)) {
            return clean
        }

        return "Talkly Message"
    }

    /**
     * Generates a stable, deterministic notification ID.
     * - Uses messageId if present so the same message always yields the same ID.
     * - Uses chatMemberId + text + timestamp fallback if messageId is absent.
     * - Always returns a positive integer != 0 and != SUMMARY_NOTIFICATION_ID (99999).
     */
    fun generateNotificationId(
        messageId: String = "",
        chatMemberId: String = "",
        messageText: String = "",
        timestamp: Long = 0L
    ): Int {
        val key = if (messageId.isNotBlank()) {
            "msg_$messageId"
        } else {
            val safeChat = chatMemberId.ifBlank { "unknown" }
            val safeTime = if (timestamp > 0L) timestamp else System.currentTimeMillis()
            "fallback_${safeChat}_${messageText.take(64)}_$safeTime"
        }

        var id = key.hashCode() and 0x7FFFFFFF
        if (id == 0) id = 1
        if (id == SUMMARY_NOTIFICATION_ID) id = 100001
        return id
    }

    @Synchronized
    private fun trackNotificationForChat(
        context: Context,
        notificationId: Int,
        chatMemberId: String,
        senderUid: String = "",
        senderPhone: String = ""
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_ACTIVE_NOTIFICATIONS, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            val keys = mutableSetOf<String>()
            if (chatMemberId.isNotBlank()) {
                keys.add(chatMemberId)
                val s = PhoneUtils.extractPhoneSuffix(chatMemberId)
                if (s.isNotBlank()) keys.add(s)
            }
            if (senderUid.isNotBlank()) {
                keys.add(senderUid)
                val s = PhoneUtils.extractPhoneSuffix(senderUid)
                if (s.isNotBlank()) keys.add(s)
            }
            if (senderPhone.isNotBlank()) {
                keys.add(senderPhone)
                val s = PhoneUtils.extractPhoneSuffix(senderPhone)
                if (s.isNotBlank()) keys.add(s)
            }

            for (k in keys) {
                val existing = prefs.getStringSet("chat_$k", emptySet())?.toMutableSet() ?: mutableSetOf()
                existing.add(notificationId.toString())
                editor.putStringSet("chat_$k", existing)
            }
            editor.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error tracking active notification ID $notificationId: ${e.localizedMessage}")
        }
    }

    /**
     * Posts a notification for incoming chat messages using the Messages channel and system default tone.
     * Each unique message receives a distinct, stable notification ID so different messages remain visible simultaneously.
     */
    fun postIncomingMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        chatMemberId: String = "",
        senderUid: String = "",
        senderPhone: String = "",
        conversationId: String = "",
        messageId: String = "",
        timestamp: Long = 0L
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
            val finalSenderName = resolveSenderDisplayName(
                context = context,
                candidateSenderName = senderName,
                chatMemberId = chatMemberId,
                senderUid = senderUid,
                senderPhone = senderPhone
            )

            val notificationId = generateNotificationId(
                messageId = messageId,
                chatMemberId = chatMemberId,
                messageText = messageText,
                timestamp = timestamp
            )

            val targetChatKey = chatMemberId.ifBlank { senderUid }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_chat_member_id", targetChatKey)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val defaultNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notificationExtras = android.os.Bundle().apply {
                putString("chat_member_id", chatMemberId)
                if (senderUid.isNotBlank()) putString("sender_uid", senderUid)
                if (senderPhone.isNotBlank()) putString("sender_phone", senderPhone)
                if (messageId.isNotBlank()) putString("message_id", messageId)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(finalSenderName)
                .setContentText(messageText)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(defaultNotificationUri)
                .setContentIntent(pendingIntent)
                .addExtras(notificationExtras)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(notificationId, builder.build())

            // Track notification ID so it can be cleared when entering this conversation
            trackNotificationForChat(
                context = context,
                notificationId = notificationId,
                chatMemberId = chatMemberId,
                senderUid = senderUid,
                senderPhone = senderPhone
            )

            // Play system default notification tone
            playSystemNotificationSound(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting message notification: ${e.localizedMessage}")
        }
    }

    /**
     * Cancels notifications for a specific chat ID when the user enters the conversation screen.
     * Clears all individual notifications posted for that chat without affecting other chats.
     */
    @Synchronized
    fun cancelNotificationsForChat(context: Context, chatMemberId: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Always cancel summary notification
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)

            if (chatMemberId.isBlank()) return

            // 2. Also cancel legacy ID just in case
            notificationManager.cancel(chatMemberId.hashCode())

            // 3. Retrieve tracked notification IDs from SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_ACTIVE_NOTIFICATIONS, Context.MODE_PRIVATE)
            val keys = mutableSetOf<String>()
            keys.add(chatMemberId)
            val suffix = PhoneUtils.extractPhoneSuffix(chatMemberId)
            if (suffix.isNotBlank()) keys.add(suffix)

            // Include repository contact aliases if known
            try {
                val members = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context).familyMembers.value
                val matched = members.firstOrNull { m ->
                    m.id == chatMemberId ||
                    (!m.firebaseUid.isNullOrBlank() && m.firebaseUid == chatMemberId) ||
                    (m.phone.isNotBlank() && (m.phone == chatMemberId || PhoneUtils.extractPhoneSuffix(m.phone) == suffix))
                }
                if (matched != null) {
                    keys.add(matched.id)
                    if (!matched.firebaseUid.isNullOrBlank()) keys.add(matched.firebaseUid)
                    if (matched.phone.isNotBlank()) {
                        keys.add(matched.phone)
                        val s = PhoneUtils.extractPhoneSuffix(matched.phone)
                        if (s.isNotBlank()) keys.add(s)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error looking up contact aliases in cancelNotificationsForChat: ${e.localizedMessage}")
            }

            val notifIdsToCancel = mutableSetOf<Int>()
            val editor = prefs.edit()
            for (k in keys) {
                val set = prefs.getStringSet("chat_$k", emptySet())
                if (!set.isNullOrEmpty()) {
                    set.forEach { idStr ->
                        idStr.toIntOrNull()?.let { notifIdsToCancel.add(it) }
                    }
                    editor.remove("chat_$k")
                }
            }
            editor.apply()

            for (id in notifIdsToCancel) {
                notificationManager.cancel(id)
            }

            // 4. On Android M+ (API 23+), verify activeNotifications matching chat extras
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val activeNotifs = notificationManager.activeNotifications ?: emptyArray()
                    for (sbn in activeNotifs) {
                        if (sbn.id == SUMMARY_NOTIFICATION_ID) continue
                        val extras = sbn.notification.extras ?: continue
                        val notifChatId = extras.getString("chat_member_id") ?: ""
                        val notifSenderUid = extras.getString("sender_uid") ?: ""
                        val notifSenderPhone = extras.getString("sender_phone") ?: ""
                        val notifSuffix = PhoneUtils.extractPhoneSuffix(if (notifSenderPhone.isNotBlank()) notifSenderPhone else notifChatId)

                        val isMatch = (notifChatId.isNotBlank() && keys.contains(notifChatId)) ||
                                (notifSenderUid.isNotBlank() && keys.contains(notifSenderUid)) ||
                                (notifSenderPhone.isNotBlank() && keys.contains(notifSenderPhone)) ||
                                (suffix.isNotBlank() && notifSuffix.isNotBlank() && suffix == notifSuffix)

                        if (isMatch) {
                            notificationManager.cancel(sbn.tag, sbn.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error scanning active notifications: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification for chat $chatMemberId: ${e.localizedMessage}")
        }
    }
}
