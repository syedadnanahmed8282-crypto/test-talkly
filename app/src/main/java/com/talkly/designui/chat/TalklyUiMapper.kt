package com.talkly.designui.chat

import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.MessageType
import com.family.talkly.data.models.ReactionUtils
import com.family.talkly.util.PhoneUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure mapper that converts Talkly ChatMessage models to TalklyDesignUI UiMessage models.
 * Preserves stable IDs to prevent flickers.
 */
object TalklyUiMapper {

    fun mapToUiMessages(
        messages: List<ChatMessage>,
        currentUserId: String?,
        currentPhone: String?,
        member: FamilyMember
    ): List<UiMessage> {
        val sorted = messages
            .filter { !it.deletedForUsers.contains(currentUserId ?: "self") }
            .sortedBy { it.timestamp }

        if (sorted.isEmpty()) return emptyList()

        val items = mutableListOf<UiMessage>()
        val calMsg = Calendar.getInstance()
        val calToday = Calendar.getInstance()
        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val memberSuffix = PhoneUtils.extractPhoneSuffix(member.phone)
        val currentPhoneSuffix = PhoneUtils.extractPhoneSuffix(currentPhone ?: "")

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
                items.add(
                    UiMessage(
                        id = "dt_${currentDayKey}",
                        kind = MsgKind.Date,
                        text = label
                    )
                )
                lastDayKey = currentDayKey
            }

            val senderSuffix = PhoneUtils.extractPhoneSuffix(msg.senderId)
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

            val kind = when (msg.messageType) {
                MessageType.TEXT -> MsgKind.Text
                MessageType.IMAGE -> MsgKind.Media
                MessageType.VIDEO -> MsgKind.Video
                MessageType.VOICE_NOTE -> MsgKind.Voice
                MessageType.CALL_LOG -> MsgKind.Text
            }

            val reactionsList = if (!msg.reaction.isNullOrBlank()) {
                ReactionUtils.parseReactions(msg.reaction, msg.senderId, msg.senderName, msg.timestamp).map { r ->
                    val isMyReaction = r.userId == "self" || (currentUserId != null && r.userId == currentUserId)
                    Reaction(by = r.userName, emoji = r.emoji, mine = isMyReaction)
                }
            } else emptyList()

            val quote = if (!msg.replyToSenderName.isNullOrBlank()) {
                Quote(name = msg.replyToSenderName, text = msg.replyToText ?: "")
            } else null

            val timeStr = timeFormat.format(msgDate)

            val ticksCount = when {
                msg.isRead -> 2
                msg.isDelivered -> 2
                else -> 1
            }

            val mediaItems = when (msg.messageType) {
                MessageType.IMAGE -> {
                    val splitUrls = (msg.mediaUrl ?: "").split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
                    if (splitUrls.size > 1) {
                        splitUrls.map { url ->
                            MediaItem(
                                url = url,
                                isVideo = false,
                                sizeBytes = msg.fileSizeBytes ?: 0L
                            )
                        }
                    } else {
                        listOf(
                            MediaItem(
                                url = msg.mediaUrl,
                                isVideo = false,
                                sizeBytes = msg.fileSizeBytes ?: 0L
                            )
                        )
                    }
                }
                MessageType.VIDEO -> listOf(
                    MediaItem(
                        url = msg.mediaUrl,
                        isVideo = true,
                        durationSec = msg.callDurationSec,
                        sizeBytes = msg.fileSizeBytes ?: 0L
                    )
                )
                else -> emptyList()
            }

            val voiceSec = if (msg.messageType == MessageType.VOICE_NOTE) {
                if (msg.callDurationSec > 0) msg.callDurationSec
                else {
                    val match = Regex("""\((\d+)s\)""").find(msg.textContent)
                    match?.groupValues?.get(1)?.toIntOrNull() ?: 15
                }
            } else 0

            val textVal = when (msg.messageType) {
                MessageType.CALL_LOG -> {
                    val icon = if (msg.callType == "VIDEO") "📹 Video Call" else "📞 Audio Call"
                    val dur = if (msg.callDurationSec > 0) " (${msg.callDurationSec}s)" else ""
                    msg.textContent.ifBlank { "$icon$dur" }
                }
                else -> msg.textContent
            }

            val captionVal = if (mediaItems.isNotEmpty()) {
                val t = msg.textContent.trim()
                if (t.isNotBlank() && !t.equals("Photo", ignoreCase = true) && !t.equals("Video", ignoreCase = true)) {
                    t
                } else ""
            } else ""

            val downloadState = when {
                msg.isUploading || msg.isPending -> DownloadState.Loading
                else -> DownloadState.Done
            }

