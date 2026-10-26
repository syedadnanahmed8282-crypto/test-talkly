package com.talkly.designui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkly.designui.components.Waveform
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.glass
import com.talkly.designui.theme.Ink
import com.talkly.designui.theme.LocalChatPalette
import com.talkly.designui.util.formatClock
import com.talkly.designui.util.formatSize
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

typealias ImageSlot = @Composable (MediaItem, Modifier) -> Unit

/** One row of the chat list: bubble + selection highlight + swipe-to-reply + pop-in animation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    msg: UiMessage,
    selected: Boolean,
    selectionMode: Boolean,
    playback: PlaybackState?,
    downloadProgress: Float,
    imageContent: ImageSlot,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeReply: () -> Unit,
    onPlayToggle: () -> Unit,
    onSpeed: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onDownload: () -> Unit,
    onReactionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChatPalette.current
    if (!msg.selectable) {
        CenterChip(msg.text, modifier)
        return
    }
    val bg by animateColorAsState(if (selected) palette.selection else Color.Transparent, label = "sel")
    Box(
        modifier.fillMaxWidth().background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 3.dp)
    ) {
        SwipeReplyBox(enabled = !selectionMode && !msg.deleted, onTrigger = onSwipeReply) {
            Box(
                Modifier.fillMaxWidth().popIn(msg.fresh, msg.mine),
                contentAlignment = if (msg.mine) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(bottom = if (msg.reactions.isNotEmpty() && !msg.deleted) 12.dp else 0.dp)) {
                    when {
                        msg.deleted -> DeletedBubble(msg.mine)
                        msg.kind == MsgKind.Text -> TextBubble(msg)
                        msg.kind == MsgKind.Voice -> VoiceBubble(msg, playback, onPlayToggle, onSpeed)
                        msg.kind == MsgKind.Audio -> AudioCard(msg, onPlayToggle = onDownloadOrOpen(msg, onDownload) { onOpenMedia(0) })
                        msg.kind == MsgKind.Video -> VideoBubble(msg, downloadProgress, imageContent, onDownload, onOpenMedia)
                        msg.kind == MsgKind.Media -> MediaBubble(msg, downloadProgress, imageContent, onDownload, onOpenMedia)
                        else -> {}
                    }
                    if (msg.reactions.isNotEmpty() && !msg.deleted) {
                        ReactionChip(
                            msg.reactions,
                            Modifier.align(if (msg.mine) Alignment.BottomEnd else Alignment.BottomStart)
                                .offset(x = if (msg.mine) (-12).dp else 12.dp, y = 12.dp),
                            onReactionsClick,
                        )
                    }
                }
            }
        }
    }
}

private fun onDownloadOrOpen(msg: UiMessage, download: () -> Unit, open: () -> Unit): () -> Unit =
    if (!msg.mine && msg.download != DownloadState.Done) download else open

/** Pop-in used for new messages: springs up from the bottom corner of its side. */
fun Modifier.popIn(enabled: Boolean, fromRight: Boolean): Modifier = composed {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (enabled) progress.animateTo(1f, spring(0.55f, 260f)) }
    this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        scaleX = 0.6f + 0.4f * p
        scaleY = scaleX
        translationX = (if (fromRight) 1 else -1) * (1 - p) * 28.dp.toPx()
        translationY = (1 - p) * 26.dp.toPx()
        transformOrigin = TransformOrigin(if (fromRight) 1f else 0f, 1f)
    }
}

