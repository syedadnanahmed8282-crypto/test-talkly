package com.family.talkly.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

@Composable
fun MediaMessageItem(
    message: ChatMessage,
    isSelf: Boolean = false,
    simulatedTimeOffsetMs: Long,
    onMediaClick: (String) -> Unit,
    onRetryUpload: (() -> Unit)? = null
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(min = 160.dp, max = 240.dp)
                .padding(bottom = 4.dp)
                .border(1.dp, ExpiredBorder, RoundedCornerShape(12.dp))
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
        AudioPlayerItem(message = message, isSelf = isSelf)
    } else {
        // Active Media Display
        Box(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 240.dp)
                .heightIn(min = 130.dp, max = 220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF080B10))
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
                        .widthIn(min = 160.dp, max = 240.dp)
                        .heightIn(min = 130.dp, max = 220.dp)
                )
            } else {
                AsyncImage(
                    model = com.family.talkly.util.PhoneUtils.getCoilMediaModel(message.mediaUrl),
                    contentDescription = message.textContent,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(min = 160.dp, max = 240.dp)
                        .heightIn(min = 130.dp, max = 220.dp)
                )
            }

            if (message.messageType == MessageType.VIDEO) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
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
                            color = Color.White,
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
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Expires in 48h",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
