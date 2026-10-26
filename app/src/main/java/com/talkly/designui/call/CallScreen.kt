package com.talkly.designui.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkly.designui.chat.Avatar
import com.talkly.designui.components.GlassCircleButton
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.glass
import com.talkly.designui.theme.Ink
import com.talkly.designui.theme.LocalChatPalette
import com.talkly.designui.util.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class CallMode { Voice, Video }
enum class CallPhase { Incoming, Ringing, Connecting, Active, Ended }
enum class Corner { TL, TR, BL, BR }

/** Drive this from your calling SDK (Agora/Zego) events. */
data class CallUiState(
    val peerName: String,
    val mode: CallMode,
    val phase: CallPhase,
    val seconds: Int = 0,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val cameraOn: Boolean = true,
    /** 1 (poor) .. 4 (great) */
    val quality: Int = 4,
    /** shown on the "ended" screen, e.g. "Voice call · 2:14" or "Missed video call" */
    val endText: String = "",
)

/** Purely visual toggles; they never leave the screen. */
class CallLocalState {
    var minimized by mutableStateOf(false)
    var split by mutableStateOf(false)
    var swapped by mutableStateOf(false)
    var selfBig by mutableStateOf(false)
    var controlsHidden by mutableStateOf(false)
    var selfCorner by mutableStateOf(Corner.TR)
    var miniCorner by mutableStateOf(Corner.BR)
    var replies by mutableStateOf(false)
    var flipCount by mutableIntStateOf(0)
    val reactions = mutableStateListOf<Pair<Long, String>>()
}

@Composable
fun rememberCallLocalState() = remember { CallLocalState() }

open class CallCallbacks {
    open fun onAnswer() {}
    open fun onDecline() {}
    open fun onEnd() {}
    open fun onToggleMute() {}
    open fun onToggleSpeaker() {}
    open fun onToggleCamera() {}
    open fun onFlipCamera() {}
    open fun onSwitchToVideo() {}
    open fun onQuickReply(text: String) {}
    open fun onCallBack() {}
    open fun onCloseEnded() {}
}

private data class BoxRect(val x: Dp, val y: Dp, val w: Dp, val h: Dp, val r: Dp)

@Composable
private fun animatedRect(t: BoxRect, snapNow: Boolean): BoxRect {
    val spec: FiniteAnimationSpec<Dp> = if (snapNow) snap() else spring(0.8f, 300f)
    val x by animateDpAsState(t.x, spec, label = "x")
    val y by animateDpAsState(t.y, spec, label = "y")
    val w by animateDpAsState(t.w, spec, label = "w")
    val h by animateDpAsState(t.h, spec, label = "h")
    val r by animateDpAsState(t.r, spec, label = "r")
    return BoxRect(x, y, w, h, r)
}

private fun cornerXY(c: Corner, w: Dp, h: Dp, sw: Dp, sh: Dp, top: Dp, bottom: Dp, margin: Dp): Pair<Dp, Dp> {
    val x = if (c == Corner.TL || c == Corner.BL) margin else sw - margin - w
    val y = if (c == Corner.TL || c == Corner.TR) top else sh - bottom - h
    return x to y
}

