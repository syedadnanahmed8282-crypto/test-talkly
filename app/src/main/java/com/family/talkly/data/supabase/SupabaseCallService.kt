package com.family.talkly.data.supabase

import android.util.Log
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

object SupabaseCallService {

    private const val TAG = "SupabaseCallService"

    suspend fun createActiveCall(activeCall: SupabaseActiveCall): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            SupabaseClientProvider.client.postgrest["active_calls"]
                .upsert(activeCall)
            Log.d(TAG, "Active call created/upserted successfully: id=${activeCall.id}, room=${activeCall.roomId}, status=${activeCall.status}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating active call in Supabase: ${e.localizedMessage}", e)
            Result.failure(e)
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
            Log.d(TAG, "Active call status updated: id=$callId -> $newStatus")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating active call status in Supabase: ${e.localizedMessage}", e)
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
            Log.d(TAG, "Active call deleted from Supabase: id=$callId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting active call in Supabase: ${e.localizedMessage}")
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
        onCallAction: (PostgresAction) -> Unit
    ): RealtimeChannel? = withContext(Dispatchers.IO) {
        if (currentUserId.isBlank() || currentUserId == "self") return@withContext null
        try {
            val channelName = "calls-user-$currentUserId"
            val channel = SupabaseClientProvider.client.realtime.channel(channelName)

            val callsFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "active_calls"
            }

            callsFlow.onEach { action ->
                onCallAction(action)
            }.launchIn(coroutineScope)

            channel.subscribe()
            Log.i(TAG, "Subscribed to Supabase Realtime Calls channel: $channelName")
            channel
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Supabase Realtime Calls channel: ${e.localizedMessage}", e)
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
