package com.talkly.designui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkly.designui.components.GlassCircleButton
import com.talkly.designui.components.Waveform
import com.talkly.designui.components.bounceClick
import com.talkly.designui.components.glass
import com.talkly.designui.theme.Ink
import com.talkly.designui.theme.LocalChatPalette
import com.talkly.designui.util.formatClock
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** All composer state in one place so the host screen can read/clear it. */
class ComposerState {
    var text by mutableStateOf("")
    var recording by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var seconds by mutableIntStateOf(0)
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)

    fun startHold() { recording = true; locked = false; paused = false; seconds = 0; dx = 0f; dy = 0f }
    fun lock() { locked = true; dx = 0f; dy = 0f }
    fun cancel() { recording = false; locked = false; paused = false; dx = 0f; dy = 0f; seconds = 0 }
    fun finish(): Int { val s = seconds; cancel(); return s }
}

@Composable
fun rememberComposerState() = remember { ComposerState() }

/**
 * Glass composer: typing (auto-grow, icons hide while typing), emoji toggle, attach, and the voice button with
 * hold-to-record, slide-left-to-cancel (230dp) and slide-up-to-lock (190dp). A quick tap = hands-free (locked) mode.
 */
@Composable
fun Composer(
    state: ComposerState,
    emojiOpen: Boolean,
    onToggleEmoji: () -> Unit,
    onAttach: () -> Unit,
    onSendText: (String) -> Unit,
    onVoiceSend: (seconds: Int) -> Unit,
    onVoiceCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalChatPalette.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val typing = state.text.isNotEmpty()
    val cancelPx = with(density) { 230.dp.toPx() }
    val lockPx = with(density) { 190.dp.toPx() }
    val holding = state.recording && !state.locked

    LaunchedEffect(state.recording, state.paused) {
        while (state.recording && !state.paused) { delay(1000); state.seconds++ }
    }

    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.locked -> {
                GlassCircleButton(Icons.Rounded.Delete, "Delete recording", size = 52.dp, tint = Ink.Danger) { state.cancel(); onVoiceCancel() }
                Row(
                    Modifier.weight(1f).height(52.dp).glass(RoundedCornerShape(50)).padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RecDot(!state.paused)
                    Text(formatClock(state.seconds), color = Ink.Dark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val t = rememberInfiniteTransition(label = "wave")
                    val prog by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "p")
                    Waveform(7, if (state.paused) 0.4f else prog, p.bubble1, Ink.Deep.copy(alpha = 0.28f), Modifier.weight(1f).height(28.dp))
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)).bounceClick { state.paused = !state.paused },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, tint = Ink.Deep) }
                }
            }
            holding -> {
                val cancelP = (abs(state.dx) / cancelPx).coerceIn(0f, 1f)
                Row(
                    Modifier.weight(1f).height(52.dp).glass(RoundedCornerShape(50)).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecDot(true)
                    Text(formatClock(state.seconds), color = Ink.Dark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val t = rememberInfiniteTransition(label = "nudge")
                    val nudge by t.animateFloat(0f, -7f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "n")
                    Row(
                        Modifier.weight(1f).graphicsLayer { translationX = state.dx * 0.3f; alpha = 1f - 0.6f * cancelP },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, null, tint = Ink.Mid, modifier = Modifier.offset(x = nudge.dp))
                        Text(if (cancelP > 0.7f) "Release to cancel" else "Slide to cancel", color = if (cancelP > 0.7f) Ink.Danger else Ink.Mid, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            else -> {
                Row(
                    Modifier.weight(1f).heightIn(min = 52.dp).glass(RoundedCornerShape(26.dp)).animateContentSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedVisibility(!typing || emojiOpen, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        Icon(
                            if (emojiOpen) Icons.Rounded.Keyboard else Icons.Rounded.EmojiEmotions, "Emoji", tint = Ink.Deep,
                            modifier = Modifier.size(26.dp).bounceClick(onClick = onToggleEmoji),
                        )
                    }
                    BasicTextField(
                        value = state.text, onValueChange = { state.text = it }, maxLines = 5,
                        textStyle = TextStyle(color = Ink.Dark, fontSize = 16.sp, lineHeight = 24.sp),
                        cursorBrush = SolidColor(p.bubble1),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (state.text.isEmpty()) Text("Message", color = Ink.Mid, fontSize = 16.sp)
                                inner()
                            }
                        },
                    )
                    AnimatedVisibility(!typing, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        Icon(Icons.Rounded.AttachFile, "Attach", tint = Ink.Deep, modifier = Modifier.size(24.dp).bounceClick(onClick = onAttach))
                    }
                }
            }
        }

        // ---- right button ----
        Box(contentAlignment = Alignment.Center) {
            when {
                state.locked -> SendButton("Send voice") { onVoiceSend(state.finish()) }
                typing && !state.recording -> SendButton("Send") { onSendText(state.text.trim()); state.text = "" }
                else -> MicButton(state, holding, cancelPx, lockPx, haptic, onVoiceSend, onVoiceCancel)
            }
            // lock pill floats above the mic while holding
            if (holding) {
                val lockP = (abs(state.dy) / lockPx).coerceIn(0f, 1f)
                Box(
                    Modifier.offset(y = (-96).dp).graphicsLayer { alpha = 0.75f + 0.25f * lockP; scaleX = 1f + 0.12f * lockP; scaleY = scaleX; translationY = -lockP * 12.dp.toPx() }
                        .size(46.dp, 104.dp).glass(RoundedCornerShape(23.dp), strong = true),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(if (lockP > 0.75f) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, tint = Ink.Deep, modifier = Modifier.size(22.dp))
                        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Ink.Deep, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecDot(blink: Boolean) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(10.dp).graphicsLayer { alpha = if (blink) a else 1f }.clip(CircleShape).background(Color(0xFFC62828)))
}

@Composable
private fun SendButton(desc: String, onClick: () -> Unit) {
    val p = LocalChatPalette.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else 0.4f, spring(0.5f, 380f), label = "pop")
    Box(
        Modifier.graphicsLayer { scaleX = s; scaleY = s }.size(52.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color(0x660369A1), spotColor = Color(0x660369A1)).clip(CircleShape)
            .background(Brush.linearGradient(listOf(p.bubble2, p.bubble1))).border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Send, desc, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun MicButton(
    state: ComposerState, holding: Boolean, cancelPx: Float, lockPx: Float,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onVoiceSend: (Int) -> Unit, onVoiceCancel: () -> Unit,
) {
    val p = LocalChatPalette.current
    val willCancel = holding && abs(state.dx) / cancelPx > 0.7f
    val t = rememberInfiniteTransition(label = "ripple")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "ring")
    val scale by animateFloatAsState(if (holding) 1.3f else 1f, spring(0.55f, 320f), label = "scale")
    Box(
        Modifier
            .offset { IntOffset((state.dx * 0.6f).roundToInt(), (state.dy * 0.6f).roundToInt()) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(52.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val t0 = System.currentTimeMillis()
                    state.startHold()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    var lockedNow = false
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!lockedNow) {
                            val d = change.position - down.position
                            state.dx = min(0f, d.x); state.dy = min(0f, d.y)
                            if (-d.y > lockPx) { lockedNow = true; state.lock(); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                            else if (-d.x > cancelPx) { cancelled = true; state.cancel(); onVoiceCancel(); haptic.performHapticFeedback(HapticFeedbackType.LongPress); break }
                        }
                        change.consume()
                    }
                    if (!cancelled && !lockedNow) {
                        if (System.currentTimeMillis() - t0 < 350) state.lock() else onVoiceSend(state.finish())
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (holding) {
            Box(Modifier.size(52.dp).graphicsLayer { scaleX = 1f + ring * 0.9f; scaleY = scaleX; alpha = 0.5f * (1f - ring) }.clip(CircleShape).background(p.blobA))
        }
        Box(
            Modifier.fillMaxSize().shadow(8.dp, CircleShape, ambientColor = Color(0x660369A1), spotColor = Color(0x660369A1)).clip(CircleShape)
                .background(if (willCancel) Brush.linearGradient(listOf(Color(0xFFE5484D), Color(0xFFB3261E))) else Brush.linearGradient(listOf(p.bubble2, p.bubble1)))
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(if (willCancel) Icons.Rounded.Delete else Icons.Rounded.Mic, "Hold to record", tint = Color.White, modifier = Modifier.size(24.dp)) }
    }
}