/** Placeholder "video": moving aurora gradient + silhouette. Replace via the remoteVideo / localVideo slots. */
@Composable
fun PlaceholderVideo(remote: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val shift by t.animateFloat(0f, 900f, infiniteRepeatable(tween(if (remote) 10000 else 12000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    val colors = if (remote) listOf(Color(0xFF1D4ED8), Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFF59E0B))
    else listOf(Color(0xFF0F766E), Color(0xFF0369A1), Color(0xFF4338CA))
    Box(modifier.background(Brush.linearGradient(colors, start = Offset(shift - 600f, 0f), end = Offset(shift + 600f, 1400f)))) {
        Canvas(Modifier.align(Alignment.Center).fillMaxSize(0.58f)) {
            val c = Color.White.copy(alpha = 0.4f)
            val r = size.minDimension * 0.16f
            drawCircle(c, r, Offset(size.width / 2, size.height * 0.34f))
            drawOval(c, Offset(size.width * 0.18f, size.height * 0.58f), Size(size.width * 0.64f, size.height * 0.7f))
        }
    }
}

@Composable
fun CallScreen(
    state: CallUiState,
    local: CallLocalState,
    callbacks: CallCallbacks,
    modifier: Modifier = Modifier,
    remoteVideo: @Composable (Modifier) -> Unit = { PlaceholderVideo(true, it) },
    localVideo: @Composable (Modifier) -> Unit = { PlaceholderVideo(false, it) },
) {
    val isVideo = state.mode == CallMode.Video
    val active = state.phase == CallPhase.Active
    val stageVideo = active && isVideo
    val canMinimize = state.phase == CallPhase.Ringing || state.phase == CallPhase.Connecting || active
    val minimized = local.minimized && canMinimize
    val origin = if (stageVideo) when (local.miniCorner) {
        Corner.TL -> TransformOrigin(0.1f, 0.1f); Corner.TR -> TransformOrigin(0.9f, 0.1f)
        Corner.BL -> TransformOrigin(0.1f, 0.9f); Corner.BR -> TransformOrigin(0.9f, 0.9f)
    } else TransformOrigin(0.5f, 0.1f)

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            !minimized,
            enter = fadeIn(tween(250)) + scaleIn(spring(0.8f, 280f), initialScale = 0.2f, transformOrigin = origin),
            exit = fadeOut(tween(250)) + scaleOut(tween(350), targetScale = 0.2f, transformOrigin = origin),
        ) { FullCall(state, local, callbacks, remoteVideo, localVideo) }

        AnimatedVisibility(minimized && stageVideo, enter = scaleIn(spring(0.55f, 300f), initialScale = 0.3f) + fadeIn(), exit = scaleOut() + fadeOut()) {
            CallMiniPip(state, local, remoteVideo)
        }
        AnimatedVisibility(minimized && !stageVideo, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            CallMiniBar(state, onExpand = { local.minimized = false }, onEnd = callbacks::onEnd)
        }
    }
}

