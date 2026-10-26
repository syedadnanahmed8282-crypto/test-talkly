package com.talkly.designui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkly.designui.chat.DefaultImageSlot
import com.talkly.designui.chat.ImageSlot
import com.talkly.designui.chat.MediaItem
import com.talkly.designui.chat.PlaybackState
import com.talkly.designui.components.ArtBox
import com.talkly.designui.components.GlassCircleButton
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.glass
import com.talkly.designui.util.formatClock
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pinch to zoom, double-tap to zoom (2.6x around the tap), drag to pan when zoomed, rubber-band back to 1x.
 * Single pointer at 1x is NOT consumed, so a surrounding pager can swipe normally.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    onZoomChange: (Boolean) -> Unit = {},
    onTap: () -> Unit = {},
    onScaleChanged: (Float) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val ox = remember { Animatable(0f) }
    val oy = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun maxX(s: Float) = (s - 1f) * size.width / 2f
    fun maxY(s: Float) = (s - 1f) * size.height / 2f

    LaunchedEffect(scale.value) { onZoomChange(scale.value > 1.02f); onScaleChanged(scale.value) }

    Box(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            if (scale.value > 1.05f) {
                                launch { scale.animateTo(1f, spring(0.7f, 300f)) }
                                launch { ox.animateTo(0f, spring(0.7f, 300f)) }
                                launch { oy.animateTo(0f, spring(0.7f, 300f)) }
                            } else {
                                val target = 2.6f
                                val rel = tap - Offset(size.width / 2f, size.height / 2f)
                                launch { scale.animateTo(target, spring(0.7f, 300f)) }
                                launch { ox.animateTo((rel.x * (1f - target)).coerceIn(-maxX(target), maxX(target)), spring(0.7f, 300f)) }
                                launch { oy.animateTo((rel.y * (1f - target)).coerceIn(-maxY(target), maxY(target)), spring(0.7f, 300f)) }
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale.value > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val c = event.calculateCentroid(useCurrent = false)
                            val rel = if (c.isSpecified) c - Offset(size.width / 2f, size.height / 2f) else Offset.Zero
                            val next = (scale.value * zoom).coerceIn(0.8f, 5f)
                            val k = next / scale.value
                            scope.launch {
                                scale.snapTo(next)
                                ox.snapTo(rel.x - (rel.x - ox.value) * k + pan.x)
                                oy.snapTo(rel.y - (rel.y - oy.value) * k + pan.y)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    scope.launch {
                        if (scale.value < 1.05f) {
                            launch { scale.animateTo(1f, spring(0.7f, 300f)) }
                            launch { ox.animateTo(0f, spring(0.7f, 300f)) }
                            launch { oy.animateTo(0f, spring(0.7f, 300f)) }
                        } else {
                            val s = scale.value.coerceAtMost(5f)
                            launch { scale.animateTo(s, spring(0.7f, 300f)) }
                            launch { ox.animateTo(ox.value.coerceIn(-maxX(s), maxX(s)), spring(0.7f, 300f)) }
                            launch { oy.animateTo(oy.value.coerceIn(-maxY(s), maxY(s)), spring(0.7f, 300f)) }
                        }
                    }
                }
            }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationX = ox.value; translationY = oy.value },
    ) { content() }
}

private val Offset.isSpecified: Boolean get() = this != Offset.Unspecified

