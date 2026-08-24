package com.family.talkly.data.supabase

import com.family.talkly.data.local.entity.ChatMessageEntity
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.MessageRequest
import com.family.talkly.data.models.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseConversation(
    val id: String,
    @SerialName("participant1_id")
    val participant1Id: String,
    @SerialName("participant2_id")
    val participant2Id: String,
    @SerialName("last_message_id")
    val lastMessageId: String? = null,
    @SerialName("last_message_text")
    val lastMessageText: String? = "",
    @SerialName("last_message_time")
    val lastMessageTime: String? = null,
    @SerialName("last_message_sender_id")
    val lastMessageSenderId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class SupabaseMessage(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String? = null,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String,
    @SerialName("message_type")
    val messageType: String = "TEXT",
    @SerialName("text_content")
    val textContent: String = "",
    @SerialName("media_url")
    val mediaUrl: String? = null,
    @SerialName("call_type")
    val callType: String? = null,
    @SerialName("call_duration_sec")
    val callDurationSec: Int = 0,
    @SerialName("is_delivered")
    val isDelivered: Boolean = false,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("read_at")
    val readAt: String? = null,
    val reaction: String? = null,
    @SerialName("is_starred")
    val isStarred: Boolean = false,
    @SerialName("is_pinned")
    val isPinned: Boolean = false,
    @SerialName("pinned_by")
    val pinnedBy: String? = null,
    @SerialName("reply_to_message_id")
    val replyToMessageId: String? = null,
    @SerialName("reply_to_sender_name")
    val replyToSenderName: String? = null,
    @SerialName("reply_to_text")
    val replyToText: String? = null,
    @SerialName("is_edited")
    val isEdited: Boolean = false,
    @SerialName("is_deleted_for_everyone")
    val isDeletedForEveryone: Boolean = false,
    @SerialName("deleted_for_users")
    val deletedForUsers: List<String> = emptyList(),
    @SerialName("created_at")
    val createdAt: String? = null
) {
    fun toChatMessage(currentUserId: String = "", senderDisplayName: String = ""): ChatMessage {
        val parsedType = try {
            MessageType.valueOf(messageType.uppercase())
        } catch (e: Exception) {
            MessageType.TEXT
        }

        val timestampMillis = parseIsoTimestampToMillis(createdAt)
        val readAtMillis = readAt?.let { parseIsoTimestampToMillis(it) }

        val isSelf = currentUserId.isNotBlank() && senderId == currentUserId
        val name = if (isSelf) "You" else if (senderDisplayName.isNotBlank()) senderDisplayName else "Member"

        return ChatMessage(
            id = id,
            senderId = senderId,
            senderName = name,
            receiverId = receiverId,
            messageType = parsedType,
            textContent = textContent,
            mediaUrl = mediaUrl,
            timestamp = timestampMillis,
            callType = callType,
            callDurationSec = callDurationSec,
            isDelivered = isDelivered,
            isRead = isRead,
            readAtTimestamp = readAtMillis,
            reaction = reaction,
            isStarred = isStarred,
            isPinned = isPinned,
            pinnedBy = pinnedBy,
            replyToMessageId = replyToMessageId,
            replyToSenderName = replyToSenderName,
            replyToText = replyToText,
            isEdited = isEdited,
            isDeletedForEveryone = isDeletedForEveryone,
            deletedForUsers = deletedForUsers,
            isPending = false,
            isUploading = false,
            isFailed = false,
            uploadProgress = 0
        )
    }

    companion object {
        fun parseIsoTimestampToMillis(isoString: String?): Long {
            if (isoString.isNullOrBlank()) return System.currentTimeMillis()
            return try {
                // Try standard ISO-8601 formats
                val clean = isoString.replace("Z", "+0000").replace("+00:00", "+0000")
                val patterns = listOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                    "yyyy-MM-dd'T'HH:mm:ssZ",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss"
                )
                var parsedDate: Date? = null
                for (p in patterns) {
                    try {
                        val sdf = SimpleDateFormat(p, Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        parsedDate = sdf.parse(clean)
                        if (parsedDate != null) break
                    } catch (e: Exception) {
                        // continue to next pattern
                    }
                }
                parsedDate?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }

        fun millisToIsoTimestamp(millis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return sdf.format(Date(millis))
        }
    }
}

@Serializable
data class SupabaseMessageRequest(
    val id: String,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String,
    @SerialName("sender_phone")
    val senderPhone: String,
    @SerialName("sender_phone_suffix")
    val senderPhoneSuffix: String = "",
    @SerialName("sender_name")
    val senderName: String,
    @SerialName("sender_avatar")
    val senderAvatar: String = "",
    @SerialName("receiver_phone")
    val receiverPhone: String,
    @SerialName("receiver_phone_suffix")
    val receiverPhoneSuffix: String = "",
    @SerialName("receiver_name")
    val receiverName: String,
    val status: String = "PENDING",
    @SerialName("initial_message")
    val initialMessage: String = "Hello, I would like to connect on Talkly!",
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
) {
    fun toMessageRequest(): MessageRequest {
        val timestampMillis = SupabaseMessage.parseIsoTimestampToMillis(createdAt)
        return MessageRequest(
            id = id,
            senderId = senderId,
            senderPhone = senderPhone,
            senderPhoneSuffix = senderPhoneSuffix,
            senderName = senderName,
            senderAvatar = senderAvatar,
            receiverId = receiverId,
            receiverPhone = receiverPhone,
            receiverPhoneSuffix = receiverPhoneSuffix,
            receiverName = receiverName,
            status = status,
            initialMessage = initialMessage,
            timestamp = timestampMillis
        )
    }
}

@Serializable
data class SupabaseContact(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("contact_user_id")
    val contactUserId: String? = null,
    @SerialName("contact_name")
    val contactName: String,
    @SerialName("contact_phone")
    val contactPhone: String,
    @SerialName("contact_phone_suffix")
    val contactPhoneSuffix: String = "",
    val relation: String = "Contact",
    @SerialName("is_pinned")
    val isPinned: Boolean = false,
    @SerialName("is_mutual")
    val isMutual: Boolean = false,
    val status: String = "ACCEPTED",
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

fun ChatMessage.toSupabaseMessage(
    conversationId: String? = null,
    resolvedSenderId: String? = null,
    resolvedReceiverId: String? = null
): SupabaseMessage {
    val validSupabaseId = if (id.isBlank()) {
        java.util.UUID.randomUUID().toString()
    } else {
        try {
            java.util.UUID.fromString(id)
            id
        } catch (e: Exception) {
            java.util.UUID.nameUUIDFromBytes(id.toByteArray()).toString()
        }
    }

    val validReplyToId = replyToMessageId?.takeIf { it.isNotBlank() }?.let { raw ->
        try {
            java.util.UUID.fromString(raw)
            raw
        } catch (e: Exception) {
            java.util.UUID.nameUUIDFromBytes(raw.toByteArray()).toString()
        }
    }

    return SupabaseMessage(
        id = validSupabaseId,
        conversationId = conversationId,
        senderId = resolvedSenderId ?: senderId,
        receiverId = resolvedReceiverId ?: receiverId,
        messageType = messageType.name,
        textContent = textContent,
        mediaUrl = mediaUrl,
        callType = callType,
        callDurationSec = callDurationSec,
        isDelivered = isDelivered,
        isRead = isRead,
        readAt = readAtTimestamp?.let { SupabaseMessage.millisToIsoTimestamp(it) },
        reaction = reaction,
        isStarred = isStarred,
        isPinned = isPinned,
        pinnedBy = pinnedBy,
        replyToMessageId = validReplyToId,
        replyToSenderName = replyToSenderName,
        replyToText = replyToText,
        isEdited = isEdited,
        isDeletedForEveryone = isDeletedForEveryone,
        deletedForUsers = deletedForUsers,
        createdAt = SupabaseMessage.millisToIsoTimestamp(timestamp)
    )
}

