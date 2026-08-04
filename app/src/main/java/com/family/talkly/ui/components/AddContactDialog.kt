package com.family.talkly.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.data.models.UserProfile

// Classic Dark Purple & Sage Green / Laurel Green Palette
private val ClassicDarkPurpleBg = Color(0xFF201030)        // Deep Classic Dark Purple
private val ClassicDarkPurpleCard = Color(0xFF2C1740)      // Classic Dark Purple Card surface
private val ClassicInputBg = Color(0xFF140921)           // High contrast dark container for inputs
private val SageGreenAccent = Color(0xFF8FA87B)           // Warm Sage Green / Laurel Green
private val SageGreenMint = Color(0xFFAEC89B)             // Light Pastel Sage for headings/highlights
private val SoftSageText = Color(0xFFCBE0BD)              // Soft Pastel Sage for subtext
private val SoftPurpleSubtext = Color(0xFFC7B7E0)         // Soft Light Purple for labels/placeholders
private val ClassicInputBorder = Color(0xFF4A2A6B)        // Dark Purple input border

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

    val relationOptions = listOf("Family Member", "Mother", "Father", "Brother", "Sister", "Grandma", "Grandpa", "Friend", "Spouse", "Other")

    fun triggerUserSearch() {
        val cleanPhone = phoneInput.trim()
        if (cleanPhone.length < 5) {
            searchStatusMessage = "Please enter a valid phone number"
            return
        }

        isSearching = true
        searchStatusMessage = "Searching Talkly database..."
        foundUser = null

        onSearchUserByPhone(cleanPhone) { profile ->
            isSearching = false
            if (profile != null) {
                foundUser = profile
                if (nameInput.isBlank()) {
                    nameInput = profile.name
                }
                avatarUrlInput = profile.profilePicUrl.ifBlank { null }
                searchStatusMessage = "✅ Talkly user found: ${profile.name}"
            } else {
                foundUser = null
                searchStatusMessage = "ℹ️ User not registered on Talkly yet. Saved as offline contact."
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
                .padding(vertical = 16.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = SageGreenAccent, spotColor = SageGreenAccent),
            shape = RoundedCornerShape(24.dp),
            color = ClassicDarkPurpleBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SageGreenAccent.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(SageGreenMint, SageGreenAccent)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = ClassicDarkPurpleBg,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Add New Member",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = SageGreenMint
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SoftPurpleSubtext
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Found User Banner Card
                if (foundUser != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        colors = CardDefaults.cardColors(containerColor = ClassicDarkPurpleCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SageGreenAccent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(SageGreenAccent),
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
                                        color = ClassicDarkPurpleBg,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = foundUser!!.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SageGreenMint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = foundUser!!.phoneNumber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SoftSageText
                                )
                            }
                        }
                    }
                }

                // Phone Input Field (Required)
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = {
                        phoneInput = it
                        searchStatusMessage = null
                    },
                    label = { Text("Mobile Phone Number *", fontWeight = FontWeight.SemiBold) },
                    placeholder = { Text("+8801700000000", color = SoftPurpleSubtext.copy(alpha = 0.5f)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = SageGreenAccent)
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = SageGreenAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { triggerUserSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Talkly User",
                                    tint = SageGreenAccent
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreenAccent,
                        unfocusedBorderColor = ClassicInputBorder,
                        focusedLabelColor = SageGreenMint,
                        unfocusedLabelColor = SoftPurpleSubtext,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLeadingIconColor = SageGreenAccent,
                        unfocusedLeadingIconColor = SoftPurpleSubtext,
                        focusedContainerColor = ClassicInputBg,
                        unfocusedContainerColor = ClassicInputBg
                    )
                )

                if (searchStatusMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = searchStatusMessage!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (foundUser != null) SageGreenMint else SoftPurpleSubtext,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Contact Name Field (Optional - Defaults to Phone or Found Name)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Contact Name (Optional)", fontWeight = FontWeight.SemiBold) },
                    placeholder = { Text("e.g. Brother Rahat", color = SoftPurpleSubtext.copy(alpha = 0.5f)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = SageGreenAccent)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreenAccent,
                        unfocusedBorderColor = ClassicInputBorder,
                        focusedLabelColor = SageGreenMint,
                        unfocusedLabelColor = SoftPurpleSubtext,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLeadingIconColor = SageGreenAccent,
                        unfocusedLeadingIconColor = SoftPurpleSubtext,
                        focusedContainerColor = ClassicInputBg,
                        unfocusedContainerColor = ClassicInputBg
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Relation Selection Field
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = relationInput,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Relation / Tag", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.FamilyRestroom, contentDescription = null, tint = SageGreenAccent)
                        },
                        trailingIcon = {
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = SageGreenAccent)
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRelationDropdown = true },
                        shape = RoundedCornerShape(14.dp),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = ClassicInputBorder,
                            disabledLabelColor = SoftPurpleSubtext,
                            disabledTextColor = Color.White,
                            disabledLeadingIconColor = SageGreenAccent,
                            disabledTrailingIconColor = SageGreenAccent,
                            disabledContainerColor = ClassicInputBg
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
                            .background(ClassicDarkPurpleCard)
                            .border(1.dp, SageGreenAccent, RoundedCornerShape(8.dp))
                    ) {
                        relationOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        color = if (option == relationInput) SageGreenMint else Color.White,
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

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = SoftPurpleSubtext,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
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
                        enabled = phoneInput.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SageGreenAccent,
                            contentColor = ClassicDarkPurpleBg,
                            disabledContainerColor = SageGreenAccent.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(46.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Save Contact",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ClassicDarkPurpleBg
                        )
                    }
                }
            }
        }
    }
}
