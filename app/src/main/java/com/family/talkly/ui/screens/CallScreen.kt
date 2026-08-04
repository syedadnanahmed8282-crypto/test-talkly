package com.family.talkly.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.zego.CurrentCallInfo
import com.family.talkly.ui.theme.WhatsappGreen
import java.util.Locale
import kotlin.math.roundToInt

enum class BeautyFilterMode(val label: String) {
    FAIR_AND_BRIGHT("✨ Fair & Bright"),
    SOFT_GLOW("🌟 Soft Glow"),
    OFF("📷 Original")
}

fun getBeautyColorMatrix(mode: BeautyFilterMode): ColorMatrix {
    return when (mode) {
        BeautyFilterMode.FAIR_AND_BRIGHT -> {
            ColorMatrix(floatArrayOf(
                1.16f, 0.00f, 0.00f, 0f, 24f, // Red channel boost for soft rosy skin
                0.00f, 1.14f, 0.00f, 0f, 22f, // Green channel boost for fair skin tone
                0.00f, 0.00f, 1.12f, 0f, 20f, // Blue channel boost for bright illumination
                0.00f, 0.00f, 0.00f, 1f, 0f   // Alpha channel
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
    val matrix = remember(beautyFilterMode) { getBeautyColorMatrix(beautyFilterMode) }
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
        modifier = modifier.applyBeautyColorFilter(beautyFilterMode, matrix)
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
    val matrix = remember(beautyFilterMode) { getBeautyColorMatrix(beautyFilterMode) }
    Box(
        modifier = modifier.background(Color(0xFF101D25)),
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
            modifier = Modifier
                .fillMaxSize()
                .applyBeautyColorFilter(beautyFilterMode, matrix)
        )

        if (!isRemotePlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101D25).copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(WhatsappGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member?.name?.take(2)?.uppercase() ?: "FA",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = member?.name ?: "Partner",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = WhatsappGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Connecting video stream...",
                            color = WhatsappGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onBindLocalView: (android.view.View?) -> Unit = {},
    onBindRemoteView: (android.view.View?) -> Unit = {}
) {
    val context = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        onDispose {
            activity?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    val member = callInfo.targetMember
    val isVideo = callInfo.callType == CallType.VIDEO

    val minutes = callInfo.durationSeconds / 60
    val seconds = callInfo.durationSeconds % 60
    val formattedTimer = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    val callStatusText = when (callInfo.state) {
        com.family.talkly.data.zego.CallState.OUTGOING_CALLING -> "Calling..."
        com.family.talkly.data.zego.CallState.OUTGOING_RINGING -> "Ringing..."
        com.family.talkly.data.zego.CallState.INCOMING_RINGING -> "Incoming call..."
        com.family.talkly.data.zego.CallState.ACTIVE -> formattedTimer
        com.family.talkly.data.zego.CallState.ENDED -> "Call ended"
        else -> ""
    }

    var isSwapped by remember { mutableStateOf(false) }
    var isSplitScreen by remember { mutableStateOf(false) }
    var beautyFilterMode by remember { mutableStateOf(BeautyFilterMode.FAIR_AND_BRIGHT) }
    var pipOffsetX by remember { mutableFloatStateOf(0f) }
    var pipOffsetY by remember { mutableFloatStateOf(0f) }
    var areControlsVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B141A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
            }
    ) {
        if (isVideo && !callInfo.isCameraOff) {
            if (isSplitScreen) {
                // Split Screen Mode: Top and Bottom halves stacked
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Half: Remote View
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        RemoteVideoView(
                            member = member,
                            isRemotePlaying = callInfo.isRemoteStreamPlaying,
                            onBindRemoteView = onBindRemoteView,
                            beautyFilterMode = beautyFilterMode,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = member?.name ?: "Partner",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Bottom Half: Local View
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        CameraPreviewView(
                            onBindLocalView = onBindLocalView,
                            beautyFilterMode = beautyFilterMode,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (callInfo.isFrontCamera) "You (Front)" else "You (Back)",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Exit Split Screen Floating Button
                IconButton(
                    onClick = { isSplitScreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Split Screen",
                        tint = Color.White
                    )
                }

            } else {
                // Standard Overlay Mode with Draggable PIP Popup
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Main Background Feed
                    if (!isSwapped) {
                        RemoteVideoView(
                            member = member,
                            isRemotePlaying = callInfo.isRemoteStreamPlaying,
                            onBindRemoteView = onBindRemoteView,
                            beautyFilterMode = beautyFilterMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CameraPreviewView(
                            onBindLocalView = onBindLocalView,
                            beautyFilterMode = beautyFilterMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Overlay Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )

                    // Draggable Local/Remote Picture-in-Picture Popup
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 90.dp, end = 16.dp)
                            .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                            .size(width = 120.dp, height = 170.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, WhatsappGreen, RoundedCornerShape(16.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    pipOffsetX += dragAmount.x
                                    pipOffsetY += dragAmount.y
                                }
                            }
                            .clickable { isSwapped = !isSwapped },
                        color = Color.Black
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (!isSwapped) {
                                CameraPreviewView(
                                    onBindLocalView = onBindLocalView,
                                    beautyFilterMode = beautyFilterMode,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                RemoteVideoView(
                                    member = member,
                                    isRemotePlaying = callInfo.isRemoteStreamPlaying,
                                    onBindRemoteView = onBindRemoteView,
                                    beautyFilterMode = beautyFilterMode,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Bottom Label Tag
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (!isSwapped) {
                                        if (callInfo.isFrontCamera) "You (Front)" else "You (Rear)"
                                    } else {
                                        member?.name?.take(8) ?: "Partner"
                                    },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Top-Right Split Screen Icon Button on PIP Popup
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .clickable { isSplitScreen = true }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Splitscreen,
                                    contentDescription = "Split Screen Mode",
                                    tint = WhatsappGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Audio Call Mode View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(WhatsappGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member?.name?.take(2)?.uppercase() ?: "FA",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = member?.name ?: "Family Member",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = callStatusText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (callInfo.state == com.family.talkly.data.zego.CallState.ACTIVE) WhatsappGreen else Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Top Status Header Overlay
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = WhatsappGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "End-to-End Encrypted Family Call",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = callStatusText,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isVideo) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        onClick = {
                            beautyFilterMode = when (beautyFilterMode) {
                                BeautyFilterMode.FAIR_AND_BRIGHT -> BeautyFilterMode.SOFT_GLOW
                                BeautyFilterMode.SOFT_GLOW -> BeautyFilterMode.OFF
                                BeautyFilterMode.OFF -> BeautyFilterMode.FAIR_AND_BRIGHT
                            }
                        },
                        color = if (beautyFilterMode != BeautyFilterMode.OFF) WhatsappGreen.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Beauty Filter",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = beautyFilterMode.label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating Call Controls
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1F2C34).copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Mic
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                if (callInfo.isMuted) Color.White else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (callInfo.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = if (callInfo.isMuted) Color.Red else Color.White
                        )
                    }

                    // Camera Flip
                    IconButton(
                        onClick = onFlipCamera,
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip Camera",
                            tint = Color.White
                        )
                    }

                    // Video Toggle
                    IconButton(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                if (callInfo.isCameraOff) Color.White else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (callInfo.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Camera Toggle",
                            tint = if (callInfo.isCameraOff) Color.Red else Color.White
                        )
                    }

                    // Speaker Toggle
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                if (callInfo.isSpeakerOn) WhatsappGreen else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speaker",
                            tint = Color.White
                        )
                    }

                    // End Call FAB
                    FloatingActionButton(
                        onClick = onEndCall,
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

