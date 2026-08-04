package com.family.talkly.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.data.models.UserProfile
import com.family.talkly.ui.theme.WhatsappGreen
import com.family.talkly.ui.theme.WhatsappTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDetailsDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSaveProfile: (name: String, bio: String, photoUrl: String, coverPhotoUrl: String) -> Unit,
    onLogout: (() -> Unit)? = null
) {
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

    // Launcher for picking avatar image
    val avatarGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAvatarUriForCropping = it
        }
    }

    // Launcher for picking cover photo image
    val coverGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedCoverUriForCropping = it
        }
    }

    // Interactive Image Crop Dialog for Avatar
    selectedAvatarUriForCropping?.let { rawUri ->
        ImageCropDialog(
            imageUri = rawUri,
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
                color = Color.Black
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
                                .background(WhatsappTeal)
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = editedName.take(2).uppercase().ifBlank { "ME" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 72.sp
                            )
                        }
                    }

                    // Action buttons bar on top
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Profile Photo",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        IconButton(
                            onClick = { showFullAvatarViewer = false },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    // Change Photo button on bottom
                    Button(
                        onClick = {
                            showFullAvatarViewer = false
                            avatarGalleryLauncher.launch("image/*")
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsappGreen),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Profile Photo", color = Color.White, fontWeight = FontWeight.Bold)
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
                color = Color.Black
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
                                .background(Brush.horizontalGradient(listOf(WhatsappTeal, WhatsappGreen)))
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No Cover Photo Set",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cover Photo",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        IconButton(
                            onClick = { showFullCoverViewer = false },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            showFullCoverViewer = false
                            coverGalleryLauncher.launch("image/*")
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsappGreen),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Cover Photo", color = Color.White, fontWeight = FontWeight.Bold)
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
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = WhatsappTeal
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                // COVER PHOTO BANNER & OVERLAPPING AVATAR CONTAINER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Cover Photo Background Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .background(Brush.horizontalGradient(listOf(WhatsappTeal, WhatsappGreen)))
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
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap to set Cover Photo",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Cover Photo Camera Edit Badge (Top Right)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clickable { coverGalleryLauncher.launch("image/*") },
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Edit Cover",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (currentCoverPhotoUrl.isNotBlank()) "Edit Cover" else "Add Cover",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Overlapping Profile Avatar (positioned at bottom center of the cover banner)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Profile Avatar Circle
                        Box(
                            modifier = Modifier
                                .size(106.dp)
                                .clip(CircleShape)
                                .border(3.5.dp, Color.White, CircleShape)
                                .background(WhatsappTeal)
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
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                )
                            }
                        }

                        // Camera badge button for Avatar (bottom end of Avatar)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(WhatsappGreen, CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .align(Alignment.BottomEnd)
                                .clickable { avatarGalleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change Profile Photo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Tap photo to view • Tap ",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = WhatsappGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = " to edit",
                        fontSize = 11.sp,
                        color = WhatsappGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Name Card Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FA)),
                        shape = RoundedCornerShape(16.dp)
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
                                        tint = WhatsappTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Name",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
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
                                        tint = WhatsappGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (isEditingName) {
                                OutlinedTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WhatsappGreen,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    singleLine = true
                                )
                            } else {
                                Text(
                                    text = editedName.ifBlank { "No Name Set" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111B21),
                                    modifier = Modifier.padding(top = 4.dp, start = 28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bio / About Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FA)),
                        shape = RoundedCornerShape(16.dp)
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
                                        tint = WhatsappTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Bio / Status",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
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
                                        tint = WhatsappGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (isEditingBio) {
                                OutlinedTextField(
                                    value = editedBio,
                                    onValueChange = { editedBio = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WhatsappGreen,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    maxLines = 2
                                )
                            } else {
                                Text(
                                    text = editedBio.ifBlank { "Available on Talkly 💬" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF111B21),
                                    modifier = Modifier.padding(top = 4.dp, start = 28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Verified Phone Number Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FA)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = WhatsappTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Verified Mobile Number",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userProfile.phoneNumber.ifBlank { "+1 555-0100" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111B21)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons: Save Changes & Logout
                    Button(
                        onClick = {
                            onSaveProfile(editedName, editedBio, currentPhotoUrl, currentCoverPhotoUrl)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsappGreen),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Profile Changes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
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
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log Out Session", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