@Composable
private fun FullCall(
    state: CallUiState, local: CallLocalState, cb: CallCallbacks,
    remoteVideo: @Composable (Modifier) -> Unit, localVideo: @Composable (Modifier) -> Unit,
) {
    val p = LocalChatPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isVideo = state.mode == CallMode.Video
    val active = state.phase == CallPhase.Active
    val stageVideo = active && isVideo
    val pre = isVideo && (state.phase == CallPhase.Incoming || state.phase == CallPhase.Ringing || state.phase == CallPhase.Connecting)
    val hide = local.controlsHidden && stageVideo

    // follow reactions' lifetime
    LaunchedEffect(local.reactions.size) { if (local.reactions.isNotEmpty()) { delay(2200); if (local.reactions.isNotEmpty()) local.reactions.removeAt(0) } }

    BoxWithConstraints(Modifier.fillMaxSize().then(if (stageVideo) Modifier.background(Color(0xFF0B1220)) else Modifier.background(Brush.linearGradient(listOf(p.bubble1, Color(0xFF0B1220)), start = Offset.Zero, end = Offset(300f, 2600f))))) {
        val W = maxWidth
        val H = maxHeight

        // ---------- floating blobs ----------
        val t = rememberInfiniteTransition(label = "blobs")
        val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
        Box(Modifier.offset(x = (-70).dp + 40.dp * b, y = 70.dp).size(280.dp).background(Brush.radialGradient(listOf(p.blobA.copy(alpha = 0.5f), Color.Transparent)), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 90.dp, y = (-110).dp + 40.dp * b).size(320.dp).background(Brush.radialGradient(listOf(p.bubble2.copy(alpha = 0.6f), Color.Transparent)), CircleShape))

        // ---------- video boxes (self full-screen preview -> morphs to corner on answer) ----------
        if (isVideo && state.phase != CallPhase.Ended) {
            val small = if (local.selfBig) 164.dp to 232.dp else 104.dp to 148.dp
            val whoSmall: Int = when { !active || local.split -> 0; local.swapped -> 1 /* remote small */; else -> 2 /* self small */ }
            var dragging by remember { mutableStateOf(false) }
            var drag by remember { mutableStateOf(Offset.Zero) }
            val dragDp = with(density) { Pair(drag.x.toDp(), drag.y.toDp()) }
            val (cx, cy) = cornerXY(local.selfCorner, small.first, small.second, W, H, 84.dp, 214.dp, 14.dp)
            val smallRect = BoxRect(cx + if (dragging) dragDp.first else 0.dp, cy + if (dragging) dragDp.second else 0.dp, small.first, small.second, 22.dp)
            val fullRect = BoxRect(0.dp, 0.dp, W, H, 0.dp)
            val half = (H - 76.dp - 200.dp - 10.dp) / 2
            val topHalf = BoxRect(12.dp, 76.dp, W - 24.dp, half, 28.dp)
            val botHalf = BoxRect(12.dp, 76.dp + half + 10.dp, W - 24.dp, half, 28.dp)
            val hiddenRemote = BoxRect(W / 2 - 60.dp, H / 2 - 90.dp, 120.dp, 170.dp, 24.dp)
            val (remoteT, selfT) = when {
                !active -> hiddenRemote to fullRect
                local.split -> if (local.swapped) botHalf to topHalf else topHalf to botHalf
                local.swapped -> smallRect to fullRect
                else -> fullRect to smallRect
            }
            val remoteR = animatedRect(remoteT, dragging && whoSmall == 1)
            val selfR = animatedRect(selfT, dragging && whoSmall == 2)
            val remoteAlpha by animateFloatAsState(if (active) 1f else 0f, tween(700), label = "ra")

            fun snapCorner() {
                val xPx = with(density) { (cx + small.first / 2).toPx() } + drag.x
                val yPx = with(density) { (cy + small.second / 2).toPx() } + drag.y
                val top = yPx < with(density) { H.toPx() } / 2
                val left = xPx < with(density) { W.toPx() } / 2
                local.selfCorner = if (top && left) Corner.TL else if (top) Corner.TR else if (left) Corner.BL else Corner.BR
            }

            @Composable
            fun VideoBox(rect: BoxRect, isSmall: Boolean, z: Float, alpha: Float, label: String, content: @Composable () -> Unit) {
                Box(
                    Modifier.offset(rect.x, rect.y).size(rect.w, rect.h).zIndex(z).graphicsLayer { this.alpha = alpha }
                        .then(if (isSmall) Modifier.shadow(14.dp, RoundedCornerShape(rect.r)) else Modifier)
                        .clip(RoundedCornerShape(rect.r))
                        .pointerInput(isSmall, local.split) {
                            detectTapGestures(onTap = {
                                when {
                                    isSmall || local.split -> local.swapped = !local.swapped
                                    else -> local.controlsHidden = !local.controlsHidden
                                }
                            })
                        }
                        .pointerInput(isSmall, local.selfCorner, local.selfBig) {
                            if (isSmall) detectDragGestures(
                                onDragStart = { dragging = true; drag = Offset.Zero },
                                onDrag = { c, d -> c.consume(); drag += d },
                                onDragEnd = { snapCorner(); dragging = false; drag = Offset.Zero },
                                onDragCancel = { dragging = false; drag = Offset.Zero },
                            )
                        },
                ) {
                    content()
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0x8C0C1220)).padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (label == "You" && state.muted) Icon(Icons.Rounded.MicOff, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(14.dp))
                    }
                    if (isSmall) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).glass(CircleShape, dark = true).bounceClick { local.selfBig = !local.selfBig },
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (local.selfBig) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull, "Resize", tint = Color.White, modifier = Modifier.size(15.dp)) }
                    }
                }
            }

            VideoBox(remoteR, whoSmall == 1, if (whoSmall == 1) 5f else 1f, remoteAlpha, state.peerName) { remoteVideo(Modifier.fillMaxSize()) }
            val flip = remember { Animatable(0f) }
            LaunchedEffect(local.flipCount) {
                if (local.flipCount > 0) { flip.animateTo(90f, tween(250)); flip.snapTo(-90f); flip.animateTo(0f, tween(250)) }
            }
            VideoBox(selfR, whoSmall == 2, if (whoSmall == 2) 5f else 1f, 1f, "You") {
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = flip.value; cameraDistance = 12f * density.density }) {
                    if (state.cameraOn) localVideo(Modifier.fillMaxSize())
                    else Column(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2433), Color(0xFF0E1522)))),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Avatar("Y", 52.dp)
                        Icon(Icons.Rounded.VideocamOff, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 8.dp).size(24.dp))
                    }
                }
            }
        }

        // ---------- dim + avatar stage ----------
        val dim by animateFloatAsState(if (pre) 1f else 0f, tween(600), label = "dim")
        Box(Modifier.fillMaxSize().zIndex(2f).graphicsLayer { alpha = dim }.background(Brush.verticalGradient(listOf(Color(0x990A1020), Color(0x380A1020), Color(0xB80A1020)))))
        val stageAlpha by animateFloatAsState(if (stageVideo) 0f else 1f, tween(550), label = "stage")
        val stageScale by animateFloatAsState(if (stageVideo) 0.88f else 1f, spring(0.8f, 250f), label = "stageS")
        Column(
            Modifier.fillMaxSize().zIndex(3f).padding(top = if (pre) 130.dp else 150.dp).graphicsLayer { alpha = stageAlpha; scaleX = stageScale; scaleY = stageScale },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ringDur = when (state.phase) { CallPhase.Incoming -> 1800; CallPhase.Active -> 3800; else -> 2400 }
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                if (state.phase != CallPhase.Ended) for (i in 0..2) {
                    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ringDur, easing = LinearEasing), initialStartOffset = StartOffset(i * ringDur / 3)), label = "r$i")
                    Box(Modifier.size(132.dp).graphicsLayer { scaleX = 1f + 1.4f * ring; scaleY = scaleX; alpha = 0.55f * (1f - ring) }.border(2.dp, Color.White, CircleShape))
                }
                Avatar(state.peerName.take(2).uppercase(), 132.dp, borderWidth = 4.dp)
            }
            Text(state.peerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 34.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(statusText(state), color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (state.phase == CallPhase.Ringing || state.phase == CallPhase.Connecting) Dots()
            }
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                Text("End-to-end encrypted", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        // ---------- header ----------
        val showHeader = state.phase == CallPhase.Ringing || state.phase == CallPhase.Connecting || active
        AnimatedVisibility(showHeader && !hide, Modifier.align(Alignment.TopCenter).zIndex(10f), enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircleButton(Icons.Rounded.KeyboardArrowDown, "Minimize", dark = true, tint = Color.White) { local.minimized = true }
                Row(Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(50), dark = true).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.peerName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (active) formatClock(state.seconds) else statusText(state) + "…", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    QualityBars(state.quality)
                }
                GlassCircleButton(Icons.Rounded.Favorite, "React", dark = true, tint = Color.White) { local.reactions.add(System.nanoTime() to listOf("❤️", "😂", "👍", "🎉", "🔥", "😍").random()) }
            }
        }
        AnimatedVisibility(active && state.quality <= 1, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp).zIndex(10f), enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
            Row(Modifier.glass(RoundedCornerShape(50), dark = true).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WifiOff, null, tint = Color(0xFFFFD3CF), modifier = Modifier.size(16.dp))
                Text("Poor connection", color = Color(0xFFFFD3CF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        AnimatedVisibility(pre && state.phase != CallPhase.Incoming || (pre && state.phase == CallPhase.Incoming), Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp).zIndex(10f), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.glass(RoundedCornerShape(50), dark = true).padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF7CF2A5)))
                Text("Your camera", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ---------- bottom ----------
        Box(Modifier.align(Alignment.BottomCenter).zIndex(10f).fillMaxWidth()) {
            when (state.phase) {
                CallPhase.Incoming -> IncomingControls(local, cb, Modifier.align(Alignment.BottomCenter))
                CallPhase.Ended -> Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 60.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    LabeledCircle("Call back", Icons.Rounded.Call, Color(0xFF15803D), onClick = cb::onCallBack)
                    LabeledCircle("Close", Icons.Rounded.Close, Color(0x33FFFFFF), onClick = cb::onCloseEnded)
                }
                else -> AnimatedVisibility(!hide, Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                    ActiveControls(state, local, cb, Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 16.dp))
                }
            }
        }

        // ---------- floating reactions ----------
        local.reactions.forEach { (id, emoji) -> key(id) { FloatingEmoji(emoji, (id % 70).toInt() + 15, W) } }
    }
}

