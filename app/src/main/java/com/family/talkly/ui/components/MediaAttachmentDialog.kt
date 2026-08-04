package com.family.talkly.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.MessageType
import com.family.talkly.ui.theme.WhatsappGreen
import com.family.talkly.ui.theme.WhatsappTeal
import com.family.talkly.util.MediaCompressorAndUploader
import com.family.talkly.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
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
    var previewMediaUris by remember { mutableStateOf<List<String>?>(null) }
    var previewMediaType by remember { mutableStateOf(MessageType.IMAGE) }

    // System Media Picker Launchers for Multiple Media
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            previewMediaUris = uris.map { it.toString() }
            previewMediaType = MessageType.IMAGE
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            previewMediaUris = uris.map { it.toString() }
            previewMediaType = MessageType.VIDEO
        }
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
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "মিডিয়া যুক্ত করুন",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111B21)
                            )
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Exactly 2 Options: Photo and Video
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Photo Button
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clickable {
                                    photoPickerLauncher.launch("image/*")
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(Color(0xFF8E24AA), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Photo",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4A148C)
                                )
                                Text(
                                    text = "গ্যালারি থেকে ছবি",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Video Button
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clickable {
                                    videoPickerLauncher.launch("video/*")
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(Color(0xFFE53935), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Video",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB71C1C)
                                )
                                Text(
                                    text = "গ্যালারি থেকে ভিডিও",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Privacy",
                                tint = WhatsappTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "48-Hour Auto-Expiry Tag automatically applied.",
                                fontSize = 11.sp,
                                color = WhatsappTeal,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Media Preview & Tagging Dialog supporting single or multiple media URIs.
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

    var captionInput by remember {
        mutableStateOf(
            if (mediaType == MessageType.IMAGE) {
                if (mediaUris.size > 1) "📷 ${mediaUris.size} Family Photos" else "📷 Family Photo"
            } else {
                "🎥 Family Video"
            }
        )
    }

    // Compression and Upload State
    var isProcessing by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("Preparing media...") }

    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isProcessing) "Compressing & Uploading..." else if (mediaUris.size > 1) "Send ${mediaUris.size} Items" else "Media Preview",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111B21)
                        )
                    )
                    if (!isProcessing) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isProcessing) {
                    // Processing Overlay UI
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progressPercent / 100f },
                                modifier = Modifier.size(72.dp),
                                color = WhatsappGreen,
                                trackColor = WhatsappGreen.copy(alpha = 0.2f),
                                strokeWidth = 6.dp
                            )
                            Text(
                                text = "$progressPercent%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = WhatsappTeal
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = progressText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF111B21),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = WhatsappGreen,
                            trackColor = Color.LightGray.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    // Media Preview Display
                    if (mediaUris.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            items(mediaUris) { uriStr ->
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = PhoneUtils.getCoilMediaModel(uriStr),
                                        contentDescription = "Photo Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(160.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        val singleUri = mediaUris.first()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.08f)),
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
                                        modifier = Modifier.fillMaxWidth().height(180.dp)
                                    )
                                } else {
                                    AsyncImage(
                                        model = PhoneUtils.getCoilMediaModel(singleUri),
                                        contentDescription = "Media Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(180.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = PhoneUtils.getCoilMediaModel(singleUri),
                                    contentDescription = "Media Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(180.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Expiry Info Badge
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F2F5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.LightGray.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Metadata",
                                    tint = WhatsappTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "48-HOUR RETENTION METADATA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsappTeal
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Auto-Expires At:",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formattedExpiry,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Caption Input Field
                    OutlinedTextField(
                        value = captionInput,
                        onValueChange = { captionInput = it },
                        label = { Text("Add caption (optional)", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WhatsappGreen,
                            unfocusedBorderColor = Color.LightGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Send Button
                    Button(
                        onClick = {
                            for ((index, mediaUrl) in mediaUris.withIndex()) {
                                val itemCaption = if (index == 0) captionInput else ""
                                onSend(itemCaption, mediaType, mediaUrl)
                            }
                            onAllDone()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsappGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (mediaUris.size > 1) "Send ${mediaUris.size} Items" else "Send Media",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
