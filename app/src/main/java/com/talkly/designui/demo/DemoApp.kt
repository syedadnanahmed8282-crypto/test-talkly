package com.talkly.designui.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import com.talkly.designui.call.*
import com.talkly.designui.chat.*
import com.talkly.designui.gallery.GalleryItem
import com.talkly.designui.gallery.GalleryPicker
import com.talkly.designui.profile.ProfileActions
import com.talkly.designui.profile.ProfileScreen
import com.talkly.designui.profile.presetPalette
import com.talkly.designui.theme.ChatPalettes
import com.talkly.designui.theme.TalklyTheme
import com.talkly.designui.viewer.AudioPlayerScreen
import com.talkly.designui.viewer.MediaViewer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Open this Activity to see the whole design with fake data. Delete it when you wire your real data. */
class TalklyDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TalklyDemoApp() }
    }
}

private var counter = 1000
private fun nid() = "d${counter++}"
private fun nowLabel(): String = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date())
private const val MB = 1024L * 1024L

fun demoMessages(): List<UiMessage> = listOf(
    UiMessage("1", MsgKind.Text, false, "Ki obostha", "9:44 PM"),
    UiMessage("2", MsgKind.Text, true, "Valo, tui?", "9:45 PM", reactions = listOf(Reaction("Farabi Ahmed", "👍", false))),
    UiMessage("3", MsgKind.Text, false, "Kita koros", "10:53 PM"),
    UiMessage("4", MsgKind.Text, true, "Net slow", "10:53 PM"),
    UiMessage("5", MsgKind.Text, false, "Net slow hoi geche", "10:53 PM"),
    UiMessage("6", MsgKind.Text, true, "Video cole na", "10:53 PM", reactions = listOf(Reaction("Farabi Ahmed", "😂", false), Reaction("You", "❤️", true))),
    UiMessage("dt1", MsgKind.Date, text = "August 7"),
    UiMessage("7", MsgKind.Text, true, "কি খবর", "10:24 AM", ticks = 1),
    UiMessage("dt2", MsgKind.Date, text = "Today"),
    UiMessage("v1", MsgKind.Voice, false, time = "9:41 AM", durationSec = 12),
    UiMessage("v2", MsgKind.Voice, true, time = "9:42 AM", durationSec = 8),
    UiMessage(
        "m1", MsgKind.Media, false, time = "9:44 AM", download = DownloadState.Idle,
        items = listOf(
            MediaItem(art = 0, sizeBytes = 380 * 1024), MediaItem(art = 1, sizeBytes = 2L * MB), MediaItem(art = 2, isVideo = true, durationSec = 14, sizeBytes = 18 * MB),
            MediaItem(art = 3, sizeBytes = 3L * MB), MediaItem(art = 4, sizeBytes = 900 * 1024),
        ),
    ),
    UiMessage("vd1", MsgKind.Video, true, time = "9:46 AM", durationSec = 24, sizeBytes = 86 * MB, items = listOf(MediaItem(art = 5, isVideo = true, durationSec = 24, sizeBytes = 86 * MB))),
    UiMessage("a1", MsgKind.Audio, false, time = "9:48 AM", title = "Evening Chill.mp3", durationSec = 95, sizeBytes = 3L * MB),
    UiMessage("vd2", MsgKind.Video, false, time = "9:50 AM", durationSec = 94, sizeBytes = 1229 * MB, download = DownloadState.Idle, items = listOf(MediaItem(art = 2, isVideo = true, durationSec = 94, sizeBytes = 1229 * MB))),
)

fun demoGallery(): List<GalleryItem> = List(24) { i ->
    val video = i in setOf(2, 7, 11, 16, 21)
    GalleryItem(
        id = "g$i", art = (i * 5 + i / 3) % 8, isVideo = video,
        durationSec = if (video) 9 + i else 0,
        sizeBytes = if (video) (18 + i * 20) * MB else (250L + (i * 7919) % 6000) * 1024,
        album = if (i % 2 == 0) "Camera" else "Screenshots",
    )
}

@Composable
fun TalklyDemoApp() {
    var paletteIdx by remember { mutableIntStateOf(0) }
    val palette = ChatPalettes[paletteIdx]
    TalklyTheme(palette) { DemoContent(onNextTheme = { paletteIdx = (paletteIdx + 1) % ChatPalettes.size }) }
}