private fun statusText(s: CallUiState) = when (s.phase) {
    CallPhase.Incoming -> if (s.mode == CallMode.Video) "Incoming video call" else "Incoming voice call"
    CallPhase.Ringing -> "Calling"
    CallPhase.Connecting -> "Connecting"
    CallPhase.Active -> formatClock(s.seconds)
    CallPhase.Ended -> s.endText.ifEmpty { "Call ended" }
}

@Composable
private fun Dots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0..2) {
            val y by t.animateFloat(0f, -5f, infiniteRepeatable(keyframes { durationMillis = 1100; 0f at 0; -5f at 330; 0f at 660; 0f at 1100 }, initialStartOffset = StartOffset(i * 150)), label = "d$i")
            Box(Modifier.size(6.dp).offset(y = y.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun QualityBars(q: Int) {
    val col = if (q >= 3) Color(0xFF7CF2A5) else if (q == 2) Color(0xFFFFD166) else Color(0xFFFF8A80)
    Row(Modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..4) {
            val h by animateDpAsState((3 + i * 3).dp, label = "q")
            Box(Modifier.width(4.dp).height(h).clip(RoundedCornerShape(2.dp)).background(if (i <= q) col else Color.White.copy(alpha = 0.3f)))
        }
    }
}

@Composable
private fun CallControl(label: String, icon: ImageVector, on: Boolean, alpha: Float = 1f, onClick: () -> Unit) {
    Column(Modifier.graphicsLayer { this.alpha = alpha }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val bg by animateColorAsState(if (on) Color.White else Color.White.copy(alpha = 0.16f), label = "bg")
        Box(Modifier.size(56.dp).clip(CircleShape).background(bg).border(1.dp, Color.White.copy(alpha = if (on) 1f else 0.28f), CircleShape).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (on) Color(0xFF0C2A40) else Color.White, modifier = Modifier.size(24.dp))
        }
        Text(label, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LabeledCircle(label: String, icon: ImageVector, color: Color, size: Dp = 64.dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.42f))
        }
        Text(label, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActiveControls(state: CallUiState, local: CallLocalState, cb: CallCallbacks, modifier: Modifier) {
    val isVideo = state.mode == CallMode.Video
    val active = state.phase == CallPhase.Active
    Row(modifier.fillMaxWidth().glass(RoundedCornerShape(36.dp), dark = true).padding(horizontal = 8.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Bottom) {
        CallControl("Mute", if (state.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, state.muted, onClick = cb::onToggleMute)
        if (isVideo) CallControl("Flip", Icons.Rounded.Cameraswitch, false) { local.flipCount++; cb.onFlipCamera() }
        else CallControl("Speaker", if (state.speaker) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown, state.speaker, onClick = cb::onToggleSpeaker)
        // End (bigger, red)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(68.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFF0564F), Color(0xFFC62828)))).bounceClick(onClick = cb::onEnd), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text("End", color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        if (isVideo) CallControl("Camera", if (state.cameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, !state.cameraOn, onClick = cb::onToggleCamera)
        else CallControl("Video", Icons.Rounded.Videocam, false, onClick = cb::onSwitchToVideo)
        if (isVideo) CallControl("Split", Icons.Rounded.ViewAgenda, local.split, if (active) 1f else 0.45f) { if (active) { local.split = !local.split; local.swapped = false } }
    }
}

@Composable
private fun IncomingControls(local: CallLocalState, cb: CallCallbacks, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dragY = remember { Animatable(0f) }
    val answerPx = with(LocalDensity.current) { 120.dp.toPx() }
    Box(modifier.fillMaxWidth().height(300.dp)) {
        AnimatedVisibility(local.replies, Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp).offset(y = (-200).dp), enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), dark = true).padding(6.dp)) {
                listOf("Can't talk right now", "I'll call you back", "Call me later").forEach { t ->
                    Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(18.dp)).clickable { cb.onQuickReply(t) }, contentAlignment = Alignment.Center) {
                        Text(t, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 44.dp, start = 22.dp, end = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Bottom) {
            LabeledCircle("Message", Icons.Rounded.Chat, Color(0x33FFFFFF), 60.dp) { local.replies = !local.replies }
            LabeledCircle("Decline", Icons.Rounded.CallEnd, Color(0xFFD63B35), 72.dp, cb::onDecline)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val hint = rememberInfiniteTransition(label = "hint")
                val up by hint.animateFloat(0f, -6f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "up")
                Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(26.dp).offset(y = up.dp))
                Box(
                    Modifier.offset { IntOffset(0, dragY.value.roundToInt()) }.size(72.dp).graphicsLayer { val s = 1f + (-dragY.value / answerPx).coerceIn(0f, 0.18f); scaleX = s; scaleY = s }
                        .shadow(10.dp, CircleShape).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF34C77B), Color(0xFF15803D))))
                        .pointerInput(Unit) { detectTapGestures { cb.onAnswer() } }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { scope.launch { dragY.animateTo(0f, spring(0.5f, 300f)) } },
                                onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                            ) { change, dy ->
                                change.consume()
                                val next = (dragY.value + dy).coerceAtMost(0f)
                                scope.launch { dragY.snapTo(next) }
                                if (next < -answerPx) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); cb.onAnswer() }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Call, "Answer", tint = Color.White, modifier = Modifier.size(30.dp)) }
                Text("Swipe up to answer", color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FloatingEmoji(emoji: String, xPercent: Int, width: Dp) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(2000, easing = LinearOutSlowInEasing)) }
    Text(
        emoji, fontSize = 36.sp,
        modifier = Modifier.offset(x = width * xPercent / 100f - 18.dp).padding(top = 560.dp)
            .graphicsLayer { translationY = -a.value * 330.dp.toPx(); alpha = if (a.value < 0.15f) a.value / 0.15f else 1f - a.value; scaleX = 0.5f + 0.6f * a.value.coerceAtMost(0.3f) / 0.3f; scaleY = scaleX },
    )
}

