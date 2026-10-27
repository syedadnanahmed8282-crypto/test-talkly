package com.talkly.designui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkly.designui.components.ArtBox
import com.talkly.designui.components.ChatBackground
import com.talkly.designui.components.GlassCircleButton
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.fadeEdges
import com.talkly.designui.components.glass
import com.talkly.designui.theme.ChatPalette
import com.talkly.designui.theme.Ink
import com.talkly.designui.theme.LocalChatPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the screen wants to tell YOUR code. Override only what you need; the rest does nothing. */
open class ChatCallbacks {
    open fun onBack() {}
    open fun onSendText(text: String, replyToId: String?) {}
    open fun onEditText(id: String, newText: String) {}
    open fun onVoiceSend(seconds: Int) {}
    open fun onDelete(ids: List<String>, forEveryone: Boolean) {}
    open fun onStar(ids: List<String>, starred: Boolean) {}
    open fun onPin(id: String?) {}
    /** toggle: add this emoji, or remove it if you already reacted with it */
    open fun onReact(messageId: String, emoji: String) {}
    open fun onRemoveMyReaction(messageId: String) {}
    open fun onForward(ids: List<String>) {}
    open fun onOpenMedia(messageId: String, index: Int) {}
    open fun onDownload(messageId: String) {}
    open fun onTogglePlay(messageId: String) {}
    open fun onSpeed(messageId: String) {}
    open fun onAudioCall() {}
    open fun onVideoCall() {}
    open fun onOpenProfile() {}
    open fun onToggleMute() {}
    open fun onOpenTheme() {}
    open fun onOpenMediaList() {}
    open fun onClearChat() {}
    open fun onToggleBlock() {}
    open fun onPickGallery() {}
    open fun onPickCamera() {}
    open fun onPickDocument() {}
    open fun onPickAudio() {}
    open fun onPickLocation() {}
    open fun onPickContact() {}
}

class ChatUiState {
    var selected by mutableStateOf<Set<String>>(emptySet())
    var replyTo by mutableStateOf<UiMessage?>(null)
    var editing by mutableStateOf<UiMessage?>(null)
    var pinnedId by mutableStateOf<String?>(null)
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    var emojiOpen by mutableStateOf(false)
    var attachOpen by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    var selMenuOpen by mutableStateOf(false)
    var actionsExpanded by mutableStateOf(false)
    var recents by mutableStateOf<List<String>>(emptyList())
    var toast by mutableStateOf<String?>(null)
    var reactionsFor by mutableStateOf<String?>(null)
    var deleteDialog by mutableStateOf(false)

    fun closePanels() { emojiOpen = false; attachOpen = false; menuOpen = false; selMenuOpen = false; actionsExpanded = false }
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id; closePanels() }
    fun startReply(m: UiMessage, composer: ComposerState) {
        if (editing != null) { editing = null; composer.text = "" }
        replyTo = m; selected = emptySet(); closePanels()
    }
}

@Composable
fun rememberChatUiState() = remember { ChatUiState() }

val DefaultImageSlot: ImageSlot = { item, m -> ArtBox(item.art, m) }

private enum class HeaderMode { Normal, Selection, Search }

