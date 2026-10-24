package com.example.app.ui.glasschat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp

/** UI-only models. Map your existing message/user models to these in ONE place (a mapper). */

enum class CallTabState { AudioOnly, Expanded }

enum class DeliveryStatus { Sending, Sent, Delivered, Seen }

data class UiReaction(val emoji: String, val count: Int)
data class UiReply(val author: String, val preview: String)

sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Image(val url: String?) : MessageContent
    data class Video(val thumbUrl: String?, val duration: String) : MessageContent
    data class Voice(val duration: String, val progress: Float = 0f) : MessageContent
}

sealed interface ChatItem { val key: String }

data class DateItem(val label: String) : ChatItem {
    override val key: String get() = "date_$label"
}

data class MessageItem(
    val id: String,
    val isMine: Boolean,
    val time: String,
    val content: MessageContent,
    val status: DeliveryStatus? = null, // only used for my messages
    val reaction: UiReaction? = null,
    val replyTo: UiReply? = null,
) : ChatItem {
    override val key: String get() = id
}

data class ChatHeaderState(
    val name: String,
    val status: String,          // e.g. "last seen recently" / "online"
    val isTyping: Boolean = false,
    val avatarUrl: String? = null,
    val initials: String = name.take(2).uppercase(),
)

/** Plug your image library here (Coil AsyncImage, Glide, ...). Default = gradient placeholder. */
typealias MediaLoader = @Composable (url: String?, modifier: Modifier) -> Unit

val PlaceholderMediaLoader: MediaLoader = { _, modifier ->
    Box(
        modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF8DD1FC), Color(0xFF40A0E6)))
        )
    )
}

@Composable
fun ChatAvatar(
    url: String?,
    initials: String,
    size: Dp,
    loader: MediaLoader,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SkyBrush)
            .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            loader(url, Modifier.fillMaxSize())
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.36f).sp,
            )
        }
    }
}