/** Drag the bubble sideways; past the threshold the reply icon turns solid and onTrigger fires on release. */
@Composable
fun SwipeReplyBox(enabled: Boolean, onTrigger: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalChatPalette.current
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val triggerPx = with(LocalDensity.current) { 56.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    Box(
        Modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (abs(offset.value) >= triggerPx) onTrigger()
                    armed = false
                    scope.launch { offset.animateTo(0f, spring(0.5f, 200f)) }
                },
                onDragCancel = { armed = false; scope.launch { offset.animateTo(0f, spring()) } },
            ) { change, dx ->
                change.consume()
                val cur = offset.value
                val resist = if (abs(cur) < triggerPx) 1f else 0.35f
                val next = (cur + dx * resist).coerceIn(-triggerPx * 1.8f, triggerPx * 1.8f)
                scope.launch { offset.snapTo(next) }
                val nowArmed = abs(next) >= triggerPx
                if (nowArmed && !armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                armed = nowArmed
            }
        }
    ) {
        val p = (abs(offset.value) / triggerPx).coerceIn(0f, 1f)
        if (p > 0.02f) {
            Box(
                Modifier.align(if (offset.value > 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 4.dp)
                    .graphicsLayer { alpha = p; scaleX = if (armed) 1.15f else 0.4f + 0.6f * p; scaleY = scaleX; rotationZ = (1 - p) * 50f }
                    .size(32.dp).clip(CircleShape)
                    .background(if (armed) palette.bubble1 else Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Reply, null, tint = if (armed) Color.White else Ink.Deep, modifier = Modifier.size(18.dp)) }
        }
        Box(Modifier.offset { IntOffset((offset.value * 0.85f).roundToInt(), 0) }) { content() }
    }
}

@Composable
private fun BubbleSurface(
    mine: Boolean,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalChatPalette.current
    val shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val bg = if (mine) Modifier.background(Brush.linearGradient(listOf(p.bubble1, p.bubble2)), shape) else Modifier.background(Color.White, shape)
    Column(
        modifier.widthIn(max = 270.dp)
            .shadow(if (mine) 4.dp else 2.dp, shape, ambientColor = Color(0x330369A1), spotColor = Color(0x550369A1))
            .then(bg).clip(shape).padding(padding),
        content = content,
    )
}

@Composable
private fun fg(mine: Boolean) = if (mine) Color.White else Ink.Dark

@Composable
private fun MetaRow(msg: UiMessage, modifier: Modifier = Modifier) {
    val color = if (msg.mine) Color.White.copy(alpha = 0.92f) else Color(0xFF4B6A80)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (msg.starred) Icon(Icons.Rounded.Star, null, tint = color, modifier = Modifier.size(11.dp))
        if (msg.edited) Text("Edited", color = color, fontSize = 11.sp, fontStyle = FontStyle.Italic)
        Text(msg.time, color = color, fontSize = 11.sp)
        if (msg.mine) Icon(if (msg.ticks >= 2) Icons.Rounded.DoneAll else Icons.Rounded.Done, null, tint = color, modifier = Modifier.size(15.dp))
    }
}

