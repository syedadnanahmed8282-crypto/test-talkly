package com.family.talkly.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// ==========================================
// TALKLY COLOR PALETTE
// ==========================================
private val TalklyBackground = Color(0xFF080B10)
private val TalklySurface = Color(0xFF11161D)
private val TalklyCard = Color(0xFF18212B)
private val TalklyElevated = Color(0xFF202B36)
private val TalklyCyan = Color(0xFF22D3EE)
private val TalklyAqua = Color(0xFF0EA5A4)
private val TalklyMint = Color(0xFF5EEAD4)
private val TalklyTextPrimary = Color(0xFFF8FAFC)
private val TalklyTextSecondary = Color(0xFFA7B0BA)
private val TalklyTextMuted = Color(0xFF64748B)
private val TalklyError = Color(0xFFF43F5E)

@Composable
fun PostStatusDialog(
    onDismiss: () -> Unit,
    onPostStatus: (textContent: String?, photoUrl: String?, backgroundColorHex: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var statusText by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<String?>(null) }
    var isVideoMedia by remember { mutableStateOf(false) }
    var selectedColorHex by remember { mutableStateOf("#0C2B3A") }
    var isPosting by remember { mutableStateOf(false) }

    // Launcher for picking image/video from mobile gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedMediaUri = it.toString()
            isVideoMedia = false
        }
    }

    val galleryPresets = listOf(
        "Mountain Dawn" to "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800&auto=format&fit=crop&q=80",
        "Coffee Vibes" to "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800&auto=format&fit=crop&q=80",
        "Night Sky" to "https://images.unsplash.com/photo-1495616811223-4d98c6e9c869?w=800&auto=format&fit=crop&q=80",
        "Creative Space" to "https://images.unsplash.com/photo-1505751172876-fa1923c5c528?w=800&auto=format&fit=crop&q=80"
    )

    val colorOptions = listOf(
        "#0C2B3A", // Deep Cyan Dark
        "#080B10", // Talkly True Black
        "#0E3838", // Deep Emerald Aqua
        "#1B2430", // Slate Elevated
        "#2A1B38", // Dark Velvet Violet
        "#2E151B"  // Dark Crimson Rust
    )

    val submitInteractionSource = remember { MutableInteractionSource() }
    val isSubmitPressed by submitInteractionSource.collectIsPressedAsState()
    val submitScale by animateFloatAsState(
        targetValue = if (isSubmitPressed) 0.96f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "submitScale"
    )

    val canPost = (statusText.isNotBlank() || selectedMediaUri != null) && !isPosting

    fun executePost() {
        if (canPost) {
            isPosting = true
            coroutineScope.launch {
                val currentUri = selectedMediaUri
                val finalPhotoUrl = if (currentUri != null && (currentUri.startsWith("content://") || currentUri.startsWith("file://") || currentUri.startsWith("/"))) {
                    try {
                        val uri = if (currentUri.startsWith("/")) Uri.fromFile(java.io.File(currentUri)) else Uri.parse(currentUri)
                        val compressor = com.family.talkly.util.MediaCompressorAndUploader(context)
                        val compressedFile = compressor.compressImage(uri) { _, _ -> }
                        compressor.uploadMediaFile(compressedFile, "status/media/${System.currentTimeMillis()}.jpg") { _, _ -> }
                    } catch (e: Exception) {
                        currentUri
                    }
                } else {
                    currentUri
                }
                onPostStatus(
                    statusText.ifBlank { null },
                    finalPhotoUrl,
                    selectedColorHex
                )
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isPosting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = TalklyBackground,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, TalklyElevated),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ==========================================
                // 1. TOP HEADER
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TalklyElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = TalklyCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Create Story",
                                fontWeight = FontWeight.Bold,
                                color = TalklyTextPrimary,
                                fontSize = 17.sp,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                text = "Expires automatically after 24h",
                                color = TalklyTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (!isPosting) onDismiss() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(TalklyCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TalklyTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ==========================================
                // 2. LIVE STORY PREVIEW CANVAS
                // ==========================================
                val previewBg = try {
                    Color(android.graphics.Color.parseColor(selectedColorHex))
                } catch (e: Exception) {
                    TalklyCard
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(previewBg)
                        .border(1.dp, TalklyElevated, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedMediaUri != null) {
                        val mediaModel = remember(selectedMediaUri) {
                            com.family.talkly.util.PhoneUtils.getCoilMediaModel(selectedMediaUri!!)
                        }
                        AsyncImage(
                            model = mediaModel,
                            contentDescription = "Selected media preview",
                            modifier = Modifier.fillMaxWidth().height(210.dp),
                            contentScale = ContentScale.Crop
                        )

                        // Subtle dark gradient for caption contrast
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                    )
                                )
                        )

                        // Top Clear Media Button
                        IconButton(
                            onClick = {
                                selectedMediaUri = null
                                isVideoMedia = false
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove media",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (isVideoMedia) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Video",
                                    tint = TalklyCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        if (statusText.isNotBlank()) {
                            Text(
                                text = statusText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    } else {
                        // Text-only story preview
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = if (statusText.isBlank()) "Type your story caption or choose a photo below..." else statusText,
                                color = if (statusText.isBlank()) TalklyTextMuted else Color.White,
                                fontSize = if (statusText.length < 50) 18.sp else 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 3. MEDIA PICKER & COLOR CHIPS
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery Selection Button
                    Surface(
                        color = TalklyCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TalklyCyan.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { galleryLauncher.launch("image/*") }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Choose from Gallery",
                                tint = TalklyCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedMediaUri != null) "Change Photo" else "Gallery Photo",
                                color = TalklyCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Color palette chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(colorOptions) { hex ->
                            val chipColor = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                TalklyCard
                            }
                            val isSelected = selectedColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(chipColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) TalklyCyan else TalklyElevated,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Presets Carousel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sample Backdrops",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TalklyTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(galleryPresets) { (label, url) ->
                        val isSelected = selectedMediaUri == url
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) TalklyElevated else TalklyCard)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) TalklyCyan else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedMediaUri = url
                                    isVideoMedia = label.contains("video", ignoreCase = true)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TalklyCyan else TalklyTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 4. FLOATING CAPTION EDITOR
                // ==========================================
                Surface(
                    color = TalklyCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TalklyElevated),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = statusText,
                            onValueChange = { if (it.length <= 200) statusText = it },
                            placeholder = {
                                Text(
                                    text = "Add a caption or thoughts...",
                                    color = TalklyTextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = TalklyCyan,
                                focusedTextColor = TalklyTextPrimary,
                                unfocusedTextColor = TalklyTextPrimary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${statusText.length}/200",
                                color = if (statusText.length >= 190) TalklyError else TalklyTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ==========================================
                // 5. SHARE STORY BUTTON
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .scale(submitScale)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (canPost) {
                                Brush.horizontalGradient(listOf(TalklyCyan, TalklyAqua))
                            } else {
                                SolidColor(TalklyElevated)
                            }
                        )
                        .clickable(
                            enabled = canPost,
                            interactionSource = submitInteractionSource,
                            indication = null
                        ) {
                            executePost()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPosting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF040E14),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Posting Story...",
                                color = Color(0xFF040E14),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Share",
                                tint = if (canPost) Color(0xFF040E14) else TalklyTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Share story",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (canPost) Color(0xFF040E14) else TalklyTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