/** Full-screen photo/video viewer: swipe pages, zoom, tap to hide/show controls, thumbnail strip. */
@Composable
fun MediaViewer(
    items: List<MediaItem>,
    startIndex: Int,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    imageContent: ImageSlot = DefaultImageSlot,
    /** playback of the *current page* when it is a video (null = not playing) */
    playback: PlaybackState? = null,
    onTogglePlay: () -> Unit = {},
    onSeek: (fraction: Float) -> Unit = {},
    onSeekBy: (seconds: Int) -> Unit = {},
    onSpeed: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    onDelete: ((MediaItem) -> Unit)? = null,
) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    var scaleNow by remember { mutableFloatStateOf(1f) }
    var chrome by remember { mutableStateOf(true) }
    val current = items.getOrNull(pager.currentPage)
    val playing = playback?.playing == true

    LaunchedEffect(pager.currentPage) { onPageChanged(pager.currentPage); zoomed = false }
    // while a video plays the controls fade after 3s; pausing brings them back
    LaunchedEffect(playing) {
        if (playing) { kotlinx.coroutines.delay(3000); chrome = false } else chrome = true
    }

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF111B2E))))) {
        HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 16.dp, userScrollEnabled = !zoomed) { page ->
            val item = items[page]
            ZoomableBox(
                Modifier.fillMaxSize(),
                onZoomChange = { if (page == pager.currentPage) zoomed = it },
                onScaleChanged = { if (page == pager.currentPage) scaleNow = it },
                onTap = { chrome = !chrome },
            ) {
                Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 78.dp, bottom = if (item.isVideo) 210.dp else 132.dp)) {
                    imageContent(item, Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)))
                    if (item.isVideo && !playing) {
                        Box(Modifier.align(Alignment.Center).size(76.dp).glass(CircleShape, dark = true).bounceClick(onClick = onTogglePlay), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter), enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircleButton(Icons.Rounded.ArrowBack, "Close", dark = true, tint = Color.White, onClick = onClose)
                Box(Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(50), dark = true).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                }
                if (items.size > 1) {
                    Box(Modifier.height(48.dp).glass(RoundedCornerShape(50), dark = true).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text("${pager.currentPage + 1} / ${items.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                if (onDelete != null) {
                    GlassCircleButton(Icons.Rounded.DeleteOutline, "Delete", dark = true, tint = Color(0xFFFF8A80)) {
                        current?.let { onDelete(it) }
                    }
                }
            }
        }

        AnimatedVisibility(zoomed || scaleNow > 1.05f, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp), enter = scaleInPop(), exit = fadeOut()) {
            Text(
                "${"%.1f".format(scaleNow)}x", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                modifier = Modifier.glass(RoundedCornerShape(50), dark = true).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(chrome && !zoomed, Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current?.isVideo == true) {
                    VideoControls(playback, current.durationSec, onTogglePlay, onSeek, onSeekBy, onSpeed, Modifier.padding(horizontal = 12.dp))
                }
                if (items.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        items.forEachIndexed { i, it ->
                            val on = i == pager.currentPage
                            val s by animateFloatAsState(if (on) 1.08f else 1f, spring(0.5f, 400f), label = "thumb")
                            ArtBox(
                                it.art,
                                Modifier.size(52.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = if (on) 1f else 0.55f }
                                    .clip(RoundedCornerShape(14.dp)).border(2.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
                                    .clickable { scope.launch { pager.animateScrollToPage(i) } },
                            ) { if (it.isVideo) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private fun scaleInPop() = androidx.compose.animation.scaleIn(spring(0.5f, 400f)) + fadeIn()

@Composable
fun VideoControls(
    playback: PlaybackState?, durationSec: Int,
    onTogglePlay: () -> Unit, onSeek: (Float) -> Unit, onSeekBy: (Int) -> Unit, onSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = playback?.playing == true
    val progress = playback?.progress ?: 0f
    val seconds = playback?.seconds ?: 0
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), dark = true).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 14.dp)) {
        SeekBar(progress, onSeek)
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(seconds), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(formatClock(durationSec), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${playback?.speed?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "1"}x", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onSpeed).padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp).bounceClick { onSeekBy(-10) })
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White).bounceClick(onClick = onTogglePlay), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color(0xFF0C2A40), modifier = Modifier.size(30.dp))
            }
            Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp).bounceClick { onSeekBy(10) })
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.VolumeUp, "Sound", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Tap or drag on the track to seek. */
@Composable
fun SeekBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableIntStateOf(1) }
    Box(
        modifier.fillMaxWidth().height(28.dp).onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { onSeek((it.x / width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume(); onSeek((change.position.x / width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.28f)))
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        Box(Modifier.offset(x = (progress.coerceIn(0f, 1f) * width / androidx.compose.ui.platform.LocalDensity.current.density).dp - 8.dp).size(16.dp).clip(CircleShape).background(Color.White))
    }
}

/** Spotify-style audio player: cover art, equalizer bars, seek bar and controls. Background follows the art colour. */
@Composable
fun AudioPlayerScreen(
    title: String, subtitle: String, art: Int, durationSec: Int, playback: PlaybackState?,
    onClose: () -> Unit, onTogglePlay: () -> Unit, onSeek: (Float) -> Unit, onSeekBy: (Int) -> Unit, onSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pair = com.talkly.designui.components.ArtPairs[art.mod(com.talkly.designui.components.ArtPairs.size)]
    val playing = playback?.playing == true
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pair.second, Color(0xFF0B1220)), endY = 1800f))) {
        Row(Modifier.statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(Icons.Rounded.KeyboardArrowDown, "Close", dark = true, tint = Color.White, onClick = onClose)
        }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 34.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ArtBox(art, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(30.dp))) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.Center).size(84.dp))
            }
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.padding(top = 14.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
            val t = rememberInfiniteTransition(label = "eq")
            Row(Modifier.fillMaxWidth().height(36.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 14) {
                    val h by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(800 + (i % 4) * 150, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 90)), label = "b$i")
                    Box(Modifier.width(5.dp).fillMaxHeight(if (playing) h else 0.3f).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.85f)))
                }
            }
        }
        VideoControls(playback, durationSec, onTogglePlay, onSeek, onSeekBy, onSpeed, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
    }
}
