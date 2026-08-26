package com.family.talkly.data.supabase

import com.family.talkly.data.models.CallDirection
import com.family.talkly.data.models.CallLog
import com.family.talkly.data.models.CallType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseActiveCall(
    val id: String,
    @SerialName("room_id")
    val roomId: String,
    @SerialName("caller_id")
    val callerId: String,
    @SerialName("caller_name")
    val callerName: String,
    @SerialName("caller_phone")
    val callerPhone: String,
    @SerialName("caller_suffix")
    val callerSuffix: String,
    @SerialName("caller_avatar_url")
    val callerAvatarUrl: String = "",
    @SerialName("receiver_id")
    val receiverId: String? = null,
    @SerialName("receiver_phone")
    val receiverPhone: String,
    @SerialName("receiver_suffix")
    val receiverSuffix: String,
    @SerialName("call_type")
    val callType: String,
    val status: String = "CALLING",
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class SupabaseCallLog(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("peer_id")
    val peerId: String? = null,
    @SerialName("peer_name")
    val peerName: String,
    val direction: String, // INCOMING, OUTGOING, MISSED
    @SerialName("call_type")
    val callType: String, // AUDIO, VIDEO
    @SerialName("duration_seconds")
    val durationSeconds: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null
) {
    fun toCallLog(): CallLog {
        val parsedDirection = try {
            CallDirection.valueOf(direction.uppercase())
        } catch (e: Exception) {
            CallDirection.INCOMING
        }
        val parsedType = try {
            CallType.valueOf(callType.uppercase())
        } catch (e: Exception) {
            CallType.VIDEO
        }
        val timestampMillis = SupabaseMessage.parseIsoTimestampToMillis(createdAt)

        return CallLog(
            id = id,
            memberId = peerId ?: "",
            memberName = peerName,
            direction = parsedDirection,
            callType = parsedType,
            timestamp = timestampMillis,
            durationSeconds = durationSeconds
        )
    }
}