            items.add(
                UiMessage(
                    id = msg.id.ifBlank { "msg_${msg.timestamp}" },
                    kind = kind,
                    mine = isMine,
                    text = textVal,
                    time = timeStr,
                    ticks = ticksCount,
                    starred = msg.isStarred,
                    edited = msg.isEdited,
                    deleted = msg.isDeletedForEveryone,
                    seen = msg.isRead,
                    quote = quote,
                    reactions = reactionsList,
                    durationSec = voiceSec,
                    sizeBytes = msg.fileSizeBytes ?: 0L,
                    items = mediaItems,
                    caption = captionVal,
                    download = downloadState,
                    fresh = false
                )
            )
        }

        return groupMediaAlbums(items)
    }

    private fun groupMediaAlbums(messages: List<UiMessage>): List<UiMessage> {
        if (messages.size <= 1) return messages
        val result = mutableListOf<UiMessage>()
        var i = 0
        while (i < messages.size) {
            val current = messages[i]
            if (!current.deleted && (current.kind == MsgKind.Media || current.kind == MsgKind.Video) && current.caption.isBlank()) {
                val albumItems = current.items.toMutableList()
                var j = i + 1
                while (j < messages.size) {
                    val next = messages[j]
                    if (!next.deleted && (next.kind == MsgKind.Media || next.kind == MsgKind.Video) &&
                        next.mine == current.mine && next.caption.isBlank() && next.time == current.time
                    ) {
                        albumItems.addAll(next.items)
                        j++
                    } else {
                        break
                    }
                }
                if (albumItems.size > 1) {
                    result.add(
                        current.copy(
                            kind = MsgKind.Media,
                            items = albumItems,
                            sizeBytes = albumItems.sumOf { it.sizeBytes }
                        )
                    )
                    i = j
                    continue
                }
            }
            result.add(current)
            i++
        }
        return result
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun loadDeviceGallery(context: android.content.Context): List<com.talkly.designui.gallery.GalleryItem> {
        val items = mutableListOf<com.talkly.designui.gallery.GalleryItem>()
        try {
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns._ID,
                android.provider.MediaStore.MediaColumns.DATA,
                android.provider.MediaStore.MediaColumns.SIZE,
                android.provider.MediaStore.MediaColumns.DATE_ADDED
            )
            val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC"

            val imageUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(imageUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
                var count = 0
                while (cursor.moveToNext() && count < 80) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol)
                    val size = cursor.getLong(sizeCol)
                    val contentUri = android.content.ContentUris.withAppendedId(imageUri, id).toString()
                    items.add(
                        com.talkly.designui.gallery.GalleryItem(
                            id = "img_$id",
                            uri = if (!path.isNullOrBlank()) path else contentUri,
                            isVideo = false,
                            sizeBytes = size,
                            album = "Camera"
                        )
                    )
                    count++
                }
            }

            val videoUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val videoProj = arrayOf(
                android.provider.MediaStore.Video.VideoColumns._ID,
                android.provider.MediaStore.Video.VideoColumns.DATA,
                android.provider.MediaStore.Video.VideoColumns.SIZE,
                android.provider.MediaStore.Video.VideoColumns.DURATION,
                android.provider.MediaStore.Video.VideoColumns.DATE_ADDED
            )
            context.contentResolver.query(videoUri, videoProj, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.VideoColumns._ID)
                val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.VideoColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.VideoColumns.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.VideoColumns.DURATION)
                var count = 0
                while (cursor.moveToNext() && count < 40) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol)
                    val size = cursor.getLong(sizeCol)
                    val durationMs = cursor.getLong(durCol)
                    val contentUri = android.content.ContentUris.withAppendedId(videoUri, id).toString()
                    items.add(
                        com.talkly.designui.gallery.GalleryItem(
                            id = "vid_$id",
                            uri = if (!path.isNullOrBlank()) path else contentUri,
                            isVideo = true,
                            durationSec = (durationMs / 1000).toInt(),
                            sizeBytes = size,
                            album = "Videos"
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TalklyUiMapper", "Error loading MediaStore", e)
        }

        if (items.isEmpty()) {
            return List(16) { i ->
                val isVid = i % 4 == 1
                com.talkly.designui.gallery.GalleryItem(
                    id = "sample_$i",
                    art = i % 8,
                    isVideo = isVid,
                    durationSec = if (isVid) 15 + i else 0,
                    sizeBytes = 1024L * 1024L * (i + 1),
                    album = if (i % 2 == 0) "Camera" else "Screenshots"
                )
            }
        }
        return items
    }
}
