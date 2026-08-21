package com.family.talkly.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseProfile(
    val id: String,
    val phone: String = "",
    @SerialName("phone_suffix")
    val phoneSuffix: String = "",
    val name: String = "",
    @SerialName("avatar_url")
    val avatarUrl: String = "",
    @SerialName("cover_photo_url")
    val coverPhotoUrl: String = "",
    val bio: String = "Available on Talkly 💬",
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
