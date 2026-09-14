package com.family.talkly.data.supabase

import android.util.Log
import com.family.talkly.util.PhoneUtils
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class ActiveCallConflictException(
    message: String = "Active call conflict: an active call is already in progress",
    val existingCall: SupabaseActiveCall? = null,
    cause: Throwable? = null
) : Exception(message, cause)

object SupabaseCallService {

    private const val TAG = "SupabaseCallService"

    suspend fun findActiveCallForParticipant(
        userId: String?,
        userPhone: String,
        userSuffix: String,
        excludeRoomId: String? = null
    ): SupabaseActiveCall? = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = PhoneUtils.cleanPhoneNumber(userPhone)
            val cleanSuffix = userSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(cleanPhone) }
            val validUid = if (!userId.isNullOrBlank() && userId != "self") userId else null

            val list = SupabaseClientProvider.client.postgrest["active_calls"]
                .select()
                .decodeList<SupabaseActiveCall>()

            for (call in list) {
                val callRoom = call.roomId.ifBlank { call.id }
                if (excludeRoomId != null && (call.id == excludeRoomId || callRoom == excludeRoomId)) {
                    continue
                }

                val statusUpper = call.status.uppercase()
                val isTerminal = statusUpper in listOf("ENDED", "REJECTED", "BUSY", "MISSED", "TIMEOUT", "CANCELLED")
                if (isTerminal) continue

                val ageMs = SupabaseMessage.parseIsoTimestampToMillis(call.createdAt).let {
                    if (it > 0) System.currentTimeMillis() - it else 0L
                }
                val maxValidAgeMs = if (statusUpper == "ACCEPTED") 4 * 60 * 60 * 1000L else 60_000L
                if (ageMs >= maxValidAgeMs) continue

                val matchesCaller = (validUid != null && call.callerId == validUid) ||
                        (cleanPhone.isNotBlank() && PhoneUtils.cleanPhoneNumber(call.callerPhone) == cleanPhone) ||
                        (cleanSuffix.isNotBlank() && (call.callerSuffix == cleanSuffix || PhoneUtils.extractPhoneSuffix(call.callerPhone) == cleanSuffix))

                val receiverUid = call.receiverId
                val matchesReceiver = (validUid != null && receiverUid != null && receiverUid == validUid) ||
                        (cleanPhone.isNotBlank() && PhoneUtils.cleanPhoneNumber(call.receiverPhone) == cleanPhone) ||
                        (cleanSuffix.isNotBlank() && (call.receiverSuffix == cleanSuffix || PhoneUtils.extractPhoneSuffix(call.receiverPhone) == cleanSuffix))

                if (matchesCaller || matchesReceiver) {
                    return@withContext call
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "[CALL_RACE] Error finding active call for participant: ${e.localizedMessage}")
            null
        }
    }

    suspend fun createActiveCall(activeCall: SupabaseActiveCall): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if an active call already exists in DB for this room
            val existing = getActiveCall(activeCall.id).getOrNull()
            if (existing != null) {
                val isTerminal = existing.status in listOf("ENDED", "REJECTED", "BUSY", "MISSED", "TIMEOUT", "CANCELLED")
                val ageMs = SupabaseMessage.parseIsoTimestampToMillis(existing.createdAt).let {
                    if (it > 0) System.currentTimeMillis() - it else 0L
                }
                if (!isTerminal && ageMs < 60_000L) {
                    Log.w(TAG, "[CALL_RACE] Active call conflict detected before insert: room ${activeCall.id} has existing active call with status=${existing.status}, caller=${existing.callerId}")
                    return@withContext Result.failure(ActiveCallConflictException("Active call already in progress", existing))
                } else {
                    Log.d(TAG, "[CALL_RACE] Cleaning stale call row in room ${activeCall.id} (status=${existing.status}, ageMs=$ageMs)")
                    try {
                        deleteActiveCall(activeCall.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "[CALL_RACE] Failed to clean stale call row: ${e.localizedMessage}")
                    }
                }
            }

            // Check if receiver is already in an active call in another room
            val receiverActiveCall = findActiveCallForParticipant(
                userId = activeCall.receiverId,
                userPhone = activeCall.receiverPhone,
                userSuffix = activeCall.receiverSuffix,
                excludeRoomId = activeCall.id
            )
            if (receiverActiveCall != null) {
                Log.w(TAG, "[CALL_BUSY] Active call rejected: receiver is already in another active call (room=${receiverActiveCall.id}, status=${receiverActiveCall.status})")
                return@withContext Result.failure(ActiveCallConflictException("User is busy / another call is in progress", receiverActiveCall))
            }

            // Check if caller is already in an active call in another room
            val callerActiveCall = findActiveCallForParticipant(
                userId = activeCall.callerId,
                userPhone = activeCall.callerPhone,
                userSuffix = activeCall.callerSuffix,
                excludeRoomId = activeCall.id
            )
            if (callerActiveCall != null) {
                Log.w(TAG, "[CALL_BUSY] Active call rejected: caller is already in another active call (room=${callerActiveCall.id}, status=${callerActiveCall.status})")
                return@withContext Result.failure(ActiveCallConflictException("You are already in an active call", callerActiveCall))
            }

            Log.d(TAG, "[CALL_RACE] Attempting DB insert for active_call: id=${activeCall.id}, caller=${activeCall.callerId}")
            SupabaseClientProvider.client.postgrest["active_calls"]
                .insert(activeCall)
            Log.d(TAG, "[CALL_RACE] DB win: active call created successfully: id=${activeCall.id}, room=${activeCall.roomId}")
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message.orEmpty().lowercase()
            val isConflict = msg.contains("duplicate") ||
                    msg.contains("conflict") ||
                    msg.contains("23505") ||
                    msg.contains("already exists") ||
                    msg.contains("unique") ||
                    (e is io.github.jan.supabase.exceptions.RestException && e.statusCode == 409)

            if (isConflict) {
                Log.w(TAG, "[CALL_RACE] DB loss: active call creation rejected due to conflict: ${e.localizedMessage}")
                val existing = getActiveCall(activeCall.id).getOrNull()
                Result.failure(ActiveCallConflictException("Active call conflict: DB race lost to existing call", existing, e))
            } else {
                Log.e(TAG, "[CALL_RACE] DB loss: error creating active call in Supabase: ${e.localizedMessage}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun updateActiveCallStatus(callId: String, newStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (callId.isBlank()) return@withContext Result.failure(IllegalArgumentException("callId cannot be blank"))
        try {
            val nowIso = SupabaseMessage.millisToIsoTimestamp(System.currentTimeMillis())
            SupabaseClientProvider.client.postgrest["active_calls"]
                .update({
                    set("status", newStatus)
                    set("updated_at", nowIso)
                }) {
                    filter {
                        eq("id", callId)
                    }
                }

            if (newStatus == "ACCEPTED") {
                val current = getActiveCall(callId).getOrNull()
                if (current == null || current.status != "ACCEPTED") {
                    Log.w(TAG, "[CALL_RACE] Cannot accept call $callId: call no longer exists or status is ${current?.status}")
                    return@withContext Result.failure(IllegalStateException("Call no longer exists or has ended"))
                }
            }
            Log.d(TAG, "[CALL_RACE] Active call status updated: id=$callId -> $newStatus")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[CALL_RACE] Error updating active call status in Supabase: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun getActiveCall(callId: String): Result<SupabaseActiveCall?> = withContext(Dispatchers.IO) {
        if (callId.isBlank()) return@withContext Result.success(null)
        try {
            val list = SupabaseClientProvider.client.postgrest["active_calls"]
                .select {
                    filter {
                        eq("id", callId)
                    }
                    limit(1)
                }
                .decodeList<SupabaseActiveCall>()
            Result.success(list.firstOrNull())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active call: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteActiveCall(callId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (callId.isBlank()) return@withContext Result.success(Unit)
        try {
            SupabaseClientProvider.client.postgrest["active_calls"]
                .delete {
                    filter {
                        eq("id", callId)
                    }
                }
            Log.d(TAG, "[CALL_RACE] Active call deleted from Supabase: id=$callId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "[CALL_RACE] Error deleting active call in Supabase: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    suspend fun deleteActiveCallsForUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank() || userId == "self") return@withContext Result.success(Unit)
        try {
            SupabaseClientProvider.client.postgrest["active_calls"]
                .delete {
                    filter {
                        or {
                            eq("caller_id", userId)
                            eq("receiver_id", userId)
                        }
                    }
                }
            Log.d(TAG, "Cleaned up all active calls for user $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning active calls for user: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    // Call Logs (History)
    suspend fun insertCallLog(callLog: SupabaseCallLog): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["call_logs"]
                .insert(callLog)
            Log.d(TAG, "Call log inserted to Supabase: id=${callLog.id}, user=${callLog.userId}, direction=${callLog.direction}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting call log in Supabase: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchCallLogs(userId: String, limit: Long = 50): Result<List<SupabaseCallLog>> = withContext(Dispatchers.IO) {
        if (userId.isBlank() || userId == "self") return@withContext Result.success(emptyList())
        try {
            val logs = SupabaseClientProvider.client.postgrest["call_logs"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<SupabaseCallLog>()
            Result.success(logs)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching call logs from Supabase: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCallLog(callLogId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["call_logs"]
                .delete {
                    filter {
                        eq("id", callLogId)
                        eq("user_id", userId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting call log from Supabase: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun clearAllCallLogs(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank() || userId == "self") return@withContext Result.success(Unit)
        try {
            SupabaseClientProvider.client.postgrest["call_logs"]
                .delete {
                    filter {
                        eq("user_id", userId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing call logs from Supabase: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    // Realtime Calls Channel
    suspend fun createCallsRealtimeChannel(
        currentUserId: String,
        coroutineScope: CoroutineScope,
        onCallAction: (PostgresAction) -> Unit,
        onStatusChange: ((RealtimeChannel.Status) -> Unit)? = null
    ): RealtimeChannel? = withContext(Dispatchers.IO) {
        if (currentUserId.isBlank() || currentUserId == "self") return@withContext null
        try {
            val preConnectSocketStatus = SupabaseClientProvider.client.realtime.status.value
            Log.d(TAG, "DIAGNOSTIC createCallsRealtimeChannel: Connecting socket (current socket status=$preConnectSocketStatus) for user=$currentUserId")
            SupabaseClientProvider.client.realtime.connect()
            val postConnectSocketStatus = SupabaseClientProvider.client.realtime.status.value
            Log.d(TAG, "DIAGNOSTIC createCallsRealtimeChannel: Socket connected (status=$postConnectSocketStatus)")

            val channelName = "calls-user-$currentUserId"
            
            // Cleanly remove any existing joined channel with this name
            try {
                val matchingChannels = SupabaseClientProvider.client.realtime.subscriptions.values.filter {
                    it.topic == "realtime:$channelName" || it.topic == channelName
                }
                for (existing in matchingChannels) {
                    Log.d(TAG, "DIAGNOSTIC Cleaning existing calls channel ${existing.topic} (status=${existing.status.value})")
                    try { existing.unsubscribe() } catch (_: Exception) {}
                    try { SupabaseClientProvider.client.realtime.removeChannel(existing) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning previous calls channel instance: ${e.localizedMessage}")
            }

            val channel = SupabaseClientProvider.client.realtime.channel(channelName)

            val callsFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "active_calls"
            }

            callsFlow.onEach { action ->
                Log.d(TAG, "DIAGNOSTIC callsFlow received action on $channelName: $action")
                onCallAction(action)
            }.launchIn(coroutineScope)

            if (onStatusChange != null) {
                channel.status.onEach { status ->
                    Log.d(TAG, "DIAGNOSTIC Realtime Calls channel $channelName status changed: $status")
                    onStatusChange(status)
                }.launchIn(coroutineScope)
            }

            Log.d(TAG, "DIAGNOSTIC Subscribing to calls channel $channelName (blockUntilSubscribed=true)")
            channel.subscribe(blockUntilSubscribed = true)
            Log.i(TAG, "DIAGNOSTIC Successfully subscribed to calls channel: $channelName, final status=${channel.status.value}")
            channel
        } catch (e: Exception) {
            Log.e(TAG, "DIAGNOSTIC Failed to create Supabase Realtime Calls channel: ${e.localizedMessage}", e)
            null
        }
    }

    suspend fun unsubscribeChannel(channel: RealtimeChannel?) = withContext(Dispatchers.IO) {
        try {
            channel?.unsubscribe()
            Log.d(TAG, "Unsubscribed from Supabase Realtime Calls channel")
        } catch (e: Exception) {
            Log.w(TAG, "Error unsubscribing calls channel: ${e.localizedMessage}")
        }
    }
}
