package com.family.talkly.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.data.models.UserProfile

// ==========================================
// TALKLY SIGNATURE DESIGN TOKENS
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
private val TextMuted = Color(0xFF64748B)
private val ErrorColor = Color(0xFFF43F5E)
private val SuccessColor = Color(0xFF10B981)
private val BorderSubtle = Color(0xFF1E293B)
private val BorderElevated = Color(0xFF24303E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDetailsDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSaveProfile: (name: String, bio: String, photoUrl: String, coverPhotoUrl: String) -> Unit,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var editedName by remember { mutableStateOf(userProfile.name) }
    var editedBio by remember { mutableStateOf(userProfile.bio) }
    var currentPhotoUrl by remember { mutableStateOf(userProfile.profilePicUrl) }
    var currentCoverPhotoUrl by remember { mutableStateOf(userProfile.coverPhotoUrl) }

    var isEditingName by remember { mutableStateOf(false) }
    var isEditingBio by remember { mutableStateOf(false) }

    var showFullAvatarViewer by remember { mutableStateOf(false) }
    var showFullCoverViewer by remember { mutableStateOf(false) }

    var selectedAvatarUriForCropping by remember { mutableStateOf<Uri?>(null) }
    var selectedCoverUriForCropping by remember { mutableStateOf<Uri?>(null) }

    val avatarGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedAvatarUriForCropping = it }
    }

    val coverGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedCoverUriForCropping = it }
    }

    // Interactive Image Crop Dialog for Avatar
    selectedAvatarUriForCropping?.let { rawUri ->
        ImageCropDialog(
            imageUri = rawUri,
            isCoverCrop = false,
            onDismiss = { selectedAvatarUriForCropping = null },
            onImageCropped = { croppedUri ->
                currentPhotoUrl = croppedUri.toString()
                selectedAvatarUriForCropping = null
            }
        )
    }

    // Interactive Image Crop Dialog for Cover Photo
    selectedCoverUriForCropping?.let { rawUri ->
        ImageCropDialog(
            imageUri = rawUri,
            isCoverCrop = true,
            onDismiss = { selectedCoverUriForCropping = null },
            onImageCropped = { croppedUri ->
                currentCoverPhotoUrl = croppedUri.toString()
                selectedCoverUriForCropping = null
            }
        )
    }

    // Full Screen Profile Picture Viewer
    if (showFullAvatarViewer) {
        Dialog(
            onDismissRequest = { showFullAvatarViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = BackgroundDark
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (currentPhotoUrl.isNotBlank()) {
                        val mediaModel = remember(currentPhotoUrl) {
                            com.family.talkly.util.PhoneUtils.getCoilMediaModel(currentPhotoUrl)
                        }
                        AsyncImage(
                            model = mediaModel,
                            contentDescription = "My Profile Picture",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                                .border(2.dp, ElectricCyan, CircleShape)
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = editedName.take(2).uppercase().ifBlank { "ME" },
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 72.sp
                            )
                        }
                    }

                    // Top Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showFullAvatarViewer = false },
                            shape = CircleShape,
                            color = SurfaceElevated.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, BorderElevated),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            text = "Profile Photo",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.size(44.dp))
                    }

                    // Change Photo Button at Bottom
                    Surface(
                        onClick = {
                            showFullAvatarViewer = false
                            avatarGalleryLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = ElectricCyan,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF040E14),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Change Profile Photo",
                                color = Color(0xFF040E14),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Full Screen Cover Photo Viewer
    if (showFullCoverViewer) {
        Dialog(
            onDismissRequest = { showFullCoverViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = BackgroundDark
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (currentCoverPhotoUrl.isNotBlank()) {
                        val mediaModel = remember(currentCoverPhotoUrl) {
                            com.family.talkly.util.PhoneUtils.getCoilMediaModel(currentCoverPhotoUrl)
                        }
                        AsyncImage(
                            model = mediaModel,
                            contentDescription = "Cover Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Brush.horizontalGradient(listOf(DeepAqua, ElectricCyan)))
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No Cover Photo Set",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showFullCoverViewer = false },
                            shape = CircleShape,
                            color = SurfaceElevated.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, BorderElevated),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            text = "Cover Photo",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.size(44.dp))
                    }

                    Surface(
                        onClick = {
                            showFullCoverViewer = false
                            coverGalleryLauncher.launch("image/*")
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = ElectricCyan,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color(0xFF040E14),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Change Cover Photo",
                                color = Color(0xFF040E14),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .shadow(32.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            color = SurfaceMain,
            border = BorderStroke(1.2.dp, BorderElevated)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = SurfaceElevated,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // COVER PHOTO BANNER & OVERLAPPING AVATAR CONTAINER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Cover Photo Background Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(DeepAqua.copy(alpha = 0.35f), SurfaceElevated)
                                )
                            )
                            .clickable {
                                if (currentCoverPhotoUrl.isNotBlank()) {
                                    showFullCoverViewer = true
                                } else {
                                    coverGalleryLauncher.launch("image/*")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentCoverPhotoUrl.isNotBlank()) {
                            val mediaModel = remember(currentCoverPhotoUrl) {
                                com.family.talkly.util.PhoneUtils.getCoilMediaModel(currentCoverPhotoUrl)
                            }
                            AsyncImage(
                                model = mediaModel,
                                contentDescription = "Cover Photo Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Bottom Fade Overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, SurfaceMain)
                                        )
                                    )
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(SurfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Set Cover Photo",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Cover Photo Camera Edit Badge
                        Surface(
                            onClick = { coverGalleryLauncher.launch("image/*") },
                            color = SurfaceMain.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderElevated),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Edit Cover",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (currentCoverPhotoUrl.isNotBlank()) "Edit Cover" else "Add Cover",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Overlapping Profile Avatar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(114.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(114.dp)
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
                                    .background(SurfaceMain)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(SurfaceElevated)
                                        .clickable {
                                            if (currentPhotoUrl.isNotBlank()) {
                                                showFullAvatarViewer = true
                                            } else {
                                                avatarGalleryLauncher.launch("image/*")
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentPhotoUrl.isNotBlank()) {
                                        val mediaModel = remember(currentPhotoUrl) {
                                            com.family.talkly.util.PhoneUtils.getCoilMediaModel(currentPhotoUrl)
                                        }
                                        AsyncImage(
                                            model = mediaModel,
                                            contentDescription = "My Profile Picture",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = editedName.take(2).uppercase().ifBlank { "ME" },
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 32.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Camera Badge Button for Avatar
                        Surface(
                            onClick = { avatarGalleryLauncher.launch("image/*") },
                            shape = CircleShape,
                            color = ElectricCyan,
                            border = BorderStroke(2.dp, SurfaceMain),
                            modifier = Modifier
                                .size(34.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change Profile Photo",
                                    tint = Color(0xFF040E14),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Editable Fields Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Name Section
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Display Name",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = { isEditingName = !isEditingName },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isEditingName) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = "Edit Name",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (isEditingName) {
                                OutlinedTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricCyan,
                                        unfocusedBorderColor = BorderElevated,
                                        focusedContainerColor = SurfaceElevated,
                                        unfocusedContainerColor = SurfaceElevated
                                    ),
                                    singleLine = true
                                )
                            } else {
                                Text(
                                    text = editedName.ifBlank { "No Name Set" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 4.dp, start = 26.dp)
                                )
                            }
                        }
                    }

                    // Bio / Status Section
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MintAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "About / Status",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = { isEditingBio = !isEditingBio },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isEditingBio) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = "Edit Bio",
                                        tint = MintAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (isEditingBio) {
                                OutlinedTextField(
                                    value = editedBio,
                                    onValueChange = { editedBio = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MintAccent,
                                        unfocusedBorderColor = BorderElevated,
                                        focusedContainerColor = SurfaceElevated,
                                        unfocusedContainerColor = SurfaceElevated
                                    ),
                                    maxLines = 2
                                )
                            } else {
                                Text(
                                    text = editedBio.ifBlank { "Available on Talkly 💬" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, start = 26.dp)
                                )
                            }
                        }
                    }

                    // Verified Mobile Number Section
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Phone", userProfile.phoneNumber)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Phone number copied", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DeepAqua.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = DeepAqua,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Verified Mobile Number",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userProfile.phoneNumber.ifBlank { "+1 555-0100" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Save Changes Button
                Surface(
                    onClick = {
                        onSaveProfile(editedName, editedBio, currentPhotoUrl, currentCoverPhotoUrl)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = ElectricCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(50.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF040E14),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Profile Changes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF040E14)
                        )
                    }
                }

                if (onLogout != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            onDismiss()
                            onLogout()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Log Out",
                            tint = ErrorColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Out Session", color = ErrorColor, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
