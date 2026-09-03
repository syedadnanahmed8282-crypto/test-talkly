package com.family.talkly.data.supabase

import com.family.talkly.data.local.entity.ChatMessageEntity
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.MessageRequest
import com.family.talkly.data.models.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object PostgresStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = ListSerializer(String.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<String>) {
        ListSerializer(String.serializer()).serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            return parseStringList(element)
        }
        return try {
            ListSerializer(String.serializer()).deserialize(decoder)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseStringList(element: JsonElement): List<String> {
        return when (element) {
            is JsonArray -> {
                element.mapNotNull {
                    when (it) {
                        is JsonPrimitive -> it.contentOrNull
                        else -> null
                    }
                }
            }
            is JsonPrimitive -> {
                val raw = element.contentOrNull ?: return emptyList()
                parsePgArrayString(raw)
            }
            is JsonNull -> emptyList()
            else -> emptyList()
        }
    }

    fun parsePgArrayString(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "[]" || trimmed.equals("null", ignoreCase = true)) {
            return emptyList()
        }
        val clean = trimmed.removePrefix("{").removeSuffix("}").removePrefix("[").removeSuffix("]")
        if (clean.isBlank()) return emptyList()
        return clean.split(",").map {
            it.trim().removeSurrounding("\"").removeSurrounding("'")
        }.filter { it.isNotBlank() }
    }
}

@Serializable
data class SupabaseTypingPayload(
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String = "",
    @SerialName("is_typing")
    val isTyping: Boolean = false,
    @SerialName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

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
    @Serializable(with = PostgresStringListSerializer::class)
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
        val name = if (isSelf) "You" else if (senderDisplayName.isNotBlank()) senderDisplayName else ""

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
        fun fromJsonObject(record: JsonObject): SupabaseMessage {
            fun getString(key: String): String? {
                val elem = record[key] ?: return null
                return if (elem is JsonPrimitive) elem.contentOrNull else null
            }
            fun getBoolean(key: String, default: Boolean = false): Boolean {
                val elem = record[key] ?: return default
                if (elem is JsonPrimitive) {
                    return elem.booleanOrNull ?: (elem.contentOrNull?.equals("true", ignoreCase = true) == true)
                }
                return default
            }
            fun getInt(key: String, default: Int = 0): Int {
                val elem = record[key] ?: return default
                if (elem is JsonPrimitive) {
                    return elem.intOrNull ?: elem.contentOrNull?.toIntOrNull() ?: default
                }
                return default
            }

            val id = getString("id") ?: UUID.randomUUID().toString()
            val convId = getString("conversation_id")
            val senderId = getString("sender_id") ?: ""
            val receiverId = getString("receiver_id") ?: ""
            val msgType = getString("message_type") ?: "TEXT"
            val textContent = getString("text_content") ?: ""
            val mediaUrl = getString("media_url")
            val callType = getString("call_type")
            val callDurationSec = getInt("call_duration_sec", 0)
            val isDelivered = getBoolean("is_delivered", false)
            val isRead = getBoolean("is_read", false)
            val readAt = getString("read_at")
            val reaction = getString("reaction")
            val isStarred = getBoolean("is_starred", false)
            val isPinned = getBoolean("is_pinned", false)
            val pinnedBy = getString("pinned_by")
            val replyToMsgId = getString("reply_to_message_id")
            val replyToSenderName = getString("reply_to_sender_name")
            val replyToText = getString("reply_to_text")
            val isEdited = getBoolean("is_edited", false)
            val isDeletedForEveryone = getBoolean("is_deleted_for_everyone", false)

            val deletedForUsers = record["deleted_for_users"]?.let {
                PostgresStringListSerializer.parseStringList(it)
            } ?: emptyList()

            val createdAt = getString("created_at")

            return SupabaseMessage(
                id = id,
                conversationId = convId,
                senderId = senderId,
                receiverId = receiverId,
                messageType = msgType,
                textContent = textContent,
                mediaUrl = mediaUrl,
                callType = callType,
                callDurationSec = callDurationSec,
                isDelivered = isDelivered,
                isRead = isRead,
                readAt = readAt,
                reaction = reaction,
                isStarred = isStarred,
                isPinned = isPinned,
                pinnedBy = pinnedBy,
                replyToMessageId = replyToMsgId,
                replyToSenderName = replyToSenderName,
                replyToText = replyToText,
                isEdited = isEdited,
                isDeletedForEveryone = isDeletedForEveryone,
                deletedForUsers = deletedForUsers,
                createdAt = createdAt
            )
        }

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

