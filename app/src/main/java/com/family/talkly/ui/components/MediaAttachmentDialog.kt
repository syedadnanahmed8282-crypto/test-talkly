package com.family.talkly.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.MessageType
import com.family.talkly.util.MediaCompressorAndUploader
import com.family.talkly.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MediaAttachmentDialog(
    onDismiss: () -> Unit,
    onSendMediaWithTag: (caption: String, type: MessageType, url: String) -> Unit,
    onSendExpiredDemo: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var previewMediaUris by remember { mutableStateOf<List<String>?>(null) }
    var previewMediaType by remember { mutableStateOf(MessageType.IMAGE) }

    var isVisible by remember { mutableStateOf(false) }
    var showGalleryPicker by remember { mutableStateOf(false) }
    var initialGalleryTab by remember { mutableStateOf(GalleryTab.ALL) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val dismissWithAnimation: () -> Unit = {
        coroutineScope.launch {
            isVisible = false
            delay(160)
            onDismiss()
        }
    }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val tempFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                previewMediaUris = listOf(Uri.fromFile(tempFile).toString())
                previewMediaType = MessageType.IMAGE
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showGalleryPicker) {
        TalklyGalleryPicker(
            initialTab = initialGalleryTab,
            onDismiss = {
                showGalleryPicker = false
            },
            onMediaSelected = { uris, type ->
                showGalleryPicker = false
                // TalklyGalleryPicker already handles selection and preview.
                // Directly route to existing media compression/upload/send pipeline.
                for (mediaUrl in uris) {
                    onSendMediaWithTag("", type, mediaUrl)
                }
                onDismiss()
            }
        )
    }

    if (previewMediaUris != null && previewMediaUris!!.isNotEmpty()) {
        MediaPreviewAndTagDialog(
            mediaUris = previewMediaUris!!,
            mediaType = previewMediaType,
            onDismiss = {
                previewMediaUris = null
            },
            onSend = { caption, type, url ->
                onSendMediaWithTag(caption, type, url)
            },
            onAllDone = {
                previewMediaUris = null
                onDismiss()
            }
        )
    } else {
        val scrimAlpha by animateFloatAsState(
            targetValue = if (isVisible) 0.52f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "attachmentScrimAlpha"
        )

        Dialog(
            onDismissRequest = dismissWithAnimation,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismissWithAnimation
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 140))
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .navigationBarsPadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} // Consume click inside the tray
                            ),
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xF20F1722),
                        border = BorderStroke(1.dp, Color(0x3322D3EE)),
                        shadowElevation = 14.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Subtle drag handle
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .background(Color(0x33A7B0BA), RoundedCornerShape(2.dp))
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Header row with compact title and close button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Add to chat",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF8FAFC)
                                )

                                IconButton(
                                    onClick = dismissWithAnimation,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0x1AFFFFFF), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color(0xFFA7B0BA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Primary Actions: Gallery + Camera
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PrimaryAttachmentTile(
                                    title = "Gallery",
                                    subtitle = "Photos & Videos",
                                    icon = Icons.Default.Image,
                                    iconTint = Color(0xFF22D3EE),
                                    iconBg = Color(0x1F22D3EE),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        initialGalleryTab = GalleryTab.ALL
                                        showGalleryPicker = true
                                    }
                                )

                                PrimaryAttachmentTile(
                                    title = "Camera",
                                    subtitle = "Take a photo",
                                    icon = Icons.Default.CameraAlt,
                                    iconTint = Color(0xFF5EEAD4),
                                    iconBg = Color(0x1F5EEAD4),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        cameraLauncher.launch(null)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Subtle Divider
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0x1422D3EE),
                                thickness = 0.8.dp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Secondary Action: Video
                            SecondaryAttachmentRow(
                                title = "Video",
                                subtitle = "Share recorded video",
                                icon = Icons.Default.Videocam,
                                iconTint = Color(0xFF0EA5A4),
                                iconBg = Color(0x1F0EA5A4),
                                onClick = {
                                    initialGalleryTab = GalleryTab.VIDEOS
                                    showGalleryPicker = true
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Privacy / 48-Hour Auto-Expiry Info Badge
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x1222D3EE), RoundedCornerShape(10.dp))
                                    .border(BorderStroke(1.dp, Color(0x2422D3EE)), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Privacy",
                                    tint = Color(0xFF5EEAD4),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "48-Hour Auto-Expiry Tag automatically applied.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFA7B0BA)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryAttachmentTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF16202C),
        border = BorderStroke(1.dp, Color(0x2E22D3EE))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconBg, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF8FAFC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFA7B0BA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SecondaryAttachmentRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFF16202C),
        border = BorderStroke(1.dp, Color(0x2422D3EE))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(iconBg, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF8FAFC)
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFA7B0BA)
                )
            }
        }
    }
}

