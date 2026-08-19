package com.family.talkly.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
private val BorderSubtle = Color(0xFF1E293B)
private val BorderElevated = Color(0xFF24303E)

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onSearchUserByPhone: (phone: String, onResult: (UserProfile?) -> Unit) -> Unit,
    onAddContact: (name: String, phone: String, relation: String, bio: String, avatarUrl: String?) -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var relationInput by remember { mutableStateOf("Family Member") }
    var avatarUrlInput by remember { mutableStateOf<String?>(null) }

    var isSearching by remember { mutableStateOf(false) }
    var searchStatusMessage by remember { mutableStateOf<String?>(null) }
    var foundUser by remember { mutableStateOf<UserProfile?>(null) }
    var showRelationDropdown by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val relationOptions = listOf(
        "Family Member", "Mother", "Father", "Brother", "Sister",
        "Grandma", "Grandpa", "Friend", "Spouse", "Colleague", "Other"
    )

    fun triggerUserSearch() {
        val cleanPhone = phoneInput.trim()
        if (cleanPhone.length < 5) {
            searchStatusMessage = "Please enter a valid phone number"
            return
        }

        isSearching = true
        searchStatusMessage = "Searching Talkly network..."
        foundUser = null

        onSearchUserByPhone(cleanPhone) { profile ->
            isSearching = false
            if (profile != null) {
                foundUser = profile
                if (nameInput.isBlank()) {
                    nameInput = profile.name
                }
                avatarUrlInput = profile.profilePicUrl.ifBlank { null }
                searchStatusMessage = "Talkly user verified: ${profile.name}"
            } else {
                foundUser = null
                searchStatusMessage = "User not on Talkly yet. Will be saved to contacts."
            }
        }
    }

    val submitInteractionSource = remember { MutableInteractionSource() }
    val isSubmitPressed by submitInteractionSource.collectIsPressedAsState()
    val submitScale by animateFloatAsState(
        targetValue = if (isSubmitPressed) 0.97f else 1f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label = "submitScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = SurfaceCard,
            border = BorderStroke(1.2.dp, BorderElevated)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Add someone",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = TextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Connect with a new person on Talkly.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Found User Verified Banner Card
                AnimatedVisibility(
                    visible = foundUser != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    if (foundUser != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Brush.sweepGradient(listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan)))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(SurfaceCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!foundUser!!.profilePicUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = foundUser!!.profilePicUrl,
                                                contentDescription = foundUser!!.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = foundUser!!.name.take(1).uppercase(),
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = foundUser!!.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified User",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = foundUser!!.phoneNumber,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Phone Input Field
                Text(
                    text = "Phone Number",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = {
                        phoneInput = it
                        searchStatusMessage = null
                    },
                    placeholder = { Text("+1 (555) 000-0000", color = TextMuted) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = ElectricCyan)
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = {
                                focusManager.clearFocus()
                                triggerUserSearch()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Talkly",
                                    tint = ElectricCyan
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            triggerUserSearch()
                        }
                    ),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = BorderElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLeadingIconColor = ElectricCyan,
                        unfocusedLeadingIconColor = TextSecondary,
                        focusedContainerColor = SurfaceElevated,
                        unfocusedContainerColor = SurfaceElevated
                    )
                )

                if (searchStatusMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = searchStatusMessage!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (foundUser != null) MintAccent else TextSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contact Name Field
                Text(
                    text = "Contact Name",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("e.g. Sarah Jenkins", color = TextMuted) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = ElectricCyan)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = BorderElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLeadingIconColor = ElectricCyan,
                        unfocusedLeadingIconColor = TextSecondary,
                        focusedContainerColor = SurfaceElevated,
                        unfocusedContainerColor = SurfaceElevated
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Relationship Selection
                Text(
                    text = "Relation / Tag",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = relationInput,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.FamilyRestroom, contentDescription = null, tint = ElectricCyan)
                        },
                        trailingIcon = {
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = ElectricCyan)
                        },
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRelationDropdown = true },
                        shape = RoundedCornerShape(16.dp),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = BorderElevated,
                            disabledTextColor = TextPrimary,
                            disabledLeadingIconColor = ElectricCyan,
                            disabledTrailingIconColor = ElectricCyan,
                            disabledContainerColor = SurfaceElevated
                        )
                    )

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showRelationDropdown = true }
                    )

                    DropdownMenu(
                        expanded = showRelationDropdown,
                        onDismissRequest = { showRelationDropdown = false },
                        modifier = Modifier
                            .background(SurfaceElevated)
                            .border(1.dp, BorderElevated, RoundedCornerShape(12.dp))
                    ) {
                        relationOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        color = if (option == relationInput) ElectricCyan else TextPrimary,
                                        fontWeight = if (option == relationInput) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    relationInput = option
                                    showRelationDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Action Buttons: Cancel and Save Contact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderElevated),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val isFormValid = phoneInput.trim().isNotBlank()

                    Surface(
                        onClick = {
                            val cleanPhone = phoneInput.trim()
                            if (cleanPhone.isNotBlank()) {
                                val finalName = nameInput.trim().ifBlank {
                                    foundUser?.name ?: cleanPhone
                                }
                                onAddContact(
                                    finalName,
                                    cleanPhone,
                                    relationInput,
                                    "Available on Talkly 💬",
                                    avatarUrlInput
                                )
                                onDismiss()
                            }
                        },
                        enabled = isFormValid,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .scale(submitScale)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isFormValid) {
                                    Brush.horizontalGradient(listOf(ElectricCyan, DeepAqua))
                                } else {
                                    Brush.horizontalGradient(listOf(SurfaceElevated, SurfaceElevated))
                                }
                            )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Save Contact",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isFormValid) Color(0xFF040E14) else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