@Composable
fun ChatScreen(
    contactName: String,
    subtitle: String,
    messages: List<UiMessage>,
    modifier: Modifier = Modifier,
    palette: ChatPalette = LocalChatPalette.current,
    ui: ChatUiState = rememberChatUiState(),
    composer: ComposerState = rememberComposerState(),
    callbacks: ChatCallbacks = ChatCallbacks(),
    playback: Map<String, PlaybackState> = emptyMap(),
    downloadProgress: Map<String, Float> = emptyMap(),
    typing: Boolean = false,
    muted: Boolean = false,
    blocked: Boolean = false,
    imageContent: ImageSlot = DefaultImageSlot,
) {
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var topH by remember { mutableStateOf(110.dp) }
    var bottomH by remember { mutableStateOf(90.dp) }
    val byId = remember(messages) { messages.associateBy { it.id } }
    val selectedMsgs = ui.selected.mapNotNull { byId[it] }
    val shown = remember(messages, ui.searching, ui.query) {
        val q = ui.query.trim()
        val base = if (ui.searching && q.isNotEmpty()) {
            messages.filter { it.selectable && !it.deleted && (it.text.contains(q, true) || it.caption.contains(q, true)) }
                .ifEmpty { listOf(UiMessage("none", MsgKind.System, text = "No results found")) }
        } else messages
        base.asReversed()
    }

    // --- new message behaviour: follow if near the bottom, otherwise count unread ---
    var unread by remember { mutableIntStateOf(0) }
    val away by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }
    val last = messages.lastOrNull()
    LaunchedEffect(last?.id) {
        if (last == null) return@LaunchedEffect
        if (last.mine || !away) { listState.animateScrollToItem(0); unread = 0 } else unread++
    }
    LaunchedEffect(typing) { if (typing && !away) listState.animateScrollToItem(0) }
    LaunchedEffect(ui.toast) { if (ui.toast != null) { delay(1800); ui.toast = null } }

    val mode = when {
        ui.selected.isNotEmpty() -> HeaderMode.Selection
        ui.searching -> HeaderMode.Search
        else -> HeaderMode.Normal
    }

    Box(modifier.fillMaxSize()) {
        ChatBackground(palette, Modifier.fillMaxSize())

        LazyColumn(
            state = listState, reverseLayout = true,
            contentPadding = PaddingValues(top = topH + 12.dp, bottom = bottomH + 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize().fadeEdges(top = topH, bottom = bottomH).pointerInput(Unit) { detectTapGestures { ui.closePanels() } },
        ) {
            if (typing) item(key = "typing") { TypingBubble(Modifier.animateItem()) }
            items(shown, key = { it.id }) { msg ->
                val selected = msg.id in ui.selected
                val single = selected && ui.selected.size == 1
                Box(Modifier.animateItem().zIndex(if (single) 2f else 0f)) {
                    MessageRow(
                        msg = msg, selected = selected, selectionMode = ui.selected.isNotEmpty(),
                        playback = playback[msg.id], downloadProgress = downloadProgress[msg.id] ?: 0f,
                        imageContent = imageContent,
                        onClick = { if (ui.selected.isNotEmpty()) ui.toggle(msg.id) },
                        onLongClick = { ui.toggle(msg.id) },
                        onSwipeReply = { ui.startReply(msg, composer) },
                        onPlayToggle = { callbacks.onTogglePlay(msg.id) },
                        onSpeed = { callbacks.onSpeed(msg.id) },
                        onOpenMedia = { callbacks.onOpenMedia(msg.id, it) },
                        onDownload = { callbacks.onDownload(msg.id) },
                        onReactionsClick = { ui.reactionsFor = msg.id },
                    )
                    if (single && !msg.deleted) {
                        ReactionBar(
                            onPick = { e -> callbacks.onReact(msg.id, e); ui.selected = emptySet() },
                            modifier = Modifier.align(if (msg.mine) Alignment.TopEnd else Alignment.TopStart).offset(y = (-46).dp).padding(horizontal = 14.dp),
                        )
                    }
                }
            }
        }

        // ---------- top ----------
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).onSizeChanged { topH = with(density) { it.height.toDp() } + 8.dp },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Crossfade(mode, label = "header") { m ->
                when (m) {
                    HeaderMode.Selection -> {
                        val single = selectedMsgs.singleOrNull()
                        SelectionBar(
                            count = ui.selected.size, canReply = single != null && !single.deleted,
                            allStarred = selectedMsgs.isNotEmpty() && selectedMsgs.all { it.starred },
                            onClose = { ui.selected = emptySet(); ui.selMenuOpen = false },
                            onReply = { single?.let { ui.startReply(it, composer) } },
                            onStar = { callbacks.onStar(ui.selected.toList(), !selectedMsgs.all { it.starred }); ui.selected = emptySet(); ui.toast = "Starred" },
                            onCopy = {
                                val t = selectedMsgs.filter { it.text.isNotEmpty() }.joinToString("\n") { it.text }
                                if (t.isNotEmpty()) clipboard.setText(AnnotatedString(t))
                                ui.toast = if (t.isEmpty()) "Nothing to copy" else "Copied"; ui.selected = emptySet()
                            },
                            onDelete = { ui.deleteDialog = true },
                            onMore = { ui.selMenuOpen = !ui.selMenuOpen },
                        )
                    }
                    HeaderMode.Search -> SearchHeader(ui.query, { ui.query = it }, { ui.searching = false; ui.query = "" })
                    HeaderMode.Normal -> ChatHeader(
                        name = contactName, subtitle = subtitle, muted = muted,
                        actionsExpanded = ui.actionsExpanded, menuOpen = ui.menuOpen,
                        onBack = callbacks::onBack,
                        onCallTap = { if (!ui.actionsExpanded) { ui.closePanels(); ui.actionsExpanded = true } else { ui.actionsExpanded = false; callbacks.onAudioCall() } },
                        onVideoTap = { ui.actionsExpanded = false; callbacks.onVideoCall() },
                        onMenuTap = { val o = !ui.menuOpen; ui.closePanels(); ui.menuOpen = o },
                        onProfile = callbacks::onOpenProfile,
                    )
                }
            }
            AnimatedVisibility(ui.pinnedId != null && !ui.searching && mode == HeaderMode.Normal, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
                PinnedBanner(ui.pinnedId?.let { byId[it] }?.describe ?: "", onUnpin = { ui.pinnedId = null; callbacks.onPin(null) })
            }
        }
        HeaderMenu(
            visible = ui.menuOpen && mode == HeaderMode.Normal, muted = muted, blocked = blocked,
            actions = HeaderMenuActions(
                onProfile = { ui.closePanels(); callbacks.onOpenProfile() },
                onSearch = { ui.closePanels(); ui.searching = true },
                onMute = { ui.closePanels(); callbacks.onToggleMute() },
                onTheme = { ui.closePanels(); callbacks.onOpenTheme() },
                onMedia = { ui.closePanels(); callbacks.onOpenMediaList() },
                onClear = { ui.closePanels(); callbacks.onClearChat() },
                onBlock = { ui.closePanels(); callbacks.onToggleBlock() },
            ),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 12.dp),
        )
        val one = selectedMsgs.singleOrNull()
        SelectionMenu(
            visible = ui.selMenuOpen && mode == HeaderMode.Selection,
            canEdit = one != null && one.mine && one.kind == MsgKind.Text && !one.deleted,
            canPin = one != null,
            pinLabel = if (one != null && ui.pinnedId == one.id) "Unpin message" else "Pin message",
            onEdit = { one?.let { ui.editing = it; ui.replyTo = null; composer.text = it.text }; ui.selected = emptySet(); ui.selMenuOpen = false },
            onForward = { callbacks.onForward(ui.selected.toList()); ui.selected = emptySet(); ui.selMenuOpen = false; ui.toast = "Forwarded" },
            onPin = {
                one?.let { val un = ui.pinnedId == it.id; ui.pinnedId = if (un) null else it.id; callbacks.onPin(ui.pinnedId); ui.toast = if (un) "Unpinned" else "Pinned" }
                ui.selected = emptySet(); ui.selMenuOpen = false
            },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 12.dp),
        )

        // ---------- bottom ----------
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().onSizeChanged { bottomH = with(density) { it.height.toDp() } }) {
            AnimatedVisibility(ui.attachOpen, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                AttachSheet(
                    onGallery = { ui.closePanels(); callbacks.onPickGallery() }, onCamera = { ui.closePanels(); callbacks.onPickCamera() },
                    onDocument = { ui.closePanels(); callbacks.onPickDocument() }, onAudio = { ui.closePanels(); callbacks.onPickAudio() },
                    onLocation = { ui.closePanels(); callbacks.onPickLocation() }, onContact = { ui.closePanels(); callbacks.onPickContact() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            AnimatedVisibility(ui.emojiOpen, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                EmojiPicker(
                    recents = ui.recents,
                    onPick = { e -> composer.text += e; ui.recents = (listOf(e) + ui.recents.filter { it != e }).take(8) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            AnimatedVisibility(ui.replyTo != null) {
                ui.replyTo?.let { r -> ContextBar(Icons.Rounded.Reply, "Replying to ${if (r.mine) "yourself" else contactName}", r.describe, { ui.replyTo = null }) }
            }
            AnimatedVisibility(ui.editing != null) {
                ui.editing?.let { e -> ContextBar(Icons.Rounded.Edit, "Editing message", e.text, { ui.editing = null; composer.text = "" }) }
            }
            if (blocked) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(52.dp).glass(RoundedCornerShape(50), strong = true).padding(start = 20.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("You blocked $contactName", color = Ink.Dark, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "Unblock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(palette.bubble1).clickable { callbacks.onToggleBlock() }.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            } else {
                Composer(
                    state = composer, emojiOpen = ui.emojiOpen,
                    onToggleEmoji = { val o = !ui.emojiOpen; ui.closePanels(); ui.emojiOpen = o },
                    onAttach = { val o = !ui.attachOpen; ui.closePanels(); ui.attachOpen = o },
                    onSendText = { t ->
                        val edit = ui.editing
                        if (edit != null) { callbacks.onEditText(edit.id, t); ui.editing = null }
                        else { callbacks.onSendText(t, ui.replyTo?.id); ui.replyTo = null }
                        ui.closePanels()
                    },
                    onVoiceSend = { s -> callbacks.onVoiceSend(s) },
                    onVoiceCancel = {},
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---------- floating bits ----------
        AnimatedVisibility(
            away && !ui.emojiOpen && !ui.attachOpen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = bottomH + 12.dp),
            enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
        ) {
            Box {
                GlassCircleButton(Icons.Rounded.KeyboardArrowDown, "Scroll to latest", size = 44.dp) { scope.launch { listState.animateScrollToItem(0) }; unread = 0 }
                if (unread > 0) {
                    Text(
                        if (unread > 9) "9+" else unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).clip(CircleShape).background(palette.bubble1).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            ui.toast != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomH + 16.dp),
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(),
        ) {
            Text(ui.toast ?: "", color = Ink.Dark, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.glass(RoundedCornerShape(50), strong = true).padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }

    if (ui.deleteDialog) {
        val ids = ui.selected.toList()
        val allMine = selectedMsgs.isNotEmpty() && selectedMsgs.all { it.mine }
        DeleteDialog(
            count = ids.size, allMine = allMine,
            onForEveryone = { callbacks.onDelete(ids, true); ui.selected = emptySet(); ui.deleteDialog = false; ui.toast = "Deleted for everyone" },
            onForMe = { callbacks.onDelete(ids, false); ui.selected = emptySet(); ui.deleteDialog = false; ui.toast = "Deleted" },
            onDismiss = { ui.deleteDialog = false },
        )
    }
    ui.reactionsFor?.let { id ->
        byId[id]?.let { m ->
            ReactionsSheet(m, onRemoveMine = { callbacks.onRemoveMyReaction(id); ui.toast = "Reaction removed"; if (m.reactions.count { !it.mine } == 0) ui.reactionsFor = null }, onDismiss = { ui.reactionsFor = null })
        } ?: run { ui.reactionsFor = null }
    }
}

@Composable
private fun DeleteDialog(count: Int, allMine: Boolean, onForEveryone: () -> Unit, onForMe: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.glass(RoundedCornerShape(28.dp), strong = true).background(Color.White.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Delete $count message${if (count > 1) "s" else ""}?", color = Ink.Dark, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 10.dp))
            if (allMine) DialogButton("Delete for everyone", Ink.Danger, onForEveryone)
            DialogButton("Delete for me", Ink.Danger, onForMe)
            DialogButton("Cancel", Ink.Deep, onDismiss)
        }
    }
}

@Composable
private fun DialogButton(label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Who reacted with what; your own row says "Tap to remove". */
@Composable
private fun ReactionsSheet(msg: UiMessage, onRemoveMine: () -> Unit, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf("all") }
    val kinds = msg.reactions.map { it.emoji }.distinct()
    val activeTab = if (tab == "all" || tab in kinds) tab else "all"
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clickable(enabled = false) {}.glass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), strong = true).background(Color.White.copy(alpha = 0.4f))
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp)
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ink.Deep.copy(alpha = 0.3f)))
                Text("Reactions", color = Ink.Dark, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp, start = 4.dp))
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("all") + kinds).forEach { k ->
                        val on = k == activeTab
                        Text(
                            if (k == "all") "All ${msg.reactions.size}" else "$k ${msg.reactions.count { it.emoji == k }}",
                            color = if (on) Color.White else Ink.Deep, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) LocalChatPalette.current.bubble1 else Color.White.copy(alpha = 0.65f)).clickable { tab = k }.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
                msg.reactions.filter { activeTab == "all" || it.emoji == activeTab }.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = r.mine) { onRemoveMine() }.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(if (r.mine) "Y" else r.by.take(2).uppercase(), 40.dp, borderWidth = 0.dp)
                        Column(Modifier.weight(1f)) {
                            Text(if (r.mine) "You" else r.by, color = Ink.Dark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (r.mine) Text("Tap to remove", color = Ink.Mid, fontSize = 12.sp)
                        }
                        Text(r.emoji, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}
