package com.family.talkly.ui.screens

import android.app.Activity
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.zego.CallState
import com.family.talkly.data.zego.CurrentCallInfo
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ==========================================
// TALKLY CALL SIGNATURE COLORS
// ==========================================
private val BackgroundDark = Color(0xFF080B10)
private val SurfaceMain = Color(0xFF11161D)
private val SurfaceCard = Color(0xFF18212B)
private val SurfaceElevated = Color(0xFF202B36)
private val ElectricCyan = Color(0xFF22D3EE)
private val DeepAqua = Color(0xFF0EA5A4)
private val MintAccent = Color(0xFF5EEAD4)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFA7B0BA)
private val DestructiveRed = Color(0xFFF43F5E)
private val BorderElevated = Color(0xFF24303E)

enum class BeautyFilterMode(val label: String) {
    FAIR_AND_BRIGHT("✨ Fair & Bright"),
    SOFT_GLOW("🌟 Soft Glow"),
    OFF("📷 Original")
}

fun getBeautyColorMatrix(mode: BeautyFilterMode): ColorMatrix {
    return when (mode) {
        BeautyFilterMode.FAIR_AND_BRIGHT -> {
            ColorMatrix(floatArrayOf(
                1.16f, 0.00f, 0.00f, 0f, 24f,
                0.00f, 1.14f, 0.00f, 0f, 22f,
                0.00f, 0.00f, 1.12f, 0f, 20f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            ))
        }
        BeautyFilterMode.SOFT_GLOW -> {
            ColorMatrix(floatArrayOf(
                1.20f, 0.00f, 0.00f, 0f, 32f,
                0.00f, 1.16f, 0.00f, 0f, 28f,
                0.00f, 0.00f, 1.14f, 0f, 24f,
                0.00f, 0.00f, 0.00f, 1f, 0f
            ))
        }
        BeautyFilterMode.OFF -> ColorMatrix()
    }
}

fun Modifier.applyBeautyColorFilter(beautyFilterMode: BeautyFilterMode, matrix: ColorMatrix): Modifier = this.drawWithContent {
    if (beautyFilterMode != BeautyFilterMode.OFF && !size.isEmpty()) {
        try {
            val paint = Paint().apply {
                colorFilter = ColorFilter.colorMatrix(matrix)
            }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(size.toRect(), paint)
                drawContent()
                canvas.restore()
            }
        } catch (e: Throwable) {
            drawContent()
        }
    } else {
        drawContent()
    }
}

