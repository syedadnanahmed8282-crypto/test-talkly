package com.family.talkly.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.MessageType

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["chatKey"]), Index(value = ["timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val chatKey: String,
    val senderId: String = "self",
    val senderName: String = "You",
    val receiverId: String = "",
    val messageType: String = MessageType.TEXT.name,
    val textContent: String = "",
    val mediaUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val callType: String? = null,
    val callDurationSec: Int = 0,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false,
    val readAtTimestamp: Long? = null,
    val reaction: String? = null,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val isEdited: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val deletedForUsersString: String = "" // Comma-separated user IDs
) {
    fun toChatMessage(): ChatMessage {
        val type = try {
            MessageType.valueOf(messageType)
        } catch (e: Exception) {
            MessageType.TEXT
        }
        val deletedUsers = if (deletedForUsersString.isBlank()) {
            emptyList()
        } else {
            deletedForUsersString.split(",").filter { it.isNotBlank() }
        }

        return ChatMessage(
            id = id,
            senderId = senderId,
            senderName = senderName,
            receiverId = receiverId,
            messageType = type,
            textContent = textContent,
            mediaUrl = mediaUrl,
            timestamp = timestamp,
            callType = callType,
            callDurationSec = callDurationSec,
            isDelivered = isDelivered,
            isRead = isRead,
            readAtTimestamp = readAtTimestamp,
            reaction = reaction,
            isStarred = isStarred,
            isPinned = isPinned,
            replyToMessageId = replyToMessageId,
            replyToSenderName = replyToSenderName,
            replyToText = replyToText,
            isEdited = isEdited,
            isDeletedForEveryone = isDeletedForEveryone,
            deletedForUsers = deletedUsers
        )
    }

    companion object {
        fun fromChatMessage(chatKey: String, message: ChatMessage): ChatMessageEntity {
            return ChatMessageEntity(
                id = message.id,
                chatKey = chatKey,
                senderId = message.senderId,
                senderName = message.senderName,
                receiverId = message.receiverId,
                messageType = message.messageType.name,
                textContent = message.textContent,
                mediaUrl = message.mediaUrl,
                timestamp = message.timestamp,
                callType = message.callType,
                callDurationSec = message.callDurationSec,
                isDelivered = message.isDelivered,
                isRead = message.isRead,
                readAtTimestamp = message.readAtTimestamp,
                reaction = message.reaction,
                isStarred = message.isStarred,
                isPinned = message.isPinned,
                replyToMessageId = message.replyToMessageId,
                replyToSenderName = message.replyToSenderName,
                replyToText = message.replyToText,
                isEdited = message.isEdited,
                isDeletedForEveryone = message.isDeletedForEveryone,
                deletedForUsersString = message.deletedForUsers.joinToString(",")
            )
        }
    }
}
