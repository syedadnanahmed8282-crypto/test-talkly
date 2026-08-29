package com.family.talkly.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember

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
fun ContactProfileDetailsDialog(
    member: FamilyMember,
    onDismiss: () -> Unit,
    onStartChat: (FamilyMember) -> Unit,
    onStartCall: (FamilyMember, CallType) -> Unit,
    onDeleteContact: ((String) -> Unit)? = null,
    isMutualContact: Boolean = true
) {
    val context = LocalContext.current
    var showFullAvatarViewer by remember { mutableStateOf(false) }
    var showFullCoverViewer by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "ringPulse")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    // Full Screen Profile Picture Viewer
    if (showFullAvatarViewer && isMutualContact) {
        Dialog(
            onDismissRequest = { showFullAvatarViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = BackgroundDark
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (member.avatarUrl != null) {
                        val mediaModel = remember(member.avatarUrl) {
                            com.family.talkly.util.PhoneUtils.getCoilMediaModel(member.avatarUrl)
                        }
                        AsyncImage(
                            model = mediaModel,
                            contentDescription = member.name,
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
                                text = member.name.take(2).uppercase(),
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 72.sp
                            )
                        }
                    }

                    // Minimal Top Controls
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
                            text = member.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }
            }
        }
    }

    // Full Screen Cover Photo Viewer
    if (showFullCoverViewer && isMutualContact && !member.coverPhotoUrl.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { showFullCoverViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = BackgroundDark
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val mediaModel = remember(member.coverPhotoUrl) {
                        com.family.talkly.util.PhoneUtils.getCoilMediaModel(member.coverPhotoUrl)
                    }
                    AsyncImage(
                        model = mediaModel,
                        contentDescription = "Cover Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

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
                }
            }
        }
    }

    // More Options Bottom Sheet
    if (showMoreOptionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMoreOptionsSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceCard,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(BorderElevated)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Contact Options",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Copy Phone Number
                ProfileOptionTile(
                    icon = Icons.Default.ContentCopy,
                    title = "Copy Phone Number",
                    subtitle = member.phone,
                    onClick = {
                        showMoreOptionsSheet = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Phone", member.phone)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Phone number copied", Toast.LENGTH_SHORT).show()
                    }
                )

                // Share Contact
                ProfileOptionTile(
                    icon = Icons.Default.Share,
                    title = "Share Contact",
                    subtitle = "Send contact card to other apps",
                    onClick = {
                        showMoreOptionsSheet = false
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${member.name}: ${member.phone}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Contact"))
                    }
                )

                // Delete Contact Option
                if (onDeleteContact != null) {
                    ProfileOptionTile(
                        icon = Icons.Default.Delete,
                        title = "Delete Contact",
                        subtitle = "Remove from your saved contacts",
                        tintColor = ErrorColor,
                        onClick = {
                            showMoreOptionsSheet = false
                            onDeleteContact(member.id)
                            onDismiss()
                        }
                    )
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
                // ==========================================
                // 1. PROFILE HERO: Layered Composition
                // ==========================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Cover Photo Background Area with Gradient
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
                                if (isMutualContact && !member.coverPhotoUrl.isNullOrBlank()) {
                                    showFullCoverViewer = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isMutualContact && !member.coverPhotoUrl.isNullOrBlank()) {
                            val mediaModel = remember(member.coverPhotoUrl) {
                                com.family.talkly.util.PhoneUtils.getCoilMediaModel(member.coverPhotoUrl)
                            }
                            AsyncImage(
                                model = mediaModel,
                                contentDescription = "Cover Photo",
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
                        }
                    }

                    // Floating Independent Navigation Controls on Top
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = SurfaceMain.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, BorderElevated),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = { showMoreOptionsSheet = true },
                            shape = CircleShape,
                            color = SurfaceMain.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, BorderElevated),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Overlapping Avatar with Glowing Cyan Accent Ring
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(114.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Animated Gradient Ring
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
                                        .clickable { if (isMutualContact) showFullAvatarViewer = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isMutualContact) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked Avatar",
                                            tint = TextMuted,
                                            modifier = Modifier.size(38.dp)
                                        )
                                    } else if (member.avatarUrl != null) {
                                        val mediaModel = remember(member.avatarUrl) {
                                            com.family.talkly.util.PhoneUtils.getCoilMediaModel(member.avatarUrl)
                                        }
                                        AsyncImage(
                                            model = mediaModel,
                                            contentDescription = member.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = member.name.take(2).uppercase(),
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 32.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Floating Online Status Beacon
                        if (isMutualContact) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (member.isRecentlyActive()) SuccessColor else TextMuted)
                                    .border(3.dp, SurfaceMain, CircleShape)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Name & Relation Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = member.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center
                    )
                    if (member.isRegisteredOnTalkly) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Talkly User",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Online / Presence Status Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (member.isRecentlyActive()) SuccessColor else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (member.isOnline) "Online" else member.displayLastSeen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (member.isOnline) SuccessColor else TextSecondary
                    )

                    if (member.relation.isNotBlank()) {
                        Text(text = " • ", color = TextMuted, fontSize = 13.sp)
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = member.relation,
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ==========================================
                // 2. QUICK ACTION DOCK: Floating Action Dock
                // ==========================================
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, BorderElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Message Action Tile
                        ProfileDockTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Filled.Chat,
                            label = "Message",
                            tint = ElectricCyan,
                            enabled = member.isRegisteredOnTalkly,
                            onClick = {
                                onDismiss()
                                onStartChat(member)
                            }
                        )

                        // Audio Call Action Tile
                        ProfileDockTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Call,
                            label = "Audio",
                            tint = MintAccent,
                            enabled = member.isRegisteredOnTalkly,
                            onClick = {
                                onDismiss()
                                onStartCall(member, CallType.AUDIO)
                            }
                        )

                        // Video Call Action Tile
                        ProfileDockTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Videocam,
                            label = "Video",
                            tint = DeepAqua,
                            enabled = member.isRegisteredOnTalkly,
                            onClick = {
                                onDismiss()
                                onStartCall(member, CallType.VIDEO)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Unregistered Warning if applicable
                if (!member.isRegisteredOnTalkly) {
                    Surface(
                        color = ErrorColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ErrorColor.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ErrorColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "This person hasn't registered on Talkly yet. Messaging and HD calls will become active once they join.",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ==========================================
                // 3. CONTACT INFORMATION CARDS
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // About / Bio Card
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "About",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isMutualContact) member.status else "Message request required to view about info",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isMutualContact) TextPrimary else TextMuted
                                )
                            }
                        }
                    }

                    // Phone Number Card with Quick Copy
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Phone", member.phone)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Phone number copied", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MintAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = MintAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Phone Number",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = member.phone,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Phone",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Circle Relationship / Tag Card
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DeepAqua.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FamilyRestroom,
                                    contentDescription = null,
                                    tint = DeepAqua,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Circle Tag",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = member.relation.ifBlank { "Contact" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ==========================================
// QUICK DOCK ACTION TILE COMPONENT
// ==========================================
@Composable
private fun ProfileDockTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(100, easing = FastOutSlowInEasing),
        label = "tileScale"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, if (enabled) tint.copy(alpha = 0.35f) else BorderElevated),
        modifier = modifier
            .height(72.dp)
            .scale(scale)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else TextMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) TextPrimary else TextMuted
            )
        }
    }
}

// ==========================================
// PROFILE OPTION TILE FOR BOTTOM SHEET
// ==========================================
@Composable
private fun ProfileOptionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tintColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tintColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = tintColor
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