@Composable
fun TextBubble(msg: UiMessage) {
    val c = fg(msg.mine)
    BubbleSurface(msg.mine, PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        msg.quote?.let { q ->
            Column(
                Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(8.dp))
                    .background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(q.name, color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(q.text, color = c, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(msg.text, color = c, fontSize = 16.sp, modifier = Modifier.weight(1f, fill = false))
            MetaRow(msg, Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun DeletedBubble(mine: Boolean) {
    val shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Text(
        if (mine) "You deleted this message" else "This message was deleted",
        color = Ink.Mid, fontSize = 15.sp, fontStyle = FontStyle.Italic,
        modifier = Modifier.clip(shape).background(Color.White.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.8f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun VoiceBubble(msg: UiMessage, playback: PlaybackState?, onPlayToggle: () -> Unit, onSpeed: () -> Unit) {
    val p = LocalChatPalette.current
    val mine = msg.mine
    val playing = playback?.playing == true
    val progress = playback?.progress ?: 0f
    val label = if (playback != null && (playing || playback.seconds > 0)) formatClock(playback.seconds) else formatClock(msg.durationSec)
    BubbleSurface(mine, PaddingValues(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 6.dp), Modifier.widthIn(min = 236.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (mine) Color.White else p.bubble1).bounceClick(onClick = onPlayToggle),
                contentAlignment = Alignment.Center,
            ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = if (mine) p.bubble1 else Color.White) }
            Spacer(Modifier.width(10.dp))
            Waveform(
                seed = (msg.id.hashCode() and 0x7fffffff) % 1000, progress = progress,
                filled = if (mine) Color.White else p.bubble1,
                dim = if (mine) Color.White.copy(alpha = 0.45f) else Ink.Deep.copy(alpha = 0.28f),
                modifier = Modifier.weight(1f).height(28.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, color = fg(mine), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            AnimatedVisibility(playing, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Text(
                    "${playback?.speed?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}x",
                    color = if (mine) Color.White else p.bubble1, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50))
                        .background(if (mine) Color.White.copy(alpha = 0.28f) else p.bubble1.copy(alpha = 0.12f))
                        .clickable(onClick = onSpeed).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        MetaRow(msg, Modifier.align(Alignment.End))
    }
}

@Composable
fun AudioCard(msg: UiMessage, onPlayToggle: () -> Unit) {
    val p = LocalChatPalette.current
    val mine = msg.mine
    BubbleSurface(mine, PaddingValues(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 6.dp), Modifier.widthIn(min = 244.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.talkly.designui.components.ArtBox(
                msg.id.hashCode() and 7, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
            ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(msg.title, color = fg(mine), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatClock(msg.durationSec)} · ${formatSize(msg.sizeBytes)}",
                    color = if (mine) Color.White.copy(alpha = 0.88f) else Ink.Mid, fontSize = 12.sp,
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (mine) Color.White else p.bubble1).bounceClick(onClick = onPlayToggle),
                contentAlignment = Alignment.Center,
            ) {
                val icon = if (!mine && msg.download != DownloadState.Done) Icons.Rounded.Download else Icons.Rounded.PlayArrow
                Icon(icon, null, tint = if (mine) p.bubble1 else Color.White)
            }
        }
        MetaRow(msg, Modifier.align(Alignment.End))
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, small: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0x9E0C2A40))
            .padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 1.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
        Text(text, color = Color.White, fontSize = if (small) 9.5.sp else 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Download button -> progress ring -> (nothing when done). Purely visual; the whole thumbnail is the tap target. */
@Composable
private fun DownloadOverlay(state: DownloadState, progress: Float, totalBytes: Long) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            DownloadState.Idle -> {
                val t = rememberInfiniteTransition(label = "bob")
                val bob by t.animateFloat(-2f, 3f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color(0x9E0C2A40)).border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(26.dp).offset(y = bob.dp)) }
            }
            DownloadState.Loading -> {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0x9E0C2A40)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize().padding(2.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.28f), strokeWidth = 3.dp)
                    Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Chip("${formatSize((totalBytes * progress).toLong())} / ${formatSize(totalBytes)}", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            }
            DownloadState.Done -> {}
        }
    }
}

private fun chipVisible(msg: UiMessage) = if (msg.mine) !msg.seen else msg.download != DownloadState.Done

@Composable
fun VideoBubble(msg: UiMessage, progress: Float, imageContent: ImageSlot, onDownload: () -> Unit, onOpen: (Int) -> Unit) {
    val item = msg.items.firstOrNull() ?: MediaItem(art = msg.id.hashCode() and 7, isVideo = true, durationSec = msg.durationSec, sizeBytes = msg.sizeBytes)
    val blurred = !msg.mine && msg.download != DownloadState.Done
    val blurDp by animateDpAsState(if (blurred) 14.dp else 0.dp, tween(800), label = "blur")
    BubbleSurface(msg.mine, PaddingValues(4.dp)) {
        Box(Modifier.size(220.dp, 150.dp).clip(RoundedCornerShape(16.dp)).clickable { if (blurred) onDownload() else onOpen(0) }) {
            imageContent(item, Modifier.fillMaxSize().blur(blurDp, BlurredEdgeTreatment.Rectangle))
            if (!blurred) {
                Box(
                    Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Color(0x800C2A40)).border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            DownloadOverlay(if (blurred) msg.download else DownloadState.Done, progress, item.sizeBytes.takeIf { it > 0 } ?: msg.sizeBytes)
            if (chipVisible(msg)) Chip(formatSize(item.sizeBytes.takeIf { it > 0 } ?: msg.sizeBytes), Modifier.align(Alignment.TopStart).padding(8.dp))
            Chip(formatClock(item.durationSec.takeIf { it > 0 } ?: msg.durationSec), Modifier.align(Alignment.BottomStart).padding(8.dp), Icons.Rounded.Videocam)
            Chip(msg.time, Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
    }
}

@Composable
fun MediaBubble(msg: UiMessage, progress: Float, imageContent: ImageSlot, onDownload: () -> Unit, onOpen: (Int) -> Unit) {
    val shown = msg.items.take(4)
    val extra = msg.items.size - 4
    val blurred = !msg.mine && msg.download != DownloadState.Done
    val blurDp by animateDpAsState(if (blurred) 14.dp else 0.dp, tween(800), label = "blur")
    val total = msg.items.sumOf { it.sizeBytes }
    @Composable
    fun Cell(i: Int, w: Dp, h: Dp) {
        val it = shown[i]
        Box(Modifier.size(w, h).clip(RoundedCornerShape(12.dp)).clickable { if (blurred) onDownload() else onOpen(i) }) {
            imageContent(it, Modifier.fillMaxSize().blur(blurDp, BlurredEdgeTreatment.Rectangle))
            if (!blurred && it.isVideo) {
                Box(Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape).background(Color(0x800C2A40)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            if (chipVisible(msg)) Chip(formatSize(it.sizeBytes), Modifier.align(Alignment.TopStart).padding(6.dp), small = true)
            if (i == 3 && extra > 0) {
                Box(Modifier.fillMaxSize().background(Color(0x940C2A40)), contentAlignment = Alignment.Center) {
                    Text("+$extra", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
    BubbleSurface(msg.mine, PaddingValues(4.dp)) {
        Box {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                when (shown.size) {
                    1 -> Cell(0, 219.dp, 150.dp)
                    2 -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Cell(0, 108.dp, 108.dp); Cell(1, 108.dp, 108.dp) }
                    3 -> { Cell(0, 219.dp, 108.dp); Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Cell(1, 108.dp, 108.dp); Cell(2, 108.dp, 108.dp) } }
                    else -> if (shown.size >= 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Cell(0, 108.dp, 108.dp); Cell(1, 108.dp, 108.dp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Cell(2, 108.dp, 108.dp); Cell(3, 108.dp, 108.dp) }
                    }
                }
            }
            Box(Modifier.matchParentSize()) { DownloadOverlay(if (blurred) msg.download else DownloadState.Done, progress, total) }
            if (msg.caption.isEmpty()) Chip(msg.time, Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        if (msg.caption.isNotEmpty()) {
            Text(msg.caption, color = fg(msg.mine), fontSize = 15.sp, modifier = Modifier.widthIn(max = 216.dp).padding(start = 8.dp, end = 8.dp, top = 6.dp))
            MetaRow(msg, Modifier.align(Alignment.End).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun ReactionChip(reactions: List<Reaction>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val emojis = reactions.map { it.emoji }.distinct().take(3).joinToString("")
    val label = if (reactions.size > 1) "$emojis ${reactions.size}" else emojis
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else 0.2f, spring(0.45f, 400f), label = "chip")
    Text(
        label, fontSize = 14.sp,
        modifier = modifier.graphicsLayer { scaleX = s; scaleY = s }
            .shadow(3.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Color.White)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun CenterChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            text, color = Ink.Deep, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun TypingBubble(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(
        modifier.padding(horizontal = 14.dp, vertical = 3.dp).shadow(2.dp, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (i in 0..2) {
            val y by t.animateFloat(
                0f, -6f,
                infiniteRepeatable(keyframes { durationMillis = 1100; 0f at 0; -6f at 330; 0f at 660; 0f at 1100 }, initialStartOffset = StartOffset(i * 150)),
                label = "dot$i",
            )
            Box(Modifier.size(8.dp).offset(y = y.dp).clip(CircleShape).background(Color(0xFF4B6A80)))
        }
    }
}
