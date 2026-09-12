package com.family.talkly.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.data.models.MessageType
import com.family.talkly.data.models.StoryTextMetadata
import com.family.talkly.data.models.StoryTextStyle
import com.family.talkly.util.MediaCompressorAndUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

// Talkly Core Blue Theme Colors
private val TalklyBlueDark = Color(0xFF080B10)
private val TalklyBlueCard = Color(0xFF11161D)
private val TalklyBlueElevated = Color(0xFF18212B)
private val TalklyBlueBorder = Color(0xFF202B36)
private val TalklyBlueTextPrimary = Color(0xFFF8FAFC)
private val TalklyBlueTextSecondary = Color(0xFFA7B0BA)
private val TalklyBlueTextMuted = Color(0xFF64748B)

/**
 * Full-screen Instagram/Facebook-style Story Editor built with Talkly's signature Blue theme.
 * Integrates TalklyGalleryPicker for local photos/videos, supports draggable/scalable/rotatable text,
 * smart local contrast analysis, and preserves existing media compression/upload pipelines.
 */
@Composable
fun PostStatusDialog(
    onDismiss: () -> Unit,
    onPostStatus: (textContent: String?, photoUrl: String?, backgroundColorHex: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Story Media State
    var selectedMediaUri by remember { mutableStateOf<String?>(null) }
    var isVideoMedia by remember { mutableStateOf(false) }
    var selectedBgColorHex by remember { mutableStateOf("#0C2B3A") }
    var isPosting by remember { mutableStateOf(false) }

    // Talkly Gallery Picker Visibility
    var showGalleryPicker by remember { mutableStateOf(false) }

    // Text Overlay State
    var storyText by remember { mutableStateOf("") }
    var normX by remember { mutableFloatStateOf(0.5f) }
    var normY by remember { mutableFloatStateOf(0.5f) }
    var textScale by remember { mutableFloatStateOf(1.0f) }
    var textRotation by remember { mutableFloatStateOf(0.0f) }
    var selectedStyle by remember { mutableStateOf(StoryTextStyle.CLASSIC) }
    var selectedTextColorHex by remember { mutableStateOf("#FFFFFF") }
    var isTextSelected by remember { mutableStateOf(false) }
    var isEditingTextContent by remember { mutableStateOf(false) }

    // Smart Color Recommendation
    var suggestedTextColorHex by remember { mutableStateOf("#FFFFFF") }

    // Recompute smart contrast when position, background, or media changes
    LaunchedEffect(selectedMediaUri, selectedBgColorHex, normX, normY) {
        withContext(Dispatchers.IO) {
            val rec = StoryContrastAnalyzer.getRecommendedColor(
                context = context,
                mediaUri = selectedMediaUri,
                backgroundColorHex = selectedBgColorHex,
                normX = normX,
                normY = normY
            )
            withContext(Dispatchers.Main) {
                suggestedTextColorHex = rec
            }
        }
    }

    // Video preview playback control
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    var isVideoPlaying by remember { mutableStateOf(true) }

    val canPost = (storyText.isNotBlank() || selectedMediaUri != null) && !isPosting

    fun executePost() {
        if (!canPost) return
        isPosting = true
        coroutineScope.launch {
            val currentUri = selectedMediaUri
            var finalPhotoUrl: String? = currentUri

            if (currentUri != null && (currentUri.startsWith("content://") || currentUri.startsWith("file://") || currentUri.startsWith("/"))) {
                try {
                    val uri = if (currentUri.startsWith("/")) Uri.fromFile(File(currentUri)) else Uri.parse(currentUri)
                    val compressor = MediaCompressorAndUploader(context)

                    if (isVideoMedia) {
                        // For video: use compressed video or prepare video file for upload
                        val compressedVideo = try {
                            compressor.compressVideo(uri) { _, _ -> }
                        } catch (_: Exception) {
                            // If compression fails or skipped, copy local stream
                            val tempFile = File(context.cacheDir, "story_upload_${System.currentTimeMillis()}.mp4")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                            }
                            tempFile
                        }
                        if (compressedVideo.exists() && compressedVideo.length() > 0) {
                            finalPhotoUrl = compressor.uploadMediaFile(compressedVideo, "status/media/${System.currentTimeMillis()}.mp4") { _, _ -> }
                        }
                    } else {
                        // For image: compress with target quality and upload
                        val compressedFile = compressor.compressImage(uri) { _, _ -> }
                        finalPhotoUrl = compressor.uploadMediaFile(compressedFile, "status/media/${System.currentTimeMillis()}.jpg") { _, _ -> }
                    }
                } catch (e: Exception) {
                    finalPhotoUrl = currentUri
                }
            }

            // Serialize text overlay with backward-compatible JSON metadata
            val serializedContent = if (storyText.isNotBlank()) {
                StoryTextMetadata(
                    text = storyText.trim(),
                    x = normX,
                    y = normY,
                    style = selectedStyle,
                    colorHex = selectedTextColorHex,
                    scale = textScale,
                    rotation = textRotation,
                    isLegacy = false
                ).toSerializedString()
            } else null

            onPostStatus(serializedContent, finalPhotoUrl, selectedBgColorHex)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isPosting && !showGalleryPicker && !isEditingTextContent) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(enabled = true) {
            when {
                showGalleryPicker -> showGalleryPicker = false
                isEditingTextContent -> isEditingTextContent = false
                isTextSelected -> isTextSelected = false
                !isPosting -> onDismiss()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TalklyBlueDark)
        ) {
            // ==========================================================
            // 1. STORY CANVAS (Full Screen 9:16 Aspect Ratio Layout)
            // ==========================================================
            val canvasBg = try {
                Color(android.graphics.Color.parseColor(selectedBgColorHex))
            } catch (_: Exception) {
                StoryTalklyDeepCyan
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvasBg)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (isTextSelected) {
                                    isTextSelected = false
                                } else if (isVideoMedia && videoViewInstance != null) {
                                    // Tap to toggle video playback
                                    if (videoViewInstance?.isPlaying == true) {
                                        videoViewInstance?.pause()
                                        isVideoPlaying = false
                                    } else {
                                        videoViewInstance?.start()
                                        isVideoPlaying = true
                                    }
                                }
                            }
                        )
                    }
            ) {
                val canvasWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val canvasHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                // Background Media (Image or Video)
                if (selectedMediaUri != null) {
                    if (isVideoMedia) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    setVideoURI(Uri.parse(selectedMediaUri))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                        start()
                                        isVideoPlaying = true
                                    }
                                    videoViewInstance = this
                                }
                            },
                            update = { view ->
                                videoViewInstance = view
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        DisposableEffect(selectedMediaUri) {
                            onDispose {
                                videoViewInstance?.stopPlayback()
                                videoViewInstance = null
                            }
                        }
                    } else {
                        AsyncImage(
                            model = selectedMediaUri,
                            contentDescription = "Story Media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Top and Bottom Soft Vignette for UI contrast
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                    )
                }

                // Video Paused Indicator
                if (isVideoMedia && !isVideoPlaying) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Paused",
                            tint = StoryTalklyCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Draggable / Pinch-to-Scale / Rotatable Text Overlay
                if (storyText.isNotBlank()) {
                    val metadata = StoryTextMetadata(
                        text = storyText,
                        x = normX,
                        y = normY,
                        style = selectedStyle,
                        colorHex = selectedTextColorHex,
                        scale = textScale,
                        rotation = textRotation,
                        isLegacy = false
                    )

                    val posX = (canvasWidthPx * normX)
                    val posY = (canvasHeightPx * normY)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (posX - 160).roundToInt().coerceIn(0, (canvasWidthPx - 320).toInt().coerceAtLeast(0)),
                                    y = (posY - 40).roundToInt().coerceIn(0, (canvasHeightPx - 80).toInt().coerceAtLeast(0))
                                )
                            }
                            .graphicsLayer {
                                scaleX = textScale
                                scaleY = textScale
                                rotationZ = textRotation
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures(panZoomLock = false) { _, pan, zoom, rotationDelta ->
                                    isTextSelected = true
                                    normX = (normX + pan.x / canvasWidthPx).coerceIn(0.08f, 0.92f)
                                    normY = (normY + pan.y / canvasHeightPx).coerceIn(0.08f, 0.92f)
                                    textScale = (textScale * zoom).coerceIn(0.6f, 3.2f)
                                    textRotation = (textRotation + rotationDelta) % 360f
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (isTextSelected) {
                                            isEditingTextContent = true
                                        } else {
                                            isTextSelected = true
                                        }
                                    }
                                )
                            }
                    ) {
                        StoryTextRender(
                            metadata = metadata,
                            isSelected = isTextSelected
                        )
                    }
                } else if (selectedMediaUri == null) {
                    // Empty text-only state placeholder
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, StoryTalklyCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .clickable { isEditingTextContent = true }
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Tap here to add text...",
                            color = StoryTalklyMint,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ==========================================================
            // 2. TOP ACTION BAR (Overlaid above canvas)
            // ==========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                IconButton(
                    onClick = { if (!isPosting) onDismiss() },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, TalklyBlueBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Middle Quick Action Tools
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add / Edit Text Button
                    IconButton(
                        onClick = { isEditingTextContent = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (storyText.isNotBlank()) StoryTalklyDeepCyan else Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, if (storyText.isNotBlank()) StoryTalklyCyan else TalklyBlueBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Text Tool",
                            tint = if (storyText.isNotBlank()) StoryTalklyCyan else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Choose Media / Gallery Button (Talkly Gallery Picker)
                    IconButton(
                        onClick = { showGalleryPicker = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (selectedMediaUri != null) StoryTalklyDeepCyan else Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, if (selectedMediaUri != null) StoryTalklyCyan else TalklyBlueBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Talkly Gallery",
                            tint = if (selectedMediaUri != null) StoryTalklyCyan else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Cycle Background Color (When text-only)
                    if (selectedMediaUri == null) {
                        IconButton(
                            onClick = {
                                val currentIndex = TALKLY_STORY_BG_COLORS.indexOf(selectedBgColorHex)
                                val nextIndex = (currentIndex + 1) % TALKLY_STORY_BG_COLORS.size
                                selectedBgColorHex = TALKLY_STORY_BG_COLORS[nextIndex]
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .border(1.dp, TalklyBlueBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatColorFill,
                                contentDescription = "Background Color",
                                tint = StoryTalklyCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Delete / Clear Media Button (If media attached)
                    if (selectedMediaUri != null) {
                        IconButton(
                            onClick = {
                                selectedMediaUri = null
                                isVideoMedia = false
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .border(1.dp, TalklyBlueBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Media",
                                tint = Color(0xFFF43F5E),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Share / Post Button (Talkly Cyan Gradient Pill)
                Surface(
                    onClick = { executePost() },
                    enabled = canPost,
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (canPost) StoryTalklyCyan else TalklyBlueBorder
                    ),
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (canPost) {
                                Brush.horizontalGradient(listOf(StoryTalklyCyan, StoryTalklyAqua))
                            } else {
                                Brush.horizontalGradient(listOf(TalklyBlueElevated, TalklyBlueCard))
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = TalklyBlueDark,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Posting...",
                                color = TalklyBlueDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = "Share",
                                color = if (canPost) TalklyBlueDark else TalklyBlueTextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Share",
                                tint = if (canPost) TalklyBlueDark else TalklyBlueTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================================
            // 3. CONTEXTUAL TEXT EDITING TOOLBAR (When text is selected)
            // ==========================================================
            AnimatedVisibility(
                visible = isTextSelected && !isEditingTextContent,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = TalklyBlueCard.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = BorderStroke(1.dp, StoryTalklyCyan.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header row with Done button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Customize Text Style",
                                color = TalklyBlueTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Done Editing Button
                            Surface(
                                onClick = { isTextSelected = false },
                                shape = RoundedCornerShape(12.dp),
                                color = StoryTalklyDeepCyan,
                                border = BorderStroke(1.dp, StoryTalklyCyan)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = StoryTalklyCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Done",
                                        color = StoryTalklyCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Horizontal Style Presets Carousel
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(StoryTextStyle.entries) { style ->
                                val isSelected = selectedStyle == style
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) StoryTalklyDeepCyan else TalklyBlueElevated)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) StoryTalklyCyan else TalklyBlueBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedStyle = style }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = style.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) StoryTalklyCyan else TalklyBlueTextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Smart Color Picker with "Suggested" badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Color & Contrast",
                                color = TalklyBlueTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (suggestedTextColorHex == selectedTextColorHex) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = StoryTalklyCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, StoryTalklyCyan)
                                ) {
                                    Text(
                                        text = "Optimal Contrast",
                                        color = StoryTalklyCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(TALKLY_STORY_TEXT_COLORS) { hex ->
                                val colorInt = try {
                                    Color(android.graphics.Color.parseColor(hex))
                                } catch (_: Exception) {
                                    Color.White
                                }
                                val isSelected = selectedTextColorHex.equals(hex, ignoreCase = true)
                                val isSuggested = suggestedTextColorHex.equals(hex, ignoreCase = true)

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(colorInt)
                                        .border(
                                            width = if (isSelected) 2.5.dp else if (isSuggested) 1.5.dp else 1.dp,
                                            color = if (isSelected) StoryTalklyCyan else if (isSuggested) StoryTalklyMint else TalklyBlueBorder,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedTextColorHex = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (hex == "#FFFFFF" || hex == "#BAE6FD" || hex == "#22D3EE" || hex == "#5EEAD4") Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================================
            // 4. FULL-SCREEN DIRECT TEXT INPUT MODAL
            // ==========================================================
            AnimatedVisibility(
                visible = isEditingTextContent,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Top Bar for Text Editor
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isEditingTextContent = false },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TalklyBlueCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Text Input",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "${storyText.length}/200",
                            color = if (storyText.length >= 190) Color(0xFFF43F5E) else StoryTalklyCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Done button
                        Surface(
                            onClick = {
                                isEditingTextContent = false
                                if (storyText.isNotBlank()) {
                                    isTextSelected = true
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = StoryTalklyCyan
                        ) {
                            Text(
                                text = "Done",
                                color = TalklyBlueDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Centered Large Text Field
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = storyText,
                            onValueChange = { if (it.length <= 200) storyText = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 34.sp
                            ),
                            cursorBrush = SolidColor(StoryTalklyCyan),
                            decorationBox = { innerTextField ->
                                if (storyText.isBlank()) {
                                    Text(
                                        text = "Type your story thoughts here...",
                                        color = TalklyBlueTextMuted,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            // ==========================================================
            // 5. TALKLY GALLERY PICKER INTEGRATION
            // ==========================================================
            if (showGalleryPicker) {
                TalklyGalleryPicker(
                    initialTab = GalleryTab.ALL,
                    onDismiss = { showGalleryPicker = false },
                    onMediaSelected = { uris, type ->
                        showGalleryPicker = false
                        val firstUri = uris.firstOrNull()
                        if (firstUri != null) {
                            selectedMediaUri = firstUri
                            isVideoMedia = (type == MessageType.VIDEO) ||
                                    firstUri.endsWith(".mp4", ignoreCase = true) ||
                                    firstUri.endsWith(".mov", ignoreCase = true) ||
                                    firstUri.contains("video", ignoreCase = true)

                            // Position text comfortably for media stories
                            if (normY == 0.5f) {
                                normY = 0.75f
                            }
                        }
                    }
                )
            }
        }
    }
}
