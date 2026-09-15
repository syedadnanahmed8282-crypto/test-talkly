package com.family.talkly.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Talkly Signature Colors
private val BackgroundDark = Color(0xFF080B10)
private val SurfaceCard = Color(0xFF18212B)
private val SurfaceElevated = Color(0xFF202B36)
private val ElectricCyan = Color(0xFF22D3EE)
private val DeepAqua = Color(0xFF0EA5A4)
private val MintAccent = Color(0xFF5EEAD4)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFA7B0BA)
private val DestructiveRed = Color(0xFFF43F5E)

@Composable
fun IncomingCallDialog(
    member: FamilyMember,
    callType: CallType,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var isCallActionLocked by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "incomingPulse")
    
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale2"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha2"
    )

    val avatarBreathing by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarBreathing"
    )

    // Directional idle animations for swipe controls (calm, desynchronized hints)
    val declineIdleNudge by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                0f at 0
                -1f at 450 with FastOutSlowInEasing
                0f at 950 with FastOutSlowInEasing
                0f at 2600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "declineIdleNudge"
    )

    val acceptIdleNudge by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                0f at 0
                0f at 1300
                1f at 1750 with FastOutSlowInEasing
                0f at 2250 with FastOutSlowInEasing
                0f at 2600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "acceptIdleNudge"
    )

    val thumbBreathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thumbBreathingScale"
    )

    val thumbGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thumbGlowAlpha"
    )

    val chevronShimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevronShimmerAlpha"
    )

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundDark
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .drawBehind {
                        // Ambient radial backdrop centered on avatar
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.16f),
                                    DeepAqua.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                center = center.copy(y = size.height * 0.42f),
                                radius = size.width * 0.85f
                            )
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Security & Call Type Capsule
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24303E))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "End-to-End Encrypted",
                                    color = ElectricCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (callType == CallType.VIDEO) "INCOMING VIDEO CALL" else "INCOMING VOICE CALL",
                            color = MintAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp
                        )
                    }

                    // Center: Glowing Avatar & Contact Credentials
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(200.dp)
                        ) {
                            // First Pulse Wave
                            Box(
                                modifier = Modifier
                                    .size(170.dp)
                                    .scale(pulseScale1)
                                    .background(ElectricCyan.copy(alpha = pulseAlpha1), CircleShape)
                            )

                            // Second Pulse Wave
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .scale(pulseScale2)
                                    .background(DeepAqua.copy(alpha = pulseAlpha2), CircleShape)
                            )

                            // Avatar Frame with breathing animation
                            Box(
                                modifier = Modifier
                                    .size(136.dp)
                                    .scale(avatarBreathing)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan)
                                        )
                                    )
                                    .padding(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(SurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (member.avatarUrl?.isNotBlank() == true) {
                                        val mediaModel = remember(member.avatarUrl) {
                                            com.family.talkly.util.PhoneUtils.getCoilMediaModel(member.avatarUrl)
                                        }
                                        AsyncImage(
                                            model = mediaModel,
                                            contentDescription = member.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = member.name.take(2).uppercase(),
                                            color = ElectricCyan,
                                            fontSize = 42.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = member.name,
                            color = TextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )

                        if (member.relation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = SurfaceCard,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24303E))
                            ) {
                                Text(
                                    text = member.relation,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        } else if (member.phone.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = member.phone,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Floating Bottom Action Dock with Modern Swipe-to-Action Controls
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = SurfaceCard.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF24303E)),
                        shadowElevation = 16.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // LEFT: Decline Swipe-Left Capsule
                            SwipeCallAction(
                                modifier = Modifier.weight(1f),
                                isDecline = true,
                                callType = callType,
                                isLocked = isCallActionLocked,
                                idleNudge = declineIdleNudge,
                                thumbPulseScale = thumbBreathingScale,
                                glowAlpha = thumbGlowAlpha,
                                chevronShimmerAlpha = chevronShimmerAlpha,
                                onActionConfirmed = {
                                    if (!isCallActionLocked) {
                                        isCallActionLocked = true
                                        onDecline()
                                    }
                                }
                            )

                            // RIGHT: Accept Swipe-Right Capsule
                            SwipeCallAction(
                                modifier = Modifier.weight(1f),
                                isDecline = false,
                                callType = callType,
                                isLocked = isCallActionLocked,
                                idleNudge = acceptIdleNudge,
                                thumbPulseScale = thumbBreathingScale,
                                glowAlpha = thumbGlowAlpha,
                                chevronShimmerAlpha = chevronShimmerAlpha,
                                onActionConfirmed = {
                                    if (!isCallActionLocked) {
                                        isCallActionLocked = true
                                        onAccept()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lightweight modern swipe-to-action control for incoming call confirmation.
 * Decline: Swipes left from resting position toward the outer edge.
 * Accept: Swipes right from resting position toward the outer edge.
 */
@Composable
private fun SwipeCallAction(
    modifier: Modifier = Modifier,
    isDecline: Boolean,
    callType: CallType,
    isLocked: Boolean,
    idleNudge: Float,
    thumbPulseScale: Float,
    glowAlpha: Float,
    chevronShimmerAlpha: Float,
    onActionConfirmed: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isConfirmed by remember { mutableStateOf(false) }

    val dragOffsetAnimatable = remember { Animatable(0f) }
    val confirmationScale = remember { Animatable(1f) }
    val haloScale = remember { Animatable(1f) }
    val haloAlpha = remember { Animatable(0f) }
    val iconBounce = remember { Animatable(1f) }

    val accentColor = if (isDecline) DestructiveRed else ElectricCyan

    BoxWithConstraints(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceElevated.copy(alpha = 0.75f))
    ) {
        val density = LocalDensity.current
        val trackWidthPx = constraints.maxWidth.toFloat()
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbSizeDp = 46.dp
        val thumbSizePx = with(density) { thumbSizeDp.toPx() }
        val paddingDp = 5.dp
        val paddingPx = with(density) { paddingDp.toPx() }
        val maxTravelPx = (trackWidthPx - thumbSizePx - (paddingPx * 2)).coerceAtLeast(1f)

        val currentDrag = dragOffsetAnimatable.value
        val progress = (kotlin.math.abs(currentDrag) / maxTravelPx).coerceIn(0f, 1f)
        val isNearThreshold = progress >= 0.70f

        val triggerConfirmation: () -> Unit = {
            if (!isConfirmed && !isLocked) {
                isConfirmed = true
                isDragging = false
                val target = if (isDecline) -maxTravelPx else maxTravelPx
                coroutineScope.launch {
                    dragOffsetAnimatable.animateTo(target, tween(90, easing = FastOutSlowInEasing))
                }
                coroutineScope.launch {
                    // 1. Thumb slightly scales up
                    // 2. Track briefly brightens
                    // 3. Soft ripple/halo expands
                    // 4. Icon short confirmation motion
                    launch {
                        haloAlpha.snapTo(0.7f)
                        haloAlpha.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                    }
                    launch {
                        haloScale.snapTo(1f)
                        haloScale.animateTo(1.6f, tween(260, easing = FastOutSlowInEasing))
                    }
                    launch {
                        confirmationScale.animateTo(1.15f, tween(110, easing = FastOutSlowInEasing))
                        confirmationScale.animateTo(1.05f, tween(100, easing = FastOutSlowInEasing))
                    }
                    launch {
                        iconBounce.animateTo(1.22f, tween(110, easing = FastOutSlowInEasing))
                        iconBounce.animateTo(1.0f, tween(100, easing = FastOutSlowInEasing))
                    }
                    kotlinx.coroutines.delay(200)
                    onActionConfirmed()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isNearThreshold || isConfirmed) 1.6.dp else 1.dp,
                    brush = if (isDecline) {
                        Brush.horizontalGradient(
                            listOf(
                                DestructiveRed.copy(alpha = if (isNearThreshold) 0.85f else (0.22f + progress * 0.55f)),
                                DestructiveRed.copy(alpha = if (isNearThreshold) 0.65f else (0.12f + progress * 0.35f))
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                ElectricCyan.copy(alpha = if (isNearThreshold) 0.65f else (0.12f + progress * 0.35f)),
                                ElectricCyan.copy(alpha = if (isNearThreshold) 0.85f else (0.22f + progress * 0.55f))
                            )
                        )
                    },
                    shape = RoundedCornerShape(28.dp)
                )
                .drawBehind {
                    if (progress > 0.01f || isConfirmed) {
                        val effectiveProgress = if (isConfirmed) 1f else progress
                        if (isDecline) {
                            val fillWidth = maxTravelPx * effectiveProgress + thumbSizePx / 2
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        DestructiveRed.copy(alpha = if (isConfirmed) 0.35f else (0.08f + progress * 0.22f))
                                    ),
                                    startX = size.width - fillWidth,
                                    endX = size.width
                                ),
                                topLeft = Offset(size.width - fillWidth, 0f),
                                size = Size(fillWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                            )
                        } else {
                            val fillWidth = maxTravelPx * effectiveProgress + thumbSizePx / 2
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = if (isConfirmed) 0.35f else (0.08f + progress * 0.22f)),
                                        Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = fillWidth
                                ),
                                topLeft = Offset.Zero,
                                size = Size(fillWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                            )
                        }
                    }
                }
                .pointerInput(isLocked, isConfirmed) {
                    if (isLocked || isConfirmed) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            val current = dragOffsetAnimatable.value
                            val dragProgress = (kotlin.math.abs(current) / maxTravelPx).coerceIn(0f, 1f)
                            if (dragProgress >= 0.70f && !isConfirmed && !isLocked) {
                                triggerConfirmation()
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
                            if (isConfirmed || isLocked) return@detectHorizontalDragGestures
                            val cur = dragOffsetAnimatable.value
                            val next = if (isDecline) {
                                if (dragAmount < 0) {
                                    (cur + dragAmount).coerceIn(-maxTravelPx, 0f)
                                } else {
                                    val resistance = 0.18f
                                    (cur + dragAmount * resistance).coerceIn(-maxTravelPx, 10f)
                                }
                            } else {
                                if (dragAmount > 0) {
                                    (cur + dragAmount).coerceIn(0f, maxTravelPx)
                                } else {
                                    val resistance = 0.18f
                                    (cur + dragAmount * resistance).coerceIn(-10f, maxTravelPx)
                                }
                            }
                            coroutineScope.launch {
                                dragOffsetAnimatable.snapTo(next)
                            }
                            val curProgress = (kotlin.math.abs(next) / maxTravelPx).coerceIn(0f, 1f)
                            if (curProgress >= 0.88f && !isConfirmed && !isLocked) {
                                triggerConfirmation()
                            }
                        }
                    )
                }
        ) {
            // Directional Chevrons and Label inside Track
            if (isDecline) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 14.dp)
                        .graphicsLayer {
                            val fade = (1f - progress * 1.8f).coerceIn(0f, 1f)
                            alpha = fade * (if (isDragging) 0.95f else chevronShimmerAlpha)
                        }
                ) {
                    Text(
                        text = "‹ ‹",
                        color = DestructiveRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Decline",
                        color = DestructiveRed.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .graphicsLayer {
                            val fade = (1f - progress * 1.8f).coerceIn(0f, 1f)
                            alpha = fade * (if (isDragging) 0.95f else chevronShimmerAlpha)
                        }
                ) {
                    Text(
                        text = "Accept",
                        color = ElectricCyan.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "› ›",
                        color = ElectricCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Calculation for Thumb placement
            val idleNudgePx = if (!isDragging && !isConfirmed) {
                idleNudge * with(density) { 5.dp.toPx() }
            } else 0f

            val baseX = if (isDecline) {
                trackWidthPx - thumbSizePx - paddingPx
            } else {
                paddingPx
            }
            val thumbX = baseX + currentDrag + idleNudgePx
            val thumbY = (trackHeightPx - thumbSizePx) / 2

            // Ripple / Halo effect expanding on confirmation
            if (haloAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(thumbX.roundToInt(), thumbY.roundToInt()) }
                        .size(thumbSizeDp)
                        .scale(haloScale.value)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = haloAlpha.value))
                )
            }

            // Ambient Thumb Glow
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbX.roundToInt(), thumbY.roundToInt()) }
                    .size(thumbSizeDp)
                    .scale(thumbPulseScale * (1f + progress * 0.12f))
                    .clip(CircleShape)
                    .background(
                        accentColor.copy(
                            alpha = if (isConfirmed) 0.65f else (glowAlpha + progress * 0.35f)
                        )
                    )
            )

            // Movable Thumb
            Surface(
                modifier = Modifier
                    .offset { IntOffset(thumbX.roundToInt(), thumbY.roundToInt()) }
                    .size(thumbSizeDp)
                    .scale(confirmationScale.value * (if (isDragging || isConfirmed) 1f else thumbPulseScale)),
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = if (isNearThreshold || isConfirmed) 8.dp else 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            if (isDecline) {
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFFB7185),
                                        DestructiveRed
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        ElectricCyan,
                                        DeepAqua
                                    )
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDecline) {
                            Icons.Default.CallEnd
                        } else {
                            if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call
                        },
                        contentDescription = if (isDecline) {
                            "Swipe left to decline call"
                        } else {
                            if (callType == CallType.VIDEO) "Swipe right to accept video call" else "Swipe right to accept call"
                        },
                        tint = if (isDecline) Color.White else Color(0xFF040E14),
                        modifier = Modifier
                            .size(24.dp)
                            .scale(iconBounce.value)
                    )
                }
            }
        }
    }
}
