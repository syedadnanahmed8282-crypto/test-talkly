package com.family.talkly.data.supabase

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

object SupabaseMessagingService {

    private const val TAG = "SupabaseMessaging"

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    private val conversationCache = ConcurrentHashMap<String, String>()
    private val resolvedUuidCache = ConcurrentHashMap<String, String>()

    private fun getSortedKey(idA: String, idB: String): String {
        return if (idA < idB) "${idA}_$idB" else "${idB}_$idA"
    }

    suspend fun resolveUserUuid(identifier: String): String? = withContext(Dispatchers.IO) {
        if (identifier.isBlank()) return@withContext null
        if (identifier == "self") {
            val currentAuthUid = SupabaseClientProvider.client.auth.currentUserOrNull()?.id
            if (!currentAuthUid.isNullOrBlank()) {
                resolvedUuidCache["self"] = currentAuthUid
                return@withContext currentAuthUid
            }
        }
        if (resolvedUuidCache.containsKey(identifier)) {
            return@withContext resolvedUuidCache[identifier]
        }

        // Check if already a valid UUID
        val isUuid = try {
            UUID.fromString(identifier)
            true
        } catch (e: Exception) {
            false
        }
        if (isUuid) {
            resolvedUuidCache[identifier] = identifier
            return@withContext identifier
        }

        // Query Supabase profiles table by phone or phone_suffix
        val cleanPhone = identifier.replace(" ", "").replace("-", "")
        val suffix = if (cleanPhone.length >= 6) cleanPhone.takeLast(6) else cleanPhone

        try {
            val profiles = SupabaseClientProvider.client.postgrest["profiles"]
                .select {
                    filter {
                        or {
                            eq("phone", cleanPhone)
                            eq("phone_suffix", suffix)
                            eq("phone", identifier)
                        }
                    }
                    limit(1)
                }
                .decodeList<SupabaseProfile>()

            val found = profiles.firstOrNull()
            if (found != null) {
                resolvedUuidCache[identifier] = found.id
                if (cleanPhone.isNotBlank()) resolvedUuidCache[cleanPhone] = found.id
                if (suffix.isNotBlank()) resolvedUuidCache[suffix] = found.id
                return@withContext found.id
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving user UUID for $identifier: ${e.localizedMessage}")
        }
        null
    }

    suspend fun getOrCreateConversationId(user1Id: String, user2Id: String): String? = withContext(Dispatchers.IO) {
        if (user1Id.isBlank() || user2Id.isBlank() || user1Id == user2Id) return@withContext null

        val sortedKey = getSortedKey(user1Id, user2Id)
        val cached = conversationCache[sortedKey]
        if (!cached.isNullOrBlank()) {
            return@withContext cached
        }

        val minId = if (user1Id < user2Id) user1Id else user2Id
        val maxId = if (user1Id < user2Id) user2Id else user1Id

        try {
            // Check existing conversation
            val existingList = SupabaseClientProvider.client.postgrest["conversations"]
                .select {
                    filter {
                        eq("participant1_id", minId)
                        eq("participant2_id", maxId)
                    }
                    limit(1)
                }
                .decodeList<SupabaseConversation>()

            val existing = existingList.firstOrNull()
            if (existing != null) {
                conversationCache[sortedKey] = existing.id
                return@withContext existing.id
            }

            // Create new conversation
            val newId = UUID.randomUUID().toString()
            val newConversation = SupabaseConversation(
                id = newId,
                participant1Id = minId,
                participant2Id = maxId
            )
            SupabaseClientProvider.client.postgrest["conversations"]
                .insert(newConversation)

            conversationCache[sortedKey] = newId
            return@withContext newId
        } catch (e: Exception) {
            Log.w(TAG, "Error in getOrCreateConversationId: ${e.localizedMessage}")
            // In case of conflict race, try querying once more
            try {
                val fallbackList = SupabaseClientProvider.client.postgrest["conversations"]
                    .select {
                        filter {
                            eq("participant1_id", minId)
                            eq("participant2_id", maxId)
                        }
                        limit(1)
                    }
                    .decodeList<SupabaseConversation>()
                val fallback = fallbackList.firstOrNull()
                if (fallback != null) {
                    conversationCache[sortedKey] = fallback.id
                    return@withContext fallback.id
                }
            } catch (e2: Exception) {
                Log.w(TAG, "Fallback getOrCreateConversationId query failed: ${e2.localizedMessage}")
            }
            null
        }
    }

    suspend fun sendMessage(message: SupabaseMessage): Boolean = withContext(Dispatchers.IO) {
            Log.e(TAG, "DEBUG_ACTUAL_INSERT_PAYLOAD: id='${message.id}', senderId='${message.senderId}', receiverId='${message.receiverId}', conversationId='${message.conversationId}', replyToId='${message.replyToMessageId}', type='${message.messageType}'")
            SupabaseClientProvider.client.postgrest["messages"]
                .insert(message)
            true
        }

    suspend fun fetchMessagesForConversation(
        conversationId: String?,
        user1Id: String,
        user2Id: String,
        limit: Long = 50,
        beforeTimestampIso: String? = null
    ): List<SupabaseMessage> = withContext(Dispatchers.IO) {
        try {
            if (!conversationId.isNullOrBlank()) {
                SupabaseClientProvider.client.postgrest["messages"]
                    .select {
                        filter {
                            eq("conversation_id", conversationId)
                            if (!beforeTimestampIso.isNullOrBlank()) {
                                lt("created_at", beforeTimestampIso)
                            }
                        }
                        order("created_at", Order.DESCENDING)
                        limit(limit)
                    }
                    .decodeList<SupabaseMessage>()
            } else {
                SupabaseClientProvider.client.postgrest["messages"]
                    .select {
                        filter {
                            or {
                                and {
                                    eq("sender_id", user1Id)
                                    eq("receiver_id", user2Id)
                                }
                                and {
                                    eq("sender_id", user2Id)
                                    eq("receiver_id", user1Id)
                                }
                            }
                            if (!beforeTimestampIso.isNullOrBlank()) {
                                lt("created_at", beforeTimestampIso)
                            }
                        }
                        order("created_at", Order.DESCENDING)
                        limit(limit)
                    }
                    .decodeList<SupabaseMessage>()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching messages: ${e.localizedMessage}")
            emptyList()
        }
    }

    suspend fun markMessageAsRead(messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val nowIso = SupabaseMessage.millisToIsoTimestamp(System.currentTimeMillis())
            SupabaseClientProvider.client.postgrest["messages"]
                .update({
                    set("is_read", true)
                    set("read_at", nowIso)
                    set("is_delivered", true)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error marking message as read: ${e.localizedMessage}")
            false
        }
    }

    suspend fun updateMessageReaction(messageId: String, reactionValue: String?): Boolean =
        toggleMessageReaction(messageId, reactionValue)

    suspend fun deleteMessageForYou(messageId: String, deletedForUsers: List<String>): Boolean =
        updateDeletedForUsers(messageId, deletedForUsers)

    suspend fun fetchRecentMessagesForUser(currentUserId: String, limit: Long = 200): List<SupabaseMessage> = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["messages"]
                .select {
                    filter {
                        or {
                            eq("sender_id", currentUserId)
                            eq("receiver_id", currentUserId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<SupabaseMessage>()
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching recent messages: ${e.localizedMessage}")
            emptyList()
        }
    }

    suspend fun markMessagesAsRead(
        currentUserId: String,
        conversationId: String?,
        senderId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val nowIso = SupabaseMessage.millisToIsoTimestamp(System.currentTimeMillis())
            if (!conversationId.isNullOrBlank()) {
                SupabaseClientProvider.client.postgrest["messages"]
                    .update({
                        set("is_read", true)
                        set("read_at", nowIso)
                        set("is_delivered", true)
                    }) {
                        filter {
                            eq("conversation_id", conversationId)
                            eq("receiver_id", currentUserId)
                            eq("is_read", false)
                        }
                    }
            } else {
                SupabaseClientProvider.client.postgrest["messages"]
                    .update({
                        set("is_read", true)
                        set("read_at", nowIso)
                        set("is_delivered", true)
                    }) {
                        filter {
                            eq("sender_id", senderId)
                            eq("receiver_id", currentUserId)
                            eq("is_read", false)
                        }
                    }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error marking messages as read: ${e.localizedMessage}")
            false
        }
    }

    suspend fun markMessageAsDelivered(messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["messages"]
                .update({
                    set("is_delivered", true)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error marking message as delivered: ${e.localizedMessage}")
            false
        }
    }

    suspend fun toggleMessageReaction(messageId: String, reactionValue: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["messages"]
                .update({
                    set("reaction", reactionValue)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error updating message reaction: ${e.localizedMessage}")
            false
        }
    }

    suspend fun editMessage(messageId: String, newText: String): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["messages"]
                .update({
                    set("text_content", newText)
                    set("is_edited", true)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error editing message in Supabase: ${e.localizedMessage}")
            false
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["messages"]
                .update({
                    set("is_deleted_for_everyone", true)
                    set("text_content", "This message was deleted")
                    set("media_url", null as String?)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting message for everyone in Supabase: ${e.localizedMessage}")
            false
        }
    }

    suspend fun toggleStarMessage(messageId: String, isStarred: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["messages"]
                .update({
                    set("is_starred", isStarred)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error starring message in Supabase: ${e.localizedMessage}")
            false
        }
    }

    suspend fun togglePinMessage(messageId: String, isPinned: Boolean, pinnedBy: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["messages"]
                .update({
                    set("is_pinned", isPinned)
                    set("pinned_by", pinnedBy)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error pinning message in Supabase: ${e.localizedMessage}")
            false
        }
    }

    suspend fun updateDeletedForUsers(messageId: String, deletedForUsers: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["messages"]
                .update({
                    set("deleted_for_users", deletedForUsers)
                }) {
                    filter {
                        eq("id", messageId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error updating deleted_for_users in Supabase: ${e.localizedMessage}")
            false
        }
    }

    suspend fun deleteChatHistory(
        currentUserId: String,
        targetUserId: String,
        conversationId: String?
    ): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        try {
            if (!conversationId.isNullOrBlank()) {
                SupabaseClientProvider.client.postgrest["messages"]
                    .delete {
                        filter {
                            eq("conversation_id", conversationId)
                        }
                    }
            } else {
                SupabaseClientProvider.client.postgrest["messages"]
                    .delete {
                        filter {
                            or {
                                and {
                                    eq("sender_id", currentUserId)
                                    eq("receiver_id", targetUserId)
                                }
                                and {
                                    eq("sender_id", targetUserId)
                                    eq("receiver_id", currentUserId)
                                }
                            }
                        }
                    }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting chat history in Supabase: ${e.localizedMessage}")
            false
        }
    }

    // Message Requests
    suspend fun fetchMessageRequests(currentUserId: String): List<SupabaseMessageRequest> = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["message_requests"]
                .select {
                    filter {
                        or {
                            eq("receiver_id", currentUserId)
                            eq("sender_id", currentUserId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<SupabaseMessageRequest>()
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching message requests: ${e.localizedMessage}")
            emptyList()
        }
    }

    suspend fun sendMessageRequest(request: SupabaseMessageRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["message_requests"]
                .upsert(request)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error sending message request to Supabase: ${e.localizedMessage}")
            false
        }
    }

    suspend fun updateMessageRequestStatus(requestId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val nowIso = SupabaseMessage.millisToIsoTimestamp(System.currentTimeMillis())
            SupabaseClientProvider.client.postgrest["message_requests"]
                .update({
                    set("status", status)
                    set("updated_at", nowIso)
                }) {
                    filter {
                        eq("id", requestId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error updating message request status: ${e.localizedMessage}")
            false
        }
    }

    suspend fun deleteMessageRequest(requestId: String): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        try {
            SupabaseClientProvider.client.postgrest["message_requests"]
                .delete {
                    filter {
                        eq("id", requestId)
                    }
                }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting message request: ${e.localizedMessage}")
            false
        }
    }

    // Realtime Subscriptions
    suspend fun createMessagingRealtimeChannel(
        currentUserId: String,
        coroutineScope: CoroutineScope,
        onMessageAction: (PostgresAction) -> Unit,
        onRequestAction: (PostgresAction) -> Unit,
        onTypingAction: ((SupabaseTypingPayload) -> Unit)? = null,
        onStatusChange: ((RealtimeChannel.Status) -> Unit)? = null
    ): RealtimeChannel? = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.realtime.connect()

            val channelName = "messages-user-$currentUserId"
            val channel = SupabaseClientProvider.client.realtime.channel(channelName)

            val messagesFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "messages"
            }

            val requestsFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "message_requests"
            }

            messagesFlow.onEach { action ->
                onMessageAction(action)
            }.launchIn(coroutineScope)

            requestsFlow.onEach { action ->
                onRequestAction(action)
            }.launchIn(coroutineScope)

            if (onTypingAction != null) {
                try {
                    val typingFlow = channel.broadcastFlow<SupabaseTypingPayload>(event = "typing")
                    typingFlow.onEach { payload ->
                        onTypingAction(payload)
                    }.launchIn(coroutineScope)
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting up typing broadcastFlow: ${e.localizedMessage}")
                }
            }

            if (onStatusChange != null) {
                channel.status.onEach { status ->
                    Log.d(TAG, "Realtime channel $channelName status changed: $status")
                    onStatusChange(status)
                }.launchIn(coroutineScope)
            }

            channel.subscribe(blockUntilSubscribed = true)
            Log.i(TAG, "Subscribed to Supabase Realtime channel: $channelName, status=${channel.status.value}")
            channel
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Supabase Realtime channel: ${e.localizedMessage}", e)
            null
        }
    }

    suspend fun sendTypingBroadcast(
        senderId: String,
        receiverId: String,
        isTyping: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (senderId.isBlank() || receiverId.isBlank()) return@withContext false
            val channelName = "messages-user-$receiverId"
            val channel = SupabaseClientProvider.client.realtime.channel(channelName)
            channel.subscribe(blockUntilSubscribed = false)
            channel.broadcast(
                event = "typing",
                message = SupabaseTypingPayload(
                    senderId = senderId,
                    receiverId = receiverId,
                    isTyping = isTyping,
                    timestamp = System.currentTimeMillis()
                )
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error sending typing broadcast: ${e.localizedMessage}")
            false
        }
    }

    suspend fun unsubscribeChannel(channel: RealtimeChannel?) = withContext(Dispatchers.IO) {
        try {
            channel?.unsubscribe()
        } catch (e: Exception) {
            Log.w(TAG, "Error unsubscribing realtime channel: ${e.localizedMessage}")
        }
    }
}
