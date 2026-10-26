package com.talkly.designui.chat

/**
 * Pure UI models. Map your own Supabase/Room messages to these in ONE place (a mapper function).
 * Nothing here touches your sync / send logic.
 */
enum class MsgKind { Text, Voice, Audio, Video, Media, Date, System }
enum class DownloadState { Idle, Loading, Done }

data class Quote(val name: String, val text: String)
data class Reaction(val by: String, val emoji: String, val mine: Boolean)

/** One photo or video. [url] is your Cloudinary url (null = show placeholder art). */
data class MediaItem(
    val url: String? = null,
    val art: Int = 0,
    val isVideo: Boolean = false,
    val durationSec: Int = 0,
    val sizeBytes: Long = 0,
)

data class UiMessage(
    val id: String,
    val kind: MsgKind,
    val mine: Boolean = false,
    val text: String = "",
    val time: String = "",
    /** 1 = sent, 2 = delivered/read */
    val ticks: Int = 2,
    val starred: Boolean = false,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    /** true after the receiver/sender opened the media (hides the size chip) */
    val seen: Boolean = false,
    val quote: Quote? = null,
    val reactions: List<Reaction> = emptyList(),
    val durationSec: Int = 0,
    val sizeBytes: Long = 0,
    val title: String = "",
    val items: List<MediaItem> = emptyList(),
    val caption: String = "",
    val download: DownloadState = DownloadState.Done,
    /** true only for a message that just arrived/was just sent -> plays the pop-in animation */
    val fresh: Boolean = false,
) {
    val describe: String
        get() = when (kind) {
            MsgKind.Voice -> "Voice message"
            MsgKind.Audio -> "Audio"
            MsgKind.Video -> "Video"
            MsgKind.Media -> "Photos"
            else -> text
        }
    val selectable: Boolean get() = kind != MsgKind.Date && kind != MsgKind.System
}

/** Playback progress for voice / audio / video, driven by your player. */
data class PlaybackState(
    val messageId: String,
    val seconds: Int = 0,
    val durationSec: Int = 1,
    val playing: Boolean = false,
    val speed: Float = 1f,
) {
    val progress: Float get() = if (durationSec <= 0) 0f else (seconds.toFloat() / durationSec).coerceIn(0f, 1f)
}
