package com.family.talkly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.family.talkly.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE chatKey = :chatKey ORDER BY timestamp ASC")
    fun getMessagesForChat(chatKey: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatKey = :chatKey ORDER BY timestamp ASC")
    suspend fun getMessagesForChatSync(chatKey: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE chatKey = :chatKey ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesForChatPaginated(chatKey: String, limit: Int = 50, offset: Int = 0): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesSync(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): ChatMessageEntity?

    @Query("UPDATE chat_messages SET isPending = :isPending, isUploading = :isUploading, isFailed = :isFailed, uploadProgress = :uploadProgress, mediaUrl = COALESCE(:mediaUrl, mediaUrl) WHERE id = :messageId")
    suspend fun updateUploadState(
        messageId: String,
        isPending: Boolean,
        isUploading: Boolean,
        isFailed: Boolean,
        uploadProgress: Int,
        mediaUrl: String? = null
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET isRead = :isRead, readAtTimestamp = :readAt WHERE id = :messageId")
    suspend fun updateMessageReadStatus(messageId: String, isRead: Boolean, readAt: Long?)

    @Query("UPDATE chat_messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun updateMessageReaction(messageId: String, reaction: String?)

    @Query("UPDATE chat_messages SET isDeletedForEveryone = :isDeletedForEveryone, textContent = :textContent, mediaUrl = NULL WHERE id = :messageId")
    suspend fun updateMessageDeletion(messageId: String, isDeletedForEveryone: Boolean, textContent: String)

    @Query("UPDATE chat_messages SET textContent = :newContent, isEdited = 1 WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, newContent: String)

    @Query("UPDATE chat_messages SET isStarred = :isStarred WHERE id = :messageId")
    suspend fun updateMessageStarred(messageId: String, isStarred: Boolean)

    @Query("UPDATE chat_messages SET isPinned = :isPinned, pinnedBy = :pinnedBy WHERE id = :messageId")
    suspend fun updateMessagePinned(messageId: String, isPinned: Boolean, pinnedBy: String?)

    @Query("UPDATE chat_messages SET isPending = :isPending WHERE id = :messageId")
    suspend fun updatePendingStatus(messageId: String, isPending: Boolean)

    @Query("DELETE FROM chat_messages WHERE chatKey = :chatKey")
    suspend fun deleteMessagesForChat(chatKey: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
