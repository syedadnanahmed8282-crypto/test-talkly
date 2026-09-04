package com.family.talkly.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.MessageType
import com.family.talkly.ui.theme.ExpiredBgLight
import com.family.talkly.ui.theme.ExpiredBorder
import com.family.talkly.ui.theme.ExpiredTextDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TalklyCyan = Color(0xFF22D3EE)
private val TalklyCard = Color(0xFF18212B)
private val TalklyElevated = Color(0xFF222F3E)

@Composable
fun MediaMessageItem(
    message: ChatMessage,
    isSelf: Boolean = false,
    simulatedTimeOffsetMs: Long,
    onMediaClick: (String) -> Unit,
    onRetryUpload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isExpired = message.isMediaExpired(simulatedTimeOffsetMs)

    var videoThumbnail by remember(message.mediaUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(message.mediaUrl, message.messageType) {
        if (message.messageType == MessageType.VIDEO && !message.mediaUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                videoThumbnail = com.family.talkly.util.PhoneUtils.getVideoThumbnail(context, message.mediaUrl)
            }
        }
    }

    if (isExpired) {
        // Render 48-Hour Expired Media Placeholder Card
        Card(
            colors = CardDefaults.cardColors(containerColor = ExpiredBgLight),
            shape = RoundedCornerShape(14.dp),
            modifier = modifier
                .widthIn(min = 160.dp, max = 260.dp)
                .border(1.dp, ExpiredBorder, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFFFFC107).copy(alpha = 0.2f), RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassDisabled,
                        contentDescription = "Expired",
                        tint = ExpiredTextDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Media expired after 48h",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ExpiredTextDark,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "The ${message.messageType.name.lowercase()} file was automatically removed.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ExpiredTextDark.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    } else if (message.messageType == MessageType.VOICE_NOTE) {
        AudioPlayerItem(message = message, isSelf = isSelf, modifier = modifier)
    } else {
        // Active Media Display - Compact, adaptive, thin 1dp border, no giant outer bubble
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TalklyCard,
            border = BorderStroke(1.dp, if (isSelf) TalklyCyan.copy(alpha = 0.35f) else TalklyElevated),
            shadowElevation = 2.dp,
            modifier = modifier
                .widthIn(min = 160.dp, max = 280.dp)
                .heightIn(min = 140.dp, max = 310.dp)
        ) {
            Box(
                modifier = Modifier
                    .clickable {
                        message.mediaUrl?.let { onMediaClick(it) }
                    }
            ) {
                if (message.messageType == MessageType.VIDEO && videoThumbnail != null) {
                    Image(
                        bitmap = videoThumbnail!!.asImageBitmap(),
                        contentDescription = message.textContent,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(min = 160.dp, max = 280.dp)
                            .heightIn(min = 140.dp, max = 310.dp)
                    )
                } else {
                    AsyncImage(
                        model = com.family.talkly.util.PhoneUtils.getCoilMediaModel(message.mediaUrl),
                        contentDescription = message.textContent,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(min = 160.dp, max = 280.dp)
                            .heightIn(min = 140.dp, max = 310.dp)
                    )
                }

                if (message.messageType == MessageType.VIDEO) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Uploading / Pending Overlay
                val isUploadingOrPending = message.isUploading || (message.isPending && !message.isFailed)
                if (isUploadingOrPending) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                progress = { (message.uploadProgress.coerceIn(0, 100) / 100f) },
                                color = TalklyCyan,
                                trackColor = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (message.uploadProgress > 0) "Uploading ${message.uploadProgress}%" else "Compressing video...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (message.isFailed) {
                    // Failed Upload Overlay
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Upload Failed",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Upload Failed",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.Button(
                                onClick = { onRetryUpload?.invoke() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5252)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text("Retry Upload", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Time badge overlay
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.formattedTime,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (isSelf) {
                            Spacer(modifier = Modifier.width(3.dp))
                            when {
                                message.isRead -> Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = TalklyCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                message.isDelivered -> Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                                else -> Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Sent",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
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
fun MediaGroupCluster(
    messages: List<ChatMessage>,
    isSelf: Boolean = false,
    simulatedTimeOffsetMs: Long = 0L,
    onMediaClick: (ChatMessage) -> Unit,
    onRetryUpload: ((ChatMessage) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) return

    val latestMsg = messages.last()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TalklyCard,
        border = BorderStroke(1.dp, if (isSelf) TalklyCyan.copy(alpha = 0.35f) else TalklyElevated),
        shadowElevation = 2.dp,
        modifier = modifier.width(268.dp)
    ) {
        Box {
            when (messages.size) {
                2 -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onMediaClick(messages[0]) }
                        ) {
                            MediaTile(messages[0], simulatedTimeOffsetMs)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onMediaClick(messages[1]) }
                        ) {
                            MediaTile(messages[1], simulatedTimeOffsetMs)
                        }
                    }
                }
                3 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clickable { onMediaClick(messages[0]) }
                        ) {
                            MediaTile(messages[0], simulatedTimeOffsetMs)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onMediaClick(messages[1]) }
                            ) {
                                MediaTile(messages[1], simulatedTimeOffsetMs)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onMediaClick(messages[2]) }
                            ) {
                                MediaTile(messages[2], simulatedTimeOffsetMs)
                            }
                        }
                    }
                }
                else -> {
                    // 4 or more items collage
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onMediaClick(messages[0]) }
                            ) {
                                MediaTile(messages[0], simulatedTimeOffsetMs)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onMediaClick(messages[1]) }
                            ) {
                                MediaTile(messages[1], simulatedTimeOffsetMs)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onMediaClick(messages[2]) }
                            ) {
                                MediaTile(messages[2], simulatedTimeOffsetMs)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onMediaClick(messages[3]) }
                            ) {
                                MediaTile(messages[3], simulatedTimeOffsetMs)
                                if (messages.size > 4) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+${messages.size - 3}",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Timestamp & Delivery status badge on cluster
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = latestMsg.formattedTime,
                        fontSize = 9.5.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    if (isSelf) {
                        Spacer(modifier = Modifier.width(3.dp))
                        when {
                            latestMsg.isRead -> Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Read",
                                tint = TalklyCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            latestMsg.isDelivered -> Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Delivered",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Sent",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    message: ChatMessage,
    simulatedTimeOffsetMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videoThumb by remember(message.mediaUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(message.mediaUrl, message.messageType) {
        if (message.messageType == MessageType.VIDEO && !message.mediaUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                videoThumb = com.family.talkly.util.PhoneUtils.getVideoThumbnail(context, message.mediaUrl)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF080B10))) {
        if (message.messageType == MessageType.VIDEO && videoThumb != null) {
            Image(
                bitmap = videoThumb!!.asImageBitmap(),
                contentDescription = message.textContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = com.family.talkly.util.PhoneUtils.getCoilMediaModel(message.mediaUrl),
                contentDescription = message.textContent,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (message.messageType == MessageType.VIDEO) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Video",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Uploading indicator
        if (message.isUploading || (message.isPending && !message.isFailed)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { (message.uploadProgress.coerceIn(0, 100) / 100f) },
                    color = TalklyCyan,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
