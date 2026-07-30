package com.family.talkly.data.models

data class MessageRequest(
    val id: String = "",
    val senderId: String = "",
    val senderPhone: String = "",
    val senderPhoneSuffix: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val receiverId: String = "",
    val receiverPhone: String = "",
    val receiverPhoneSuffix: String = "",
    val receiverName: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED, BLOCKED
    val initialMessage: String = "Hello, I would like to connect on Talkly!",
    val timestamp: Long = System.currentTimeMillis()
)
