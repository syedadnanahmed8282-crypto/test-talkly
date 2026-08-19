package com.family.talkly.ui.screens.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ==========================================
// TALKLY PREMIUM COLOR PALETTE
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
private val SuccessColor = Color(0xFF22C55E)

data class PresetAvatar(
    val id: String,
    val name: String,
    val color: Color,
    val url: String
)

val PRESET_AVATARS = listOf(
    PresetAvatar("1", "Classic Teal", DeepAqua, "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop"),
    PresetAvatar("2", "Bright Green", Color(0xFF059669), "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&auto=format&fit=crop"),
    PresetAvatar("3", "Warm Coral", Color(0xFFE11D48), "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300&auto=format&fit=crop"),
    PresetAvatar("4", "Deep Blue", Color(0xFF2563EB), "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=300&auto=format&fit=crop"),
    PresetAvatar("5", "Royal Violet", Color(0xFF7C3AED), "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=300&auto=format&fit=crop")
)

@Composable
fun ProfileSetupScreen(
    phoneNumber: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSaveProfile: (String, String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var customAvatarUrl by remember { mutableStateOf<String?>(null) }
    var selectedAvatar by remember { mutableStateOf(PRESET_AVATARS[0]) }
    var nameValidationError by remember { mutableStateOf<String?>(null) }

    var selectedImageForCropping by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageForCropping = uri
        }
    }

    selectedImageForCropping?.let { rawUri ->
        com.family.talkly.ui.components.ImageCropDialog(
            imageUri = rawUri,
            onDismiss = { selectedImageForCropping = null },
            onImageCropped = { croppedUri ->
                customAvatarUrl = croppedUri.toString()
                selectedImageForCropping = null
            }
        )
    }

    val activeAvatarUrl = customAvatarUrl ?: selectedAvatar.url

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    fun submitProfile() {
        val trimmedName = nameInput.trim()
        if (trimmedName.isBlank()) {
            nameValidationError = "Please enter your display name"
            return
        }
        if (trimmedName.length < 2) {
            nameValidationError = "Name must be at least 2 characters"
            return
        }
        nameValidationError = null
        keyboardController?.hide()
        focusManager.clearFocus()
        onSaveProfile(trimmedName, activeAvatarUrl)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .drawBehind {
                    // Ambient radial background glow behind avatar
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ElectricCyan.copy(alpha = 0.08f),
                                DeepAqua.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = center.copy(y = size.height * 0.22f),
                            radius = size.width * 0.75f
                        )
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ==========================================
                // 1. TOP BRANDING & HEADER
                // ==========================================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Talkly Icon Badge
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(16.dp, CircleShape, spotColor = ElectricCyan.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricCyan, DeepAqua)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Talkly Logo",
                            tint = Color(0xFF040E14),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Complete your profile",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Set up your profile so your contacts can recognize you.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // ==========================================
                    // 2. PROFILE PHOTO AVATAR
                    // ==========================================
                    val cameraInteractionSource = remember { MutableInteractionSource() }
                    val isCameraPressed by cameraInteractionSource.collectIsPressedAsState()
                    val cameraScale by animateFloatAsState(
                        targetValue = if (isCameraPressed) 0.88f else 1f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "cameraScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(122.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer Cyan/Aqua Gradient Ring
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            ElectricCyan,
                                            MintAccent,
                                            DeepAqua,
                                            ElectricCyan
                                        )
                                    )
                                )
                                .padding(3.dp)
                        ) {
                            // Inner Dark Canvas
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (activeAvatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = activeAvatarUrl,
                                        contentDescription = "Profile Picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Default Avatar Placeholder",
                                        tint = TextMuted,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                            }
                        }

                        // Floating Camera / Edit Action Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .align(Alignment.BottomEnd)
                                .scale(cameraScale)
                                .shadow(8.dp, CircleShape, spotColor = ElectricCyan.copy(alpha = 0.5f))
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(ElectricCyan, DeepAqua)
                                    )
                                )
                                .border(2.5.dp, BackgroundDark, CircleShape)
                                .clickable(
                                    interactionSource = cameraInteractionSource,
                                    indication = null
                                ) { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change profile photo from gallery",
                                tint = Color(0xFF040E14),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Choose a preset avatar or tap above for gallery:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        letterSpacing = 0.2.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset Avatars Selector Carousel
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(PRESET_AVATARS) { avatar ->
                            val isSelected = avatar.id == selectedAvatar.id && customAvatarUrl == null
                            val borderGradient = if (isSelected) {
                                Brush.linearGradient(listOf(ElectricCyan, MintAccent))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFF24303E), Color(0xFF1E2834)))
                            }

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        brush = borderGradient,
                                        shape = CircleShape
                                    )
                                    .background(SurfaceCard)
                                    .clickable {
                                        customAvatarUrl = null
                                        selectedAvatar = avatar
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = avatar.url,
                                    contentDescription = avatar.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected avatar",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // ==========================================
                    // 3. DISPLAY NAME INPUT
                    // ==========================================
                    ProfileInputField(
                        label = "Display name",
                        placeholder = "Enter your name",
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            if (nameValidationError != null) nameValidationError = null
                        },
                        leadingIcon = Icons.Default.Person,
                        imeAction = ImeAction.Done,
                        onDone = { submitProfile() },
                        isError = nameValidationError != null,
                        testTag = "profile_name_input"
                    )

                    // Inline Name Error Animation
                    AnimatedVisibility(
                        visible = nameValidationError != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ErrorColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = nameValidationError ?: "",
                                color = ErrorColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ==========================================
                    // 4. VERIFIED PHONE NUMBER CARD
                    // ==========================================
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF24303E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mobile number",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = phoneNumber.ifBlank { "+880 1712-345678" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }

                            // Verified Check Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(SuccessColor.copy(alpha = 0.12f))
                                    .border(1.dp, SuccessColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = SuccessColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified",
                                    color = SuccessColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Global Error Message Animation
                    AnimatedVisibility(
                        visible = !errorMessage.isNullOrEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ErrorColor.copy(alpha = 0.12f))
                                .border(1.dp, ErrorColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ErrorColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = ErrorColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ==========================================
                // 5. SAVE & CONTINUE BUTTON
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 8.dp)
                ) {
                    val isFormValid = nameInput.isNotBlank() && !isLoading
                    val saveInteractionSource = remember { MutableInteractionSource() }
                    val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                    val saveScale by animateFloatAsState(
                        targetValue = if (isSavePressed && isFormValid) 0.97f else 1f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "saveScale"
                    )

                    Button(
                        onClick = { submitProfile() },
                        enabled = isFormValid,
                        interactionSource = saveInteractionSource,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            disabledContainerColor = Color(0xFF1B2834),
                            contentColor = Color(0xFF040E14),
                            disabledContentColor = Color(0xFF475569)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(saveScale)
                            .shadow(
                                elevation = if (isFormValid) 12.dp else 0.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = ElectricCyan.copy(alpha = 0.35f)
                            )
                            .testTag("save_profile_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFF040E14),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = "Save & Continue",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CUSTOM PREMIUM INPUT COMPONENT
// ==========================================
@Composable
private fun ProfileInputField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: ImageVector,
    imeAction: ImeAction = ImeAction.Default,
    onDone: () -> Unit = {},
    isError: Boolean = false,
    testTag: String = ""
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> ErrorColor
            isFocused -> ElectricCyan
            else -> Color(0xFF24303E)
        },
        animationSpec = tween(200),
        label = "borderColor"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isError -> ErrorColor
            isFocused -> ElectricCyan
            else -> TextMuted
        },
        animationSpec = tween(200),
        label = "iconTint"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isError) ErrorColor else if (isFocused) ElectricCyan else TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        Surface(
            color = SurfaceCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(ElectricCyan),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = imeAction
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onDone() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                    )
                }
            }
        }
    }
}
