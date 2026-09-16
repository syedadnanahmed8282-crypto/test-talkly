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

    var isSwapped by remember { mutableStateOf(false) }
    var isSplitScreen by remember { mutableStateOf(false) }
    var beautyFilterMode by remember { mutableStateOf(BeautyFilterMode.FAIR_AND_BRIGHT) }
    
    // Telegram-grade floating preview position & interaction physics
    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }
    var isDraggingPreview by remember { mutableStateOf(false) }
    var isPositionInitialized by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val previewDragScale by animateFloatAsState(
        targetValue = if (isDraggingPreview) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selfPreviewDragScale"
    )

    var areControlsVisible by remember { mutableStateOf(true) }

    val infiniteTransition = rememberInfiniteTransition(label = "callTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isVideo && !isInPipMode) {
                        areControlsVisible = !areControlsVisible
                    }
                }
        ) {
            if (isVideo && callInfo.state == CallState.ACTIVE) {
                // ==========================================
                // ACTIVE VIDEO CALL VIEW
                // ==========================================
                val effectiveSwapped = if (isInPipMode) false else isSwapped
                if (isSplitScreen && !isInPipMode) {
                    // Split Screen: Top (Remote) and Bottom (Local)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(1.dp, BorderElevated)
                        ) {
                            RemoteVideoView(
                                member = member,
                                isRemotePlaying = callInfo.isRemoteStreamPlaying,
                                onBindRemoteView = onBindRemoteView,
                                beautyFilterMode = beautyFilterMode,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = SurfaceCard.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BorderElevated),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .statusBarsPadding()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = member?.name ?: "Contact",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(1.dp, BorderElevated)
                        ) {
                            if (!callInfo.isCameraOff) {
                                CameraPreviewView(
                                    onBindLocalView = onBindLocalView,
                                    beautyFilterMode = beautyFilterMode,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(BackgroundDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideocamOff,
                                        contentDescription = "Camera Off",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Surface(
                                color = SurfaceCard.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BorderElevated),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .navigationBarsPadding()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = if (callInfo.isFrontCamera) "You (Front)" else "You (Back)",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Exit Split Screen Button
                    IconButton(
                        onClick = { isSplitScreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 16.dp, end = 16.dp)
                            .background(SurfaceCard, CircleShape)
                            .border(1.dp, BorderElevated, CircleShape)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Split Screen",
                            tint = TextPrimary
                        )
                    }
                } else {
                    // Full Screen Feed with Picture-in-Picture Floating Window
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val containerWidthPx = with(density) { maxWidth.toPx() }
                        val containerHeightPx = with(density) { maxHeight.toPx() }

                        // Telegram-grade floating preview sizing: ~26% screen width, 16:9 vertical aspect ratio
                        val previewWidthDp = (maxWidth * 0.26f).coerceIn(92.dp, 116.dp)
                        val previewHeightDp = previewWidthDp * (16f / 9f)
                        val previewWidthPx = with(density) { previewWidthDp.toPx() }
                        val previewHeightPx = with(density) { previewHeightDp.toPx() }

                        val marginPx = with(density) { 16.dp.toPx() }
                        val minX = marginPx
                        val maxX = (containerWidthPx - previewWidthPx - marginPx).coerceAtLeast(minX)
                        val topMarginPx = with(density) { 76.dp.toPx() }
                        val minY = topMarginPx
                        val bottomMarginPx = with(density) { 130.dp.toPx() }
                        val maxY = (containerHeightPx - previewHeightPx - bottomMarginPx).coerceAtLeast(minY)

                        LaunchedEffect(containerWidthPx, containerHeightPx) {
                            if (!isPositionInitialized && containerWidthPx > 0f) {
                                animX.snapTo(maxX)
                                animY.snapTo(minY)
                                isPositionInitialized = true
                            }
                        }

                        if (!effectiveSwapped) {
                            RemoteVideoView(
                                member = member,
                                isRemotePlaying = callInfo.isRemoteStreamPlaying,
                                onBindRemoteView = onBindRemoteView,
                                beautyFilterMode = beautyFilterMode,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            if (!callInfo.isCameraOff) {
                                CameraPreviewView(
                                    onBindLocalView = onBindLocalView,
                                    beautyFilterMode = beautyFilterMode,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(BackgroundDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideocamOff,
                                        contentDescription = "Camera Off",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }

                        if (!isInPipMode) {
                            // Top Gradient Scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                        )
                                    )
                            )

                            // Bottom Gradient Scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                            )
                        }

                        // Floating Preview Overlay (Telegram-style dragging with edge-snapping in full screen, corner overlay in native PiP)
                        val overlayModifier = if (isInPipMode) {
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(width = 44.dp, height = 66.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                                .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
                                .size(width = previewWidthDp, height = previewHeightDp)
                                .scale(previewDragScale)
                                .shadow(elevation = 10.dp, shape = RoundedCornerShape(16.dp), clip = false)
                                .clip(RoundedCornerShape(16.dp))
                                .pointerInput(minX, maxX, minY, maxY, containerWidthPx) {
                                    val touchSlop = viewConfiguration.touchSlop
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        var isDrag = false
                                        val pointerId = down.id

                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                                if (!isDrag) {
                                                    val dragDistance = (change.position - down.position).getDistance()
                                                    if (dragDistance > touchSlop) {
                                                        isDrag = true
                                                        isDraggingPreview = true
                                                        change.consume()
                                                    }
                                                } else {
                                                    val dragDelta = change.position - change.previousPosition
                                                    change.consume()
                                                    coroutineScope.launch {
                                                        val newX = (animX.value + dragDelta.x).coerceIn(minX, maxX)
                                                        val newY = (animY.value + dragDelta.y).coerceIn(minY, maxY)
                                                        animX.snapTo(newX)
                                                        animY.snapTo(newY)
                                                    }
                                                }

                                                if (change.changedToUp()) {
                                                    change.consume()
                                                    if (!isDrag) {
                                                        val splitTouchBound = with(density) { 36.dp.toPx() }
                                                        if (down.position.x > previewWidthPx - splitTouchBound && down.position.y < splitTouchBound) {
                                                            isSplitScreen = true
                                                        } else {
                                                            isSwapped = !isSwapped
                                                        }
                                                    } else {
                                                        isDraggingPreview = false
                                                        val targetX = if (animX.value + previewWidthPx / 2f < containerWidthPx / 2f) minX else maxX
                                                        val targetY = animY.value.coerceIn(minY, maxY)
                                                        coroutineScope.launch {
                                                            launch {
                                                                animX.animateTo(
                                                                    targetValue = targetX,
                                                                    animationSpec = spring(
                                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                                        stiffness = Spring.StiffnessMediumLow
                                                                    )
                                                                )
                                                            }
                                                            launch {
                                                                animY.animateTo(
                                                                    targetValue = targetY,
                                                                    animationSpec = spring(
                                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                                        stiffness = Spring.StiffnessMediumLow
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                    break
                                                }
                                            }
                                        } finally {
                                            if (isDraggingPreview) {
                                                isDraggingPreview = false
                                                val targetX = if (animX.value + previewWidthPx / 2f < containerWidthPx / 2f) minX else maxX
                                                val targetY = animY.value.coerceIn(minY, maxY)
                                                coroutineScope.launch {
                                                    launch { animX.animateTo(targetX, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) }
                                                    launch { animY.animateTo(targetY, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) }
                                                }
                                            }
                                        }
                                    }
                                }
                        }

                        Surface(
                            modifier = overlayModifier,
                            color = BackgroundDark
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (!effectiveSwapped) {
                                    if (!callInfo.isCameraOff) {
                                        CameraPreviewView(
                                            onBindLocalView = onBindLocalView,
                                            beautyFilterMode = beautyFilterMode,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(BackgroundDark),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VideocamOff,
                                                contentDescription = "Camera Off",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(if (isInPipMode) 18.dp else 28.dp)
                                            )
                                        }
                                    }
                                } else {
                                    RemoteVideoView(
                                        member = member,
                                        isRemotePlaying = callInfo.isRemoteStreamPlaying,
                                        onBindRemoteView = onBindRemoteView,
                                        beautyFilterMode = beautyFilterMode,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                if (!isInPipMode) {
                                    // Bottom PIP Tag
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(vertical = 3.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (!isSwapped) {
                                                if (callInfo.isFrontCamera) "You (Front)" else "You (Rear)"
                                            } else {
                                                member?.name?.take(8) ?: "Partner"
                                            },
                                            color = TextPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Top Right Split Screen Icon
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .background(SurfaceCard.copy(alpha = 0.8f), CircleShape)
                                            .clickable { isSplitScreen = true }
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Splitscreen,
                                            contentDescription = "Split Screen Mode",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // AUDIO / OUTGOING RINGING CALL VIEW
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = 0.14f),
                                        DeepAqua.copy(alpha = 0.06f),
                                        Color.Transparent
                                    ),
                                    center = center.copy(y = size.height * 0.40f),
                                    radius = size.width * 0.85f
                                )
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Security Status
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = onMinimizeCall,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .align(Alignment.CenterStart)
                                        .clip(CircleShape)
                                        .background(SurfaceCard)
                                        .border(1.dp, BorderElevated, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Minimize Call",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Surface(
                                    color = SurfaceCard,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, BorderElevated)
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
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (isVideo) "PAYRA VIDEO CALL" else "PAYRA VOICE CALL",
                                color = MintAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.8.sp
                            )
                        }

                        // Center Avatar & Identity
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(200.dp)
                            ) {
                                if (isOutgoing) {
                                    // Animated Outgoing Waves
                                    Box(
                                        modifier = Modifier
                                            .size(175.dp)
                                            .scale(pulseScale)
                                            .background(ElectricCyan.copy(alpha = pulseAlpha), CircleShape)
                                    )
                                }

                                // Outer Gradient Ring
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
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
                                                fontSize = 44.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = member?.name ?: "Contact",
                                color = TextPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = callStatusText,
                                color = if (callInfo.state == CallState.ACTIVE) ElectricCyan else TextSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Bottom Spacer to balance layout
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }

            // ==========================================
            // TOP STATUS HEADER OVERLAY (For Video Calls)
            // ==========================================
            if (isVideo && callInfo.state == CallState.ACTIVE) {
                AnimatedVisibility(
                    visible = areControlsVisible && !isInPipMode,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 20.dp, end = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = onMinimizeCall,
                                modifier = Modifier
                                    .size(38.dp)
                                    .align(Alignment.CenterStart)
                                    .clip(CircleShape)
                                    .background(SurfaceCard.copy(alpha = 0.85f))
                                    .border(1.dp, BorderElevated, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Minimize Call",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Surface(
                                color = SurfaceCard.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, BorderElevated)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = formattedTimer,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Beauty Filter Pill
                        Surface(
                            onClick = {
                                beautyFilterMode = when (beautyFilterMode) {
                                    BeautyFilterMode.FAIR_AND_BRIGHT -> BeautyFilterMode.SOFT_GLOW
                                    BeautyFilterMode.SOFT_GLOW -> BeautyFilterMode.OFF
                                    BeautyFilterMode.OFF -> BeautyFilterMode.FAIR_AND_BRIGHT
                                }
                            },
                            color = if (beautyFilterMode != BeautyFilterMode.OFF) ElectricCyan.copy(alpha = 0.85f) else SurfaceCard.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, BorderElevated)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Beauty Filter",
                                    tint = if (beautyFilterMode != BeautyFilterMode.OFF) Color(0xFF040E14) else TextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = beautyFilterMode.label,
                                    color = if (beautyFilterMode != BeautyFilterMode.OFF) Color(0xFF040E14) else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // FLOATING CALL CONTROLS DOCK (Both Audio & Video)
            // ==========================================
            AnimatedVisibility(
                visible = (areControlsVisible || !isVideo) && !isInPipMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = SurfaceCard.copy(alpha = 0.95f),
                    border = BorderStroke(1.2.dp, BorderElevated),
                    shadowElevation = 18.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Mute Mic
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (callInfo.isMuted) DestructiveRed.copy(alpha = 0.18f) else SurfaceElevated
                                )
                                .border(
                                    1.dp,
                                    if (callInfo.isMuted) DestructiveRed else BorderElevated,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (callInfo.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (callInfo.isMuted) DestructiveRed else TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 2. Camera Flip (If Video) or Switch to Video (If Audio)
                        IconButton(
                            onClick = {
                                if (isVideo) onFlipCamera() else onToggleCamera()
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                                .border(1.dp, BorderElevated, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isVideo) Icons.Default.Cameraswitch else Icons.Default.Videocam,
                                contentDescription = if (isVideo) "Flip Camera" else "Switch to Video",
                                tint = ElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 3. Camera Toggle On/Off (If Video)
                        if (isVideo) {
                            IconButton(
                                onClick = onToggleCamera,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (callInfo.isCameraOff) DestructiveRed.copy(alpha = 0.18f) else SurfaceElevated
                                    )
                                    .border(
                                        1.dp,
                                        if (callInfo.isCameraOff) DestructiveRed else BorderElevated,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (callInfo.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                    contentDescription = "Camera Toggle",
                                    tint = if (callInfo.isCameraOff) DestructiveRed else TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // 4. Speaker Toggle
                        IconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (callInfo.isSpeakerOn) ElectricCyan.copy(alpha = 0.18f) else SurfaceElevated
                                )
                                .border(
                                    1.dp,
                                    if (callInfo.isSpeakerOn) ElectricCyan else BorderElevated,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speaker",
                                tint = if (callInfo.isSpeakerOn) ElectricCyan else TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 5. End Call Action (Prominent Destructive Button)
                        Surface(
                            onClick = onEndCall,
                            shape = CircleShape,
                            color = DestructiveRed,
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "End Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
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

