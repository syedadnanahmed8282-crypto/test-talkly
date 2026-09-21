package com.family.talkly.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.talkly.data.models.CallType
import com.family.talkly.data.zego.CallState
import com.family.talkly.data.zego.CurrentCallInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DestructiveRed = Color(0xFFF43F5E)
private val DestructiveRedDeep = Color(0xFFBE123C)
private val ElectricCyan = Color(0xFF22D3EE)
private val MintAccent = Color(0xFF5EEAD4)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFA7B0BA)
private val SurfacePill = Color(0xCC0D141C)
private val SurfacePillBorder = Color(0x3322D3EE)

/**
 * Compact, header-integrated Active Call Control.
 *
 * Displays live duration (MM:SS) and a red swipeable end-call button.
 * - Tapping anywhere except the red button restores the full-screen CallScreen.
 * - Swiping the red button LEFT progressively reveals the red end-call zone and ends the call.
 */
@Composable
fun ActiveCallHeaderControl(
    callInfo: CurrentCallInfo,
    onRestoreCall: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = callInfo.callType == CallType.VIDEO
    val minutes = callInfo.durationSeconds / 60
    val seconds = callInfo.durationSeconds % 60
    val formattedDuration = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val infiniteTransition = rememberInfiniteTransition(label = "activeCallPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    // Swipe left gesture state
    // We allow swiping up to maxTravelPx to the left across the pill
    var pillWidthPx by remember { mutableFloatStateOf(0f) }
    val dragOffsetAnimatable = remember { androidx.compose.animation.core.Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isConfirmed by remember { mutableStateOf(false) }

    // Max travel is roughly the width of the pill minus the button width
    val buttonSizeDp = 28.dp
    val buttonSizePx = with(density) { buttonSizeDp.toPx() }
    val maxTravelPx = (pillWidthPx - buttonSizePx - with(density) { 8.dp.toPx() }).coerceAtLeast(with(density) { 50.dp.toPx() })

    // Progress from 0f to 1f (0 = default at right, 1 = fully swiped left)
    val swipeProgress = (abs(dragOffsetAnimatable.value) / maxTravelPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(34.dp)
            .onGloballyPositioned { coords ->
                pillWidthPx = coords.size.width.toFloat()
            }
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        if (swipeProgress > 0.05f) DestructiveRed.copy(alpha = 0.15f + swipeProgress * 0.45f) else SurfacePill,
                        SurfacePill
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        if (swipeProgress > 0.05f) DestructiveRed.copy(alpha = 0.4f + swipeProgress * 0.5f) else SurfacePillBorder,
                        if (swipeProgress > 0.3f) DestructiveRed.copy(alpha = 0.4f + swipeProgress * 0.5f) else SurfacePillBorder
                    )
                ),
                shape = RoundedCornerShape(17.dp)
            )
            .clickable {
                if (!isDragging && swipeProgress < 0.1f) {
                    onRestoreCall()
                }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Background content: Call type icon, pulsing dot, and duration (or "Slide to end" hint)
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Live pulsing green/mint dot or call icon
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(MintAccent.copy(alpha = dotAlpha))
            )

            // Duration text or "Release" hint
            Text(
                text = when {
                    swipeProgress >= 0.70f -> "Release to end"
                    swipeProgress > 0.25f -> "Slide left"
                    callInfo.state == CallState.ACTIVE -> formattedDuration
                    else -> "Calling..."
                },
                color = if (swipeProgress > 0.25f) DestructiveRed else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                maxLines = 1
            )
        }

        // Red swipeable End Call Button (positioned on the right, slides left)
        Box(
            modifier = Modifier
                .offset { IntOffset(x = dragOffsetAnimatable.value.roundToInt(), y = 0) }
                .size(buttonSizeDp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(DestructiveRed, DestructiveRedDeep)
                    )
                )
                .shadow(elevation = 4.dp, shape = CircleShape)
                .pointerInput(isConfirmed) {
                    if (isConfirmed) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            val current = dragOffsetAnimatable.value
                            val progress = (abs(current) / maxTravelPx).coerceIn(0f, 1f)
                            if (progress >= 0.65f && !isConfirmed) {
                                isConfirmed = true
                                coroutineScope.launch {
                                    dragOffsetAnimatable.animateTo(
                                        targetValue = -maxTravelPx,
                                        animationSpec = tween(120, easing = FastOutSlowInEasing)
                                    )
                                    delay(100)
                                    onEndCall()
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffsetAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            coroutineScope.launch {
                                dragOffsetAnimatable.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (isConfirmed) return@detectHorizontalDragGestures
                            val cur = dragOffsetAnimatable.value
                            // Dragging left means dragAmount < 0
                            val next = if (dragAmount < 0) {
                                (cur + dragAmount).coerceIn(-maxTravelPx, 0f)
                            } else {
                                // Resistance when dragging right
                                (cur + dragAmount * 0.2f).coerceIn(-maxTravelPx, 8f)
                            }
                            coroutineScope.launch {
                                dragOffsetAnimatable.snapTo(next)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "Swipe left to end call",
                tint = Color.White,
                modifier = Modifier
                    .size(15.dp)
                    .scale(if (swipeProgress > 0.5f) 1.15f else 1.0f)
            )
        }
    }
}
