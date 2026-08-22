package com.family.talkly.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseFcmToken(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    val token: String,
    @SerialName("device_id")
    val deviceId: String = "",
    val platform: String = "android",
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