/** Minimized audio call: slim bar under the status bar. Tap to expand. */
@Composable
fun CallMiniBar(state: CallUiState, onExpand: () -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalChatPalette.current
    val t = rememberInfiniteTransition(label = "mini")
    Row(
        modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 60.dp).fillMaxWidth().height(50.dp).shadow(10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(p.bubble1, p.bubble2))).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50)).clickable(onClick = onExpand).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0..4) {
                val h by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700 + (i % 3) * 200, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 130)), label = "m$i")
                Box(Modifier.width(4.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(Color.White))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(state.peerName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (state.phase == CallPhase.Active) formatClock(state.seconds) else "Calling…", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFD63B35)).bounceClick(onClick = onEnd), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Minimized video call: floating remote-video window you can drag to any corner. Tap to expand. */
@Composable
fun CallMiniPip(state: CallUiState, local: CallLocalState, remoteVideo: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = 132.dp
        val h = 188.dp
        var dragging by remember { mutableStateOf(false) }
        var drag by remember { mutableStateOf(Offset.Zero) }
        val (cx, cy) = cornerXY(local.miniCorner, w, h, maxWidth, maxHeight, 64.dp, 100.dp, 12.dp)
        val tx = cx + if (dragging) with(density) { drag.x.toDp() } else 0.dp
        val ty = cy + if (dragging) with(density) { drag.y.toDp() } else 0.dp
        val spec: FiniteAnimationSpec<Dp> = if (dragging) snap() else spring(0.75f, 300f)
        val x by animateDpAsState(tx, spec, label = "px")
        val y by animateDpAsState(ty, spec, label = "py")
        Box(
            Modifier.offset(x, y).size(w, h).shadow(18.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                .pointerInput(Unit) { detectTapGestures { local.minimized = false } }
                .pointerInput(local.miniCorner) {
                    detectDragGestures(
                        onDragStart = { dragging = true; drag = Offset.Zero },
                        onDrag = { c, d -> c.consume(); drag += d },
                        onDragEnd = {
                            val xPx = with(density) { (cx + w / 2).toPx() } + drag.x
                            val yPx = with(density) { (cy + h / 2).toPx() } + drag.y
                            val top = yPx < with(density) { maxHeight.toPx() } / 2
                            val left = xPx < with(density) { maxWidth.toPx() } / 2
                            local.miniCorner = if (top && left) Corner.TL else if (top) Corner.TR else if (left) Corner.BL else Corner.BR
                            dragging = false; drag = Offset.Zero
                        },
                        onDragCancel = { dragging = false; drag = Offset.Zero },
                    )
                },
        ) {
            remoteVideo(Modifier.fillMaxSize())
            Text(formatClock(state.seconds), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0x990C1220)).padding(horizontal = 8.dp, vertical = 2.dp))
            Icon(Icons.Rounded.OpenInFull, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp))
        }
    }
}
