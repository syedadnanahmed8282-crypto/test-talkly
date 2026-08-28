package com.family.talkly.data.supabase

import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.StatusItem
import com.family.talkly.data.models.StatusLiker
import com.family.talkly.data.models.StatusViewer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabasePresencePayload(
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String = "",
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("online_at")
    val onlineAt: Long = System.currentTimeMillis()
)

@Serializable
data class SupabaseStatus(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String,
    @SerialName("user_avatar_url")
    val userAvatarUrl: String? = null,
    @SerialName("text_content")
    val textContent: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    @SerialName("background_color_hex")
    val backgroundColorHex: String = "#321C3B",
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null
) {
    fun toStatusItem(viewers: List<StatusViewer> = emptyList(), likes: List<StatusLiker> = emptyList()): StatusItem {
        val timestampMillis = SupabaseMessage.parseIsoTimestampToMillis(createdAt)
        val isMediaVideo = photoUrl?.let { it.endsWith(".mp4", ignoreCase = true) || it.contains("video/upload") } ?: false
        return StatusItem(
            id = id,
            userId = userId,
            userName = userName,
            userAvatarUrl = userAvatarUrl,
            textContent = textContent,
            photoUrl = photoUrl,
            isVideo = isMediaVideo,
            backgroundColorHex = backgroundColorHex,
            timestamp = timestampMillis,
            isSeen = false,
            viewers = viewers,
            likes = likes
        )
    }
}

@Serializable
data class SupabaseStatusViewer(
    val id: String? = null,
    @SerialName("status_id")
    val statusId: String,
    @SerialName("viewer_id")
    val viewerId: String,
    @SerialName("viewer_name")
    val viewerName: String,
    @SerialName("viewer_avatar_url")
    val viewerAvatarUrl: String? = null,
    @SerialName("viewed_at")
    val viewedAt: String? = null
) {
    fun toStatusViewer(): StatusViewer {
        return StatusViewer(
            userId = viewerId,
            userName = viewerName,
            userAvatarUrl = viewerAvatarUrl,
            timeAgo = "Recently"
        )
    }
}

@Serializable
data class SupabaseStatusLike(
    val id: String? = null,
    @SerialName("status_id")
    val statusId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String,
    @SerialName("user_avatar_url")
    val userAvatarUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
) {
    fun toStatusLiker(): StatusLiker {
        return StatusLiker(
            userId = userId,
            userName = userName,
            userAvatarUrl = userAvatarUrl
        )
    }
}

fun FamilyMember.toSupabaseContact(ownerUserId: String): SupabaseContact {
    val phoneSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(phone)
    return SupabaseContact(
        userId = ownerUserId,
        contactUserId = firebaseUid,
        contactName = name,
        contactPhone = phone,
        contactPhoneSuffix = phoneSuffix,
        relation = relation,
        isPinned = isPinned,
        isMutual = false,
        status = "ACCEPTED"
    )
}