@Composable
fun CameraPreviewView(
    onBindLocalView: (android.view.View?) -> Unit,
    beautyFilterMode: BeautyFilterMode = BeautyFilterMode.FAIR_AND_BRIGHT,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        onBindLocalView(this@apply)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        onBindLocalView(null)
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun RemoteVideoView(
    member: FamilyMember?,
    isRemotePlaying: Boolean,
    onBindRemoteView: (android.view.View?) -> Unit,
    beautyFilterMode: BeautyFilterMode = BeautyFilterMode.FAIR_AND_BRIGHT,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            onBindRemoteView(this@apply)
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            onBindRemoteView(null)
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isRemotePlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundDark.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan)
                                )
                            )
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (member?.avatarUrl?.isNotBlank() == true) {
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
                                    text = member?.name?.take(2)?.uppercase() ?: "TK",
                                    color = ElectricCyan,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = member?.name ?: "Contact",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderElevated)
                    ) {
                        Text(
                            text = "Connecting secure video stream...",
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallScreen(
    callInfo: CurrentCallInfo,
    isInPipMode: Boolean = false,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onBindLocalView: (android.view.View?) -> Unit = {},
    onBindRemoteView: (android.view.View?) -> Unit = {},
    onMinimizeCall: () -> Unit = {}
) {
    val TAG = "CallScreen"
    val isVideo = callInfo.callType == CallType.VIDEO
    android.util.Log.e(TAG, "CallScreen recomposing: isVideo=$isVideo, state=${callInfo.state}, isCameraOff=${callInfo.isCameraOff}")

    // Telegram-level Back gesture: minimizes the active call back into Talkly instead of dropping or exiting
    BackHandler(enabled = !isInPipMode && callInfo.state == CallState.ACTIVE) {
        onMinimizeCall()
    }

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        onDispose {
            activity?.window?.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    val member = callInfo.targetMember
    val isOutgoing = callInfo.state == CallState.OUTGOING_CALLING || callInfo.state == CallState.OUTGOING_RINGING

    val minutes = callInfo.durationSeconds / 60
    val seconds = callInfo.durationSeconds % 60
    val formattedTimer = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    val callStatusText = when (callInfo.state) {
        CallState.OUTGOING_CALLING -> "Connecting..."
        CallState.OUTGOING_RINGING -> "Ringing..."
        CallState.INCOMING_RINGING -> "Incoming call..."
        CallState.ACTIVE -> formattedTimer
        CallState.ENDED -> "Call ended"
        else -> ""
    }

    val beautyFilterMode = BeautyFilterMode.FAIR_AND_BRIGHT

    val callUiState = remember(callInfo, member) {
        com.talkly.designui.call.CallUiState(
            peerName = member?.name?.ifBlank { null } ?: "Contact",
            mode = if (isVideo) com.talkly.designui.call.CallMode.Video else com.talkly.designui.call.CallMode.Voice,
            phase = when (callInfo.state) {
                CallState.INCOMING_RINGING -> com.talkly.designui.call.CallPhase.Incoming
                CallState.OUTGOING_CALLING -> com.talkly.designui.call.CallPhase.Connecting
                CallState.OUTGOING_RINGING -> com.talkly.designui.call.CallPhase.Ringing
                CallState.ACTIVE -> com.talkly.designui.call.CallPhase.Active
                CallState.ENDED -> com.talkly.designui.call.CallPhase.Ended
                else -> com.talkly.designui.call.CallPhase.Ended
            },
            seconds = callInfo.durationSeconds,
            muted = callInfo.isMuted,
            speaker = callInfo.isSpeakerOn,
            cameraOn = !callInfo.isCameraOff,
            quality = 4,
            endText = if (isVideo) "Video call ended · $formattedTimer" else "Voice call ended · $formattedTimer"
        )
    }

    val callLocalState = com.talkly.designui.call.rememberCallLocalState()

    LaunchedEffect(callLocalState.minimized) {
        if (callLocalState.minimized) {
            onMinimizeCall()
        }
    }

    val callCallbacks = remember(callInfo, onEndCall, onToggleMute, onToggleSpeaker, onToggleCamera, onFlipCamera) {
        object : com.talkly.designui.call.CallCallbacks() {
            override fun onDecline() { onEndCall() }
            override fun onEnd() { onEndCall() }
            override fun onToggleMute() { onToggleMute() }
            override fun onToggleSpeaker() { onToggleSpeaker() }
            override fun onToggleCamera() { onToggleCamera() }
            override fun onFlipCamera() { onFlipCamera() }
            override fun onCloseEnded() { onEndCall() }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        com.talkly.designui.call.CallScreen(
            state = callUiState,
            local = callLocalState,
            callbacks = callCallbacks,
            modifier = Modifier.fillMaxSize(),
            remoteVideo = { mod ->
                RemoteVideoView(
                    member = member,
                    isRemotePlaying = callInfo.isRemoteStreamPlaying,
                    onBindRemoteView = onBindRemoteView,
                    beautyFilterMode = beautyFilterMode,
                    modifier = mod.fillMaxSize()
                )
            },
            localVideo = { mod ->
                CameraPreviewView(
                    onBindLocalView = onBindLocalView,
                    beautyFilterMode = beautyFilterMode,
                    modifier = mod.fillMaxSize()
                )
            }
        )
    }
}


/**
 * Telegram-grade in-app minimized call overlay pill.
 * Floats elegantly below the status bar while a call is active in the background,
 * displaying real-time call indicator, participant name, and duration,
 * with single-tap restoration back to the full CallScreen and an inline end-call button.
 */
@Composable
fun TalklyMinimizedCallPill(
    callInfo: CurrentCallInfo,
    onRestoreCall: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = callInfo.callType == CallType.VIDEO
    val minutes = callInfo.durationSeconds / 60
    val seconds = callInfo.durationSeconds % 60
    val formattedTimer = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    val member = callInfo.targetMember
    val memberName = member?.name?.ifBlank { null } ?: "Talkly Call"

    val infiniteTransition = rememberInfiniteTransition(label = "minimizedPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "minimizedPulseAlpha"
    )

    Surface(
        onClick = onRestoreCall,
        modifier = modifier
            .padding(top = 10.dp, start = 16.dp, end = 16.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(26.dp), clip = false),
        shape = RoundedCornerShape(26.dp),
        color = SurfaceCard.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, BorderElevated)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Animated Status Dot / Call Type Icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isVideo) ElectricCyan.copy(alpha = 0.18f) else MintAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = if (isVideo) "Active Video Call" else "Active Audio Call",
                    tint = if (isVideo) ElectricCyan else MintAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Member Info and Duration
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = memberName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MintAccent.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (callInfo.state == CallState.ACTIVE) formattedTimer else "Connecting...",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Quick End Call Button
            IconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DestructiveRed)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