/**
 * Modern Talkly Media Preview & Tagging Dialog supporting single or multiple media URIs
 * (Used for camera capture preview and standalone preview flows).
 */
@Composable
fun MediaPreviewAndTagDialog(
    mediaUris: List<String>,
    mediaType: MessageType,
    onDismiss: () -> Unit,
    onSend: (caption: String, type: MessageType, url: String) -> Unit,
    onAllDone: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentTimeMs = remember { System.currentTimeMillis() }
    val expirationTimeMs = remember { currentTimeMs + ChatMessage.EXPIRATION_48_HOURS_MS }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    val formattedCreation = remember { dateFormat.format(Date(currentTimeMs)) }
    val formattedExpiry = remember { dateFormat.format(Date(expirationTimeMs)) }

    var captionInput by remember { mutableStateOf("") }

    // Compression and Upload State
    var isProcessing by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("Preparing media...") }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF20F1722),
            border = BorderStroke(1.dp, Color(0x3322D3EE)),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0x1F22D3EE), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (mediaType == MessageType.VIDEO) Icons.Default.Videocam else Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isProcessing) {
                                    "Uploading..."
                                } else if (mediaUris.size > 1) {
                                    "Send ${mediaUris.size} Items"
                                } else if (mediaType == MessageType.VIDEO) {
                                    "Video Preview"
                                } else {
                                    "Photo Preview"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = if (mediaType == MessageType.VIDEO) "Video attachment" else "Camera capture",
                                fontSize = 11.sp,
                                color = Color(0xFFA7B0BA)
                            )
                        }
                    }

                    if (!isProcessing) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(0x1AFFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFA7B0BA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isProcessing) {
                    // Processing Overlay UI
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progressPercent / 100f },
                                modifier = Modifier.size(72.dp),
                                color = Color(0xFF22D3EE),
                                trackColor = Color(0x2422D3EE),
                                strokeWidth = 6.dp
                            )
                            Text(
                                text = "$progressPercent%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF22D3EE)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = progressText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFFF8FAFC),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF22D3EE),
                            trackColor = Color(0x2422D3EE)
                        )
                    }
                } else {
                    // Media Preview Display
                    if (mediaUris.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        ) {
                            items(mediaUris) { uriStr ->
                                Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF18212B))
                                        .border(BorderStroke(1.dp, Color(0x2822D3EE)), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = PhoneUtils.getCoilMediaModel(uriStr),
                                        contentDescription = "Photo Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    } else {
                        val singleUri = mediaUris.first()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF18212B))
                                .border(BorderStroke(1.dp, Color(0x2822D3EE)), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mediaType == MessageType.VIDEO) {
                                var thumbBitmap by remember(singleUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
                                LaunchedEffect(singleUri) {
                                    withContext(Dispatchers.IO) {
                                        thumbBitmap = PhoneUtils.getVideoThumbnail(context, singleUri)
                                    }
                                }
                                if (thumbBitmap != null) {
                                    Image(
                                        bitmap = thumbBitmap!!.asImageBitmap(),
                                        contentDescription = "Media Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    AsyncImage(
                                        model = PhoneUtils.getCoilMediaModel(singleUri),
                                        contentDescription = "Media Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                        .border(1.5.dp, Color(0xFF22D3EE), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color(0xFF22D3EE),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = PhoneUtils.getCoilMediaModel(singleUri),
                                    contentDescription = "Media Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Modern 48-Hour Auto-Expiry Info Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1222D3EE), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color(0x2422D3EE)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Auto-Expiry",
                                tint = Color(0xFF5EEAD4),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "48-Hour Auto-Expiry",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF5EEAD4)
                            )
                        }
                        Text(
                            text = "Expires $formattedExpiry",
                            fontSize = 11.sp,
                            color = Color(0xFFA7B0BA),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Caption Input Field
                    OutlinedTextField(
                        value = captionInput,
                        onValueChange = { captionInput = it },
                        placeholder = { Text("Add caption (optional)...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0x2822D3EE),
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC),
                            focusedContainerColor = Color(0xFF16202C),
                            unfocusedContainerColor = Color(0xFF16202C),
                            cursorColor = Color(0xFF22D3EE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row: Cancel + Send Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16202C),
                                contentColor = Color(0xFFA7B0BA)
                            ),
                            border = BorderStroke(1.dp, Color(0x2822D3EE)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                val finalCaption = captionInput.trim()
                                for ((index, mediaUrl) in mediaUris.withIndex()) {
                                    val itemCaption = if (index == 0) finalCaption else ""
                                    onSend(itemCaption, mediaType, mediaUrl)
                                }
                                onAllDone()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22D3EE),
                                contentColor = Color(0xFF080B10)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(2f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color(0xFF080B10),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (mediaUris.size > 1) "Send (${mediaUris.size})" else "Send",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF080B10)
                            )
                        }
                    }
                }
            }
        }
    }
}
