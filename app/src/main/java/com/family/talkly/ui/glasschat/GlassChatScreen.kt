package com.family.talkly.ui.glasschat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** All callbacks the screen can fire. Wire these to your EXISTING ViewModel / repository. */
data class ChatActions(
    val onBack: () -> Unit,
    val onAudioCall: () -> Unit,
    val onVideoCall: () -> Unit,
    val onSend: () -> Unit,
    val onProfileClick: () -> Unit = {},
    val onVoiceRecord: () -> Unit = {},
    val onAttach: (AttachType) -> Unit = {},
    val onReply: (MessageItem) -> Unit = {},
    val onCopy: (MessageItem) -> Unit = {},
    val onDelete: (MessageItem) -> Unit = {},
    val onReact: (MessageItem, String) -> Unit = { _, _ -> },
)

/**
 * Telegram-style chat screen with floating liquid-glass header and composer.
 * Call this from the place that used to show your old chat screen.
 *
 * Needs edge-to-edge: call `enableEdgeToEdge()` (or WindowCompat.setDecorFitsSystemWindows(window, false))
 * in your Activity.
 */
@Composable
fun GlassChatScreen(
    header: ChatHeaderState,
    items: List<ChatItem>,                 // chronological order (oldest first)
    input: String,
    onInputChange: (String) -> Unit,
    actions: ChatActions,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0,
    mediaLoader: MediaLoader = PlaceholderMediaLoader,
    replyTo: UiReply? = null,
    onDismissReply: (() -> Unit)? = null,
    bottomOverlay: (@Composable () -> Unit)? = null,
) {
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var callState by remember { mutableStateOf(CallTabState.AudioOnly) }
    var showAttach by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MessageItem?>(null) }

    // Expanded call tab folds back by itself after 4 s of no tap.
    LaunchedEffect(callState) {
        if (callState == CallTabState.Expanded) {
            delay(4000)
            callState = CallTabState.AudioOnly
        }
    }

    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
    val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues().calculateBottomPadding()
    val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    val reversed = remember(items) { items.asReversed() }

    Box(modifier.fillMaxSize()) {
        // Everything glass blurs is inside this hazeSource box.
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            GlassBackground(Modifier.fillMaxSize())
            if (items.isNotEmpty() || header.isTyping) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPad, bottom = 84.dp + bottomInset),
                ) {
                    if (header.isTyping) item(key = "typing") { TypingBubble() }
                    lazyItems(reversed, key = { it.key }) { row ->
                        when (row) {
                            is DateItem -> DateChip(row.label)
                            is MessageItem -> MessageRow(row, mediaLoader, onLongPress = { selected = it })
                        }
                    }
                }
            }
        }

        if (items.isEmpty() && !header.isTyping) {
            EmptyChatState(
                name = header.name, hazeState = hazeState,
                onSayHi = { onInputChange("Hi 👋") },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        GlassHeader(
            state = header, callState = callState, hazeState = hazeState, loader = mediaLoader,
            onBack = actions.onBack,
            onProfileClick = actions.onProfileClick,
            onCallTabTap = { callState = CallTabState.Expanded },
            onAudioCall = { callState = CallTabState.AudioOnly; actions.onAudioCall() },
            onVideoCall = { callState = CallTabState.AudioOnly; actions.onVideoCall() },
            onCollapse = { callState = CallTabState.AudioOnly },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        ScrollDownButton(
            visible = showDown, unread = unreadCount, hazeState = hazeState,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 84.dp + bottomInset),
        )

        if (bottomOverlay != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .fillMaxWidth()
            ) {
                bottomOverlay()
            }
        } else {
            GlassComposer(
                value = input, onValueChange = onInputChange,
                onSend = { actions.onSend(); scope.launch { listState.animateScrollToItem(0) } },
                onVoice = actions.onVoiceRecord,
                onAttachClick = { showAttach = !showAttach },
                hazeState = hazeState,
                replyTo = replyTo,
                onDismissReply = onDismissReply,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        AttachMenu(
            visible = showAttach, hazeState = hazeState,
            onPick = { showAttach = false; actions.onAttach(it) },
            onDismiss = { showAttach = false },
        )

        MessageMenu(
            item = selected, hazeState = hazeState, loader = mediaLoader,
            onDismiss = { selected = null },
            onReact = { e -> selected?.let { actions.onReact(it, e) }; selected = null },
            onReply = { selected?.let(actions.onReply); selected = null },
            onCopy = { selected?.let(actions.onCopy); selected = null },
            onDelete = { selected?.let(actions.onDelete); selected = null },
        )
    }
}
