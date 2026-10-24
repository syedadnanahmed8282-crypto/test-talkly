package com.family.talkly.ui.glasschat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.MessageType
import com.family.talkly.data.models.ReactionUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Maps domain models (FamilyMember, ChatMessage) into GlassChatScreen UI models
 * (ChatHeaderState, ChatItem, MessageItem, MessageContent, DeliveryStatus).
 */
object ChatUiMapper {

    val CoilMediaLoader: MediaLoader = { url, modifier ->
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        } else {
            PlaceholderMediaLoader(url, modifier)
        }
    }

    fun mapToHeader(member: FamilyMember): ChatHeaderState {
        val statusText = when {
            member.isOnline -> "online"
            member.displayLastSeen.isNotBlank() -> member.displayLastSeen.lowercase(Locale.getDefault())
            else -> "offline"
        }
        val safeName = member.name.ifBlank { "User" }
        return ChatHeaderState(
            name = safeName,
            status = statusText,
            isTyping = member.isTyping,
            avatarUrl = member.avatarUrl,
            initials = safeName.take(2).uppercase(Locale.getDefault())
        )
    }

    fun mapToChatItems(
        messages: List<ChatMessage>,
        currentUserId: String?,
        currentPhone: String?,
        member: FamilyMember
    ): List<ChatItem> {
        val sorted = messages
            .filter { !it.isDeletedForEveryone }
            .sortedBy { it.timestamp }

        if (sorted.isEmpty()) return emptyList()

        val items = mutableListOf<ChatItem>()
        val calMsg = Calendar.getInstance()
        val calToday = Calendar.getInstance()
        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
        val currentPhoneSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone ?: "")

        var lastDayKey: String? = null

        for (msg in sorted) {
            val msgDate = Date(msg.timestamp)
            calMsg.time = msgDate
            val currentDayKey = dayFormat.format(msgDate)

            if (currentDayKey != lastDayKey) {
                val label = when {
                    isSameDay(calMsg, calToday) -> "Today"
                    isSameDay(calMsg, calYesterday) -> "Yesterday"
                    else -> displayFormat.format(msgDate)
                }
                items.add(DateItem(label))
                lastDayKey = currentDayKey
            }

            val senderSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(msg.senderId)
            val isFromMember = (msg.senderId == member.id) ||
                    (!member.firebaseUid.isNullOrBlank() && msg.senderId == member.firebaseUid) ||
                    (member.phone.isNotBlank() && msg.senderId == member.phone) ||
                    (memberSuffix.isNotBlank() && memberSuffix == senderSuffix)

            val isMine = if (isFromMember) {
                false
            } else {
                (msg.senderId == "self") ||
                        (currentUserId != null && msg.senderId == currentUserId) ||
                        (currentPhone != null && msg.senderId == currentPhone) ||
                        (currentPhoneSuffix.isNotBlank() && senderSuffix == currentPhoneSuffix)
            }

            val content = when (msg.messageType) {
                MessageType.TEXT -> MessageContent.Text(msg.textContent)
                MessageType.IMAGE -> MessageContent.Image(msg.mediaUrl)
                MessageType.VIDEO -> {
                    val durationStr = if (msg.callDurationSec > 0) {
                        String.format(Locale.getDefault(), "%d:%02d", msg.callDurationSec / 60, msg.callDurationSec % 60)
                    } else "0:00"
                    MessageContent.Video(msg.mediaUrl, durationStr)
                }
                MessageType.VOICE_NOTE -> {
                    val durationStr = if (msg.callDurationSec > 0) {
                        String.format(Locale.getDefault(), "%d:%02d", msg.callDurationSec / 60, msg.callDurationSec % 60)
                    } else {
                        // Extract seconds if in textContent (e.g. "Voice Message (5s)")
                        val match = Regex("""\((\d+)s\)""").find(msg.textContent)
                        if (match != null) {
                            val s = match.groupValues[1].toIntOrNull() ?: 0
                            String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
                        } else "0:15"
                    }
                    MessageContent.Voice(durationStr)
                }
                MessageType.CALL_LOG -> {
                    val callTypeLabel = if (msg.callType == "VIDEO") "📹 Video Call" else "📞 Audio Call"
                    val durationInfo = if (msg.callDurationSec > 0) " (${msg.callDurationSec}s)" else ""
                    MessageContent.Text(msg.textContent.ifBlank { "$callTypeLabel$durationInfo" })
                }
            }

            val deliveryStatus = if (isMine) {
                when {
                    msg.isPending || msg.isUploading -> DeliveryStatus.Sending
                    msg.isRead -> DeliveryStatus.Seen
                    msg.isDelivered -> DeliveryStatus.Delivered
                    else -> DeliveryStatus.Sent
                }
            } else null

            val uiReaction = parseUiReaction(msg.reaction)
            val uiReply = if (!msg.replyToSenderName.isNullOrBlank()) {
                UiReply(msg.replyToSenderName, msg.replyToText ?: "")
            } else null

            items.add(
                MessageItem(
                    id = msg.id.ifBlank { msg.timestamp.toString() },
                    isMine = isMine,
                    time = msg.formattedTime,
                    content = content,
                    status = deliveryStatus,
                    reaction = uiReaction,
                    replyTo = uiReply
                )
            )
        }

        return items
    }

    private fun parseUiReaction(reactionStr: String?): UiReaction? {
        if (reactionStr.isNullOrBlank()) return null
        val entries = ReactionUtils.parseReactions(reactionStr)
        if (entries.isEmpty()) return null
        val firstEmoji = entries.first().emoji
        return UiReaction(emoji = firstEmoji, count = entries.size)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