@Composable
private fun DemoContent(onNextTheme: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<UiMessage>().apply { addAll(demoMessages()) } }
    val ui = rememberChatUiState()
    val composer = rememberComposerState()
    val downloads: SnapshotStateMap<String, Float> = remember { mutableStateMapOf() }
    var typing by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var pb by remember { mutableStateOf<PlaybackState?>(null) }
    var showGallery by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var coverArt by remember { mutableIntStateOf(0) }
    var viewer by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var viewerPage by remember { mutableIntStateOf(0) }
    var call by remember { mutableStateOf<CallUiState?>(null) }
    val callLocal = rememberCallLocalState()
    val replies = remember { listOf("Hmm", "Koi tui?", "Okay okay", "Hahaha", "Net slow hoi geche") }
    var replyIdx by remember { mutableIntStateOf(0) }

    fun update(id: String, f: (UiMessage) -> UiMessage) { val i = messages.indexOfFirst { it.id == id }; if (i >= 0) messages[i] = f(messages[i]) }
    fun add(m: UiMessage) {
        messages.add(m)
        scope.launch { delay(900); update(m.id) { it.copy(fresh = false) } }
    }
    fun simulateReply() = scope.launch {
        delay(700); typing = true
        delay(2000); typing = false
        add(UiMessage(nid(), MsgKind.Text, false, replies[replyIdx++ % replies.size], nowLabel(), fresh = true))
    }
    fun pidFor(msgId: String, page: Int) = if (messages.firstOrNull { it.id == msgId }?.kind == MsgKind.Media) "$msgId:$page" else msgId
    fun durFor(msgId: String, page: Int): Int {
        val m = messages.firstOrNull { it.id == msgId } ?: return 1
        return if (m.kind == MsgKind.Media) m.items.getOrNull(page)?.durationSec ?: 1 else m.durationSec.coerceAtLeast(1)
    }
    fun finishCall(text: String?, then: (() -> Unit)? = null) {
        val mode = call?.mode
        call = null
        if (text != null) add(UiMessage(nid(), MsgKind.System, text = text, fresh = true))
        if (then != null && mode != null) then()
    }
    fun startCall(mode: CallMode, incoming: Boolean) {
        if (call != null) return
        callLocal.minimized = false; callLocal.split = false; callLocal.swapped = false; callLocal.selfBig = false
        callLocal.controlsHidden = false; callLocal.replies = false
        call = CallUiState("Farabi Ahmed", mode, if (incoming) CallPhase.Incoming else CallPhase.Ringing, speaker = mode == CallMode.Video)
    }
    fun endCall(declined: Boolean = false) {
        val c = call ?: return
        if (c.phase == CallPhase.Ended) return
        val word = if (c.mode == CallMode.Video) "video" else "voice"
        val text = when {
            c.phase == CallPhase.Active -> (if (c.mode == CallMode.Video) "Video" else "Voice") + " call · " + com.talkly.designui.util.formatClock(c.seconds)
            c.phase == CallPhase.Incoming -> (if (declined) "Declined " else "Missed ") + word + " call"
            else -> "Cancelled $word call"
        }
        call = c.copy(phase = CallPhase.Ended, endText = text)
    }

    // ---- fake call state machine (replace with your Agora/Zego events) ----
    LaunchedEffect(call?.phase) {
        when (call?.phase) {
            CallPhase.Ringing -> { delay(3600); call = call?.copy(phase = CallPhase.Connecting) }
            CallPhase.Connecting -> { delay(1100); call = call?.copy(phase = CallPhase.Active, seconds = 0) }
            CallPhase.Active -> while (true) { delay(1000); call = call?.let { it.copy(seconds = it.seconds + 1, quality = if ((it.seconds + 1) % 17 == 0) 1 else if ((it.seconds + 1) % 17 == 5) 4 else it.quality) } }
            CallPhase.Ended -> { delay(2600); finishCall(call?.endText) }
            else -> {}
        }
    }
    // ---- fake playback clock ----
    LaunchedEffect(pb?.messageId, pb?.playing, pb?.speed) {
        while (pb?.playing == true) {
            delay((1000 / (pb?.speed ?: 1f)).toLong())
            val cur = pb ?: break
            pb = if (cur.seconds + 1 >= cur.durationSec) cur.copy(seconds = 0, playing = false) else cur.copy(seconds = cur.seconds + 1)
        }
    }

    val cb = remember {
        object : ChatCallbacks() {
            override fun onBack() {}
            override fun onSendText(text: String, replyToId: String?) {
                val q = replyToId?.let { id -> messages.firstOrNull { it.id == id } }?.let { Quote(if (it.mine) "You" else "Farabi Ahmed", it.describe) }
                add(UiMessage(nid(), MsgKind.Text, true, text, nowLabel(), ticks = 1, quote = q, fresh = true))
                simulateReply()
            }
            override fun onEditText(id: String, newText: String) = update(id) { it.copy(text = newText, edited = true) }
            override fun onVoiceSend(seconds: Int) { add(UiMessage(nid(), MsgKind.Voice, true, time = nowLabel(), ticks = 1, durationSec = seconds.coerceAtLeast(1), fresh = true)) }
            override fun onDelete(ids: List<String>, forEveryone: Boolean) {
                if (forEveryone) ids.forEach { id -> update(id) { it.copy(deleted = true) } }
                else messages.removeAll { it.id in ids }
            }
            override fun onStar(ids: List<String>, starred: Boolean) = ids.forEach { id -> update(id) { it.copy(starred = starred) } }
            override fun onReact(messageId: String, emoji: String) = update(messageId) { m ->
                val mine = m.reactions.firstOrNull { it.mine }
                val others = m.reactions.filter { !it.mine }
                m.copy(reactions = if (mine?.emoji == emoji) others else others + Reaction("You", emoji, true))
            }
            override fun onRemoveMyReaction(messageId: String) = update(messageId) { m -> m.copy(reactions = m.reactions.filter { !it.mine }) }
            override fun onDownload(messageId: String) {
                val m = messages.firstOrNull { it.id == messageId } ?: return
                if (m.download == DownloadState.Loading) { update(messageId) { it.copy(download = DownloadState.Idle) }; return }
                update(messageId) { it.copy(download = DownloadState.Loading) }
                downloads[messageId] = 0f
                scope.launch {
                    while ((downloads[messageId] ?: 0f) < 1f && messages.firstOrNull { it.id == messageId }?.download == DownloadState.Loading) {
                        delay(80); downloads[messageId] = ((downloads[messageId] ?: 0f) + 0.03f).coerceAtMost(1f)
                    }
                    if ((downloads[messageId] ?: 0f) >= 1f) update(messageId) { it.copy(download = DownloadState.Done) }
                }
            }
            override fun onOpenMedia(messageId: String, index: Int) { update(messageId) { it.copy(seen = true) }; viewer = messageId to index; viewerPage = index; pb = null }
            override fun onTogglePlay(messageId: String) {
                val m = messages.firstOrNull { it.id == messageId } ?: return
                if (m.kind == MsgKind.Audio) { onOpenMedia(messageId, 0); pb = PlaybackState(messageId, 0, m.durationSec.coerceAtLeast(1), true); return }
                pb = if (pb?.messageId == messageId) pb?.copy(playing = !(pb?.playing ?: false)) else PlaybackState(messageId, 0, m.durationSec.coerceAtLeast(1), true)
            }
            override fun onSpeed(messageId: String) { pb = pb?.let { it.copy(speed = if (it.speed == 1f) 1.5f else if (it.speed == 1.5f) 2f else 1f) } }
            override fun onAudioCall() = startCall(CallMode.Voice, false)
            override fun onVideoCall() = startCall(CallMode.Video, false)
            override fun onOpenProfile() { showProfile = true }
            override fun onToggleMute() { muted = !muted; ui.toast = if (muted) "Notifications muted" else "Notifications unmuted" }
            override fun onOpenTheme() { onNextTheme(); ui.toast = "Theme changed" }
            override fun onClearChat() { messages.clear(); messages.add(UiMessage(nid(), MsgKind.System, text = "Chat cleared")) }
            override fun onToggleBlock() { blocked = !blocked }
            override fun onPickGallery() { showGallery = true }
            override fun onPickCamera() { add(UiMessage(nid(), MsgKind.Video, true, time = nowLabel(), ticks = 1, durationSec = 12, sizeBytes = 25 * MB, fresh = true, items = listOf(MediaItem(art = 3, isVideo = true, durationSec = 12, sizeBytes = 25 * MB)))) }
            override fun onPickAudio() { add(UiMessage(nid(), MsgKind.Audio, true, time = nowLabel(), ticks = 1, title = "Midnight Drive.mp3", durationSec = 75, sizeBytes = 3L * MB, fresh = true)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ChatScreen(
            contactName = "Farabi Ahmed", subtitle = if (typing) "typing…" else "yesterday at 9:53 PM",
            messages = messages, ui = ui, composer = composer, callbacks = cb,
            playback = pb?.let { mapOf(it.messageId to it) } ?: emptyMap(), downloadProgress = downloads,
            typing = typing, muted = muted, blocked = blocked,
        )

        if (showGallery) GalleryPicker(
            items = demoGallery(), onDismiss = { showGallery = false },
            onSend = { sel, caption ->
                add(UiMessage(nid(), MsgKind.Media, true, time = nowLabel(), ticks = 1, caption = caption, fresh = true, items = sel.map { MediaItem(art = it.art, isVideo = it.isVideo, durationSec = it.durationSec, sizeBytes = it.sizeBytes) }))
            },
            onCamera = { cb.onPickCamera() },
        )

        viewer?.let { (id, start) ->
            val m = messages.firstOrNull { it.id == id }
            if (m == null) viewer = null
            else if (m.kind == MsgKind.Audio) {
                AudioPlayerScreen(
                    title = m.title, subtitle = "Shared by ${if (m.mine) "you" else "Farabi Ahmed"}", art = m.id.hashCode() and 7, durationSec = m.durationSec, playback = pb?.takeIf { it.messageId == id },
                    onClose = { viewer = null; pb = null },
                    onTogglePlay = { cb.onTogglePlay(id) },
                    onSeek = { f -> pb = (pb ?: PlaybackState(id, 0, m.durationSec.coerceAtLeast(1), false)).let { it.copy(seconds = (f * it.durationSec).toInt()) } },
                    onSeekBy = { d -> pb = pb?.let { it.copy(seconds = (it.seconds + d).coerceIn(0, it.durationSec)) } },
                    onSpeed = { cb.onSpeed(id) },
                )
            } else {
                val pid = pidFor(id, viewerPage)
                MediaViewer(
                    items = m.items.ifEmpty { listOf(MediaItem(art = 0, isVideo = m.kind == MsgKind.Video, durationSec = m.durationSec)) }, startIndex = start,
                    title = if (m.kind == MsgKind.Video) "Video" else "Photos", onClose = { viewer = null; pb = null },
                    playback = pb?.takeIf { it.messageId == pid },
                    onPageChanged = { viewerPage = it; pb = null },
                    onTogglePlay = { pb = if (pb?.messageId == pid) pb?.copy(playing = !(pb?.playing ?: false)) else PlaybackState(pid, 0, durFor(id, viewerPage), true) },
                    onSeek = { f -> pb = (pb?.takeIf { it.messageId == pid } ?: PlaybackState(pid, 0, durFor(id, viewerPage), false)).let { it.copy(seconds = (f * it.durationSec).toInt()) } },
                    onSeekBy = { d -> pb = pb?.takeIf { it.messageId == pid }?.let { it.copy(seconds = (it.seconds + d).coerceIn(0, it.durationSec)) } },
                    onSpeed = { cb.onSpeed(pid) },
                )
            }
        }

        if (showProfile) ProfileScreen(
            name = "Farabi Ahmed", lastSeen = "yesterday at 9:53 PM", about = "Available", muted = muted, blocked = blocked, mediaCount = 24,
            palette = presetPalette(coverArt),
            actions = ProfileActions(
                onBack = { showProfile = false },
                onAudio = { showProfile = false; startCall(CallMode.Voice, false) },
                onVideo = { showProfile = false; startCall(CallMode.Video, false) },
                onSearch = { showProfile = false; ui.searching = true },
                onToggleMute = { cb.onToggleMute() }, onTheme = { cb.onOpenTheme() },
                onClear = { cb.onClearChat() }, onBlock = { cb.onToggleBlock() },
                onChangeCover = { coverArt = (coverArt + 1) % 8 },
                onDemoIncomingVoice = { showProfile = false; scope.launch { delay(700); startCall(CallMode.Voice, true) } },
                onDemoIncomingVideo = { showProfile = false; scope.launch { delay(700); startCall(CallMode.Video, true) } },
            ),
            cover = { m -> com.talkly.designui.components.ArtBox(coverArt, m) },
        )

        call?.let { c ->
            CallScreen(
                state = c, local = callLocal,
                callbacks = object : CallCallbacks() {
                    override fun onAnswer() { call = call?.copy(phase = CallPhase.Connecting) }
                    override fun onDecline() = endCall(declined = true)
                    override fun onEnd() = endCall()
                    override fun onToggleMute() { call = call?.let { it.copy(muted = !it.muted) } }
                    override fun onToggleSpeaker() { call = call?.let { it.copy(speaker = !it.speaker) } }
                    override fun onToggleCamera() { call = call?.let { it.copy(cameraOn = !it.cameraOn) } }
                    override fun onSwitchToVideo() { call = call?.copy(mode = CallMode.Video, cameraOn = true, speaker = true) }
                    override fun onQuickReply(text: String) { add(UiMessage(nid(), MsgKind.Text, true, text, nowLabel(), ticks = 1, fresh = true)); endCall(declined = true) }
                    override fun onCallBack() { val m = call?.mode ?: CallMode.Voice; finishCall(call?.endText); scope.launch { delay(100); startCall(m, false) } }
                    override fun onCloseEnded() = finishCall(call?.endText)
                },
            )
        }
    }
}
