package com.family.talkly.data.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    VOICE_NOTE,
    CALL_LOG
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "self",
    val senderName: String = "You",
    val receiverId: String = "",
    val messageType: MessageType = MessageType.TEXT,
    val textContent: String = "",
    val mediaUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val callType: String? = null, // "AUDIO" or "VIDEO"
    val callDurationSec: Int = 0,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false,
    val readAtTimestamp: Long? = null,
    val reaction: String? = null,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val pinnedBy: String? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val isEdited: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val deletedForUsers: List<String> = emptyList(),
    val isPending: Boolean = false,
    val isUploading: Boolean = false,
    val isFailed: Boolean = false,
    val uploadProgress: Int = 0
) {
    companion object {
        const val EXPIRATION_48_HOURS_MS = 48 * 60 * 60 * 1000L // 172,800,000 ms
    }

    fun isMediaExpired(simulatedTimeOffsetMs: Long = 0L): Boolean {
        if (messageType != MessageType.IMAGE && messageType != MessageType.VIDEO && messageType != MessageType.VOICE_NOTE) {
            return false
        }
        val effectiveCurrentTime = System.currentTimeMillis() + simulatedTimeOffsetMs
        return (effectiveCurrentTime - timestamp) >= EXPIRATION_48_HOURS_MS
    }

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val formattedReadTime: String
        get() {
            val t = readAtTimestamp ?: timestamp
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(t))
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

data class ReactionEntry(
    val userId: String = "",
    val userName: String = "User",
    val emoji: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val avatarUrl: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

object ReactionUtils {
    fun parseReactions(
        reactionStr: String?,
        msgSenderId: String = "self",
        msgSenderName: String = "User",
        msgTimestamp: Long = System.currentTimeMillis()
    ): List<ReactionEntry> {
        if (reactionStr.isNullOrBlank()) return emptyList()
        val trimmed = reactionStr.trim()
        if (trimmed.startsWith("[")) {
            try {
                val array = org.json.JSONArray(trimmed)
                val list = mutableListOf<ReactionEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ReactionEntry(
                            userId = obj.optString("userId", "self"),
                            userName = obj.optString("userName", "User"),
                            emoji = obj.optString("emoji", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            avatarUrl = if (obj.has("avatarUrl") && !obj.isNull("avatarUrl")) obj.optString("avatarUrl") else null
                        )
                    )
                }
                return list
            } catch (e: Exception) {
                // Fallback to single emoji
            }
        }
        return listOf(
            ReactionEntry(
                userId = msgSenderId,
                userName = msgSenderName,
                emoji = trimmed,
                timestamp = msgTimestamp
            )
        )
    }

    fun serializeReactions(entries: List<ReactionEntry>): String? {
        if (entries.isEmpty()) return null
        val array = org.json.JSONArray()
        entries.forEach { entry ->
            val obj = org.json.JSONObject().apply {
                put("userId", entry.userId)
                put("userName", entry.userName)
                put("emoji", entry.emoji)
                put("timestamp", entry.timestamp)
                if (!entry.avatarUrl.isNullOrBlank()) {
                    put("avatarUrl", entry.avatarUrl)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }
}

