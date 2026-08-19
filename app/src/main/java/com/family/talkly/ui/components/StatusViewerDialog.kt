package com.family.talkly.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
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
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.StatusItem
import com.family.talkly.data.models.UserStatusGroup
import com.family.talkly.util.PhoneUtils
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// TALKLY COLOR SYSTEM
// ==========================================
private val ViewerBackground = Color(0xFF080B10)
private val ViewerSurface = Color(0xFF11161D)
private val ViewerCard = Color(0xFF18212B)
private val ViewerElevated = Color(0xFF202B36)
private val ViewerCyan = Color(0xFF22D3EE)
private val ViewerAqua = Color(0xFF0EA5A4)
private val ViewerMint = Color(0xFF5EEAD4)
private val ViewerTextPrimary = Color(0xFFF8FAFC)
private val ViewerTextSecondary = Color(0xFFA7B0BA)
private val ViewerTextMuted = Color(0xFF64748B)
private val ViewerHeart = Color(0xFFF43F5E)

@Composable
fun StatusViewerDialog(
    statusGroups: List<UserStatusGroup>,
    initialGroupIndex: Int = 0,
    currentUserId: String = "self",
    familyMembers: List<FamilyMember> = emptyList(),
    onDismiss: () -> Unit,
    onMarkStatusSeen: (statusId: String) -> Unit,
    onAddStatusClick: (() -> Unit)? = null,
    onToggleLikeStatus: ((statusId: String) -> Unit)? = null,
    onSendStatusReply: ((targetUserId: String, replyText: String) -> Unit)? = null,
    onSelectMemberProfile: ((FamilyMember) -> Unit)? = null
) {
    if (statusGroups.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    var groupIndex by remember { mutableIntStateOf(initialGroupIndex.coerceIn(0, statusGroups.lastIndex)) }
    val currentGroup = statusGroups[groupIndex]

    var statusIndex by remember { mutableIntStateOf(0) }
    val currentStatus = currentGroup.statuses.getOrNull(statusIndex) ?: currentGroup.statuses.first()

    val isOwnStatus = remember(currentGroup.userId, currentStatus.userId, currentUserId) {
        currentGroup.userId == "self" ||
        currentGroup.userId == currentUserId ||
        currentStatus.userId == "self" ||
        currentStatus.userId == currentUserId
    }

    var progress by remember(groupIndex, statusIndex) { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var isLikedByMe by remember(currentStatus.id, currentStatus.likes) {
        mutableStateOf(currentStatus.likes.any { it.userId == currentUserId || it.userId == "self" })
    }

    // Mark current status as seen
    LaunchedEffect(currentStatus.id) {
        onMarkStatusSeen(currentStatus.id)
    }

    // Timer loop for auto-advancing progress
    LaunchedEffect(groupIndex, statusIndex, isPaused, showAnalyticsDialog) {
        if (!isPaused && !showAnalyticsDialog) {
            val totalSteps = 100
            val stepDelay = 50L // 5 seconds total (100 * 50ms)
            while (progress < 1f) {
                delay(stepDelay)
                progress += 1f / totalSteps
            }
            // Auto advance
            if (statusIndex < currentGroup.statuses.lastIndex) {
                statusIndex++
            } else if (groupIndex < statusGroups.lastIndex) {
                groupIndex++
                statusIndex = 0
            } else {
                onDismiss()
            }
        }
    }

    fun formatStatusTime(timestamp: Long): String {
        val diffMinutes = (System.currentTimeMillis() - timestamp) / (60 * 1000)
        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 24 * 60 -> "${diffMinutes / 60}h ago"
            else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val bgColor = try {
            Color(android.graphics.Color.parseColor(currentStatus.backgroundColorHex))
        } catch (e: Exception) {
            ViewerBackground
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth * 0.3f) {
                                // Tap left -> previous
                                if (statusIndex > 0) {
                                    statusIndex--
                                } else if (groupIndex > 0) {
                                    groupIndex--
                                    statusIndex = statusGroups[groupIndex].statuses.lastIndex
                                }
                            } else {
                                // Tap right -> next
                                if (statusIndex < currentGroup.statuses.lastIndex) {
                                    statusIndex++
                                } else if (groupIndex < statusGroups.lastIndex) {
                                    groupIndex++
                                    statusIndex = 0
                                } else {
                                    onDismiss()
                                }
                            }
                        }
                    )
                }
        ) {
            // ==========================================
            // 1. BACKGROUND PHOTO OR TEXT CANVAS
            // ==========================================
            if (currentStatus.photoUrl != null) {
                val mediaModel = remember(currentStatus.photoUrl) {
                    PhoneUtils.getCoilMediaModel(currentStatus.photoUrl)
                }
                AsyncImage(
                    model = mediaModel,
                    contentDescription = "Status image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Top Vignette Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                )

                // Bottom Gradient Overlay for text readability & interaction controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                            )
                        )
                )
            }

            // Main Text Content Display
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentStatus.photoUrl == null) {
                    Text(
                        text = currentStatus.textContent ?: "",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ViewerTextPrimary,
                            fontSize = 24.sp,
                            lineHeight = 34.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                } else if (!currentStatus.textContent.isNullOrBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp)
                    ) {
                        Text(
                            text = currentStatus.textContent ?: "",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = ViewerTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // ==========================================
            // 2. TOP SEGMENTED PROGRESS & USER HEADER
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Segmented Progress Bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    currentGroup.statuses.forEachIndexed { idx, _ ->
                        val itemProgress = when {
                            idx < statusIndex -> 1f
                            idx == statusIndex -> progress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { itemProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = ViewerCyan,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // User Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (!isOwnStatus) {
                                    val authorSuffix = PhoneUtils.extractPhoneSuffix(currentGroup.userId)
                                    val authorMember = familyMembers.firstOrNull { m ->
                                        m.id == currentGroup.userId || m.firebaseUid == currentGroup.userId || (authorSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == authorSuffix)
                                    } ?: FamilyMember(
                                        id = currentGroup.userId,
                                        name = currentGroup.userName,
                                        relation = "Contact",
                                        avatarUrl = currentGroup.userAvatarUrl,
                                        status = "Available on Talkly 💬",
                                        phone = currentGroup.userId,
                                        isRegisteredOnTalkly = true,
                                        firebaseUid = if (!currentGroup.userId.startsWith("contact_")) currentGroup.userId else null
                                    )
                                    onDismiss()
                                    onSelectMemberProfile?.invoke(authorMember)
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(ViewerCyan, ViewerMint, ViewerAqua, ViewerCyan)))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(ViewerSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!currentGroup.userAvatarUrl.isNullOrBlank()) {
                                    val mediaModel = remember(currentGroup.userAvatarUrl) {
                                        PhoneUtils.getCoilMediaModel(currentGroup.userAvatarUrl)
                                    }
                                    AsyncImage(
                                        model = mediaModel,
                                        contentDescription = currentGroup.userName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = currentGroup.userName.take(2).uppercase(),
                                        color = ViewerCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = currentGroup.userName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ViewerTextPrimary,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatStatusTime(currentStatus.timestamp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ViewerTextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOwnStatus && onAddStatusClick != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ViewerCyan)
                                    .clickable {
                                        onDismiss()
                                        onAddStatusClick()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add status",
                                        tint = Color(0xFF040E14),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Add Story",
                                        color = Color(0xFF040E14),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. BOTTOM ACTIONS & ANALYTICS AREA
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                if (isOwnStatus) {
                    // Own Status: Viewers & Loves Count Pill Button
                    Surface(
                        color = ViewerSurface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, ViewerCyan.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable { showAnalyticsDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Views",
                                tint = ViewerCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${currentStatus.viewers.size} Views",
                                color = ViewerTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Likes",
                                tint = ViewerHeart,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${currentStatus.likes.size} Loves",
                                color = ViewerTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    // Other User's Status: Love Reaction + Message Reply Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Love Reaction Button with smooth scale
                        val heartScale by animateFloatAsState(
                            targetValue = if (isLikedByMe) 1.15f else 1f,
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            label = "heartScale"
                        )

                        IconButton(
                            onClick = {
                                isLikedByMe = !isLikedByMe
                                onToggleLikeStatus?.invoke(currentStatus.id)
                                val msg = if (isLikedByMe) "Sent love reaction ❤️" else "Reaction removed"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .scale(heartScale)
                                .clip(CircleShape)
                                .background(ViewerSurface.copy(alpha = 0.85f))
                                .border(1.dp, if (isLikedByMe) ViewerHeart.copy(alpha = 0.5f) else ViewerElevated, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Love Reaction",
                                tint = if (isLikedByMe) ViewerHeart else ViewerTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Message Reply Box
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Reply to story...", color = ViewerTextMuted, fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ViewerCyan,
                                unfocusedBorderColor = ViewerElevated,
                                focusedContainerColor = ViewerSurface.copy(alpha = 0.85f),
                                unfocusedContainerColor = ViewerSurface.copy(alpha = 0.85f),
                                cursorColor = ViewerCyan,
                                focusedTextColor = ViewerTextPrimary,
                                unfocusedTextColor = ViewerTextPrimary
                            ),
                            trailingIcon = {
                                if (replyText.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            if (replyText.isNotBlank()) {
                                                onSendStatusReply?.invoke(currentGroup.userId, replyText)
                                                Toast.makeText(context, "Reply sent 📩", Toast.LENGTH_SHORT).show()
                                                replyText = ""
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send Reply",
                                            tint = ViewerCyan
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // ==========================================
            // 4. OWN STORY ANALYTICS MODAL
            // ==========================================
            if (showAnalyticsDialog) {
                StatusAnalyticsModal(
                    status = currentStatus,
                    familyMembers = familyMembers,
                    onDismiss = { showAnalyticsDialog = false },
                    onSelectMemberProfile = { member ->
                        showAnalyticsDialog = false
                        onDismiss()
                        onSelectMemberProfile?.invoke(member)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusAnalyticsModal(
    status: StatusItem,
    familyMembers: List<FamilyMember> = emptyList(),
    onDismiss: () -> Unit,
    onSelectMemberProfile: ((FamilyMember) -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Viewers, 1: Likes

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = ViewerBackground,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, ViewerElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Story Insights",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = ViewerTextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(ViewerCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ViewerTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ViewerCard,
                    contentColor = ViewerCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ViewerCyan,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Viewers (${status.viewers.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selectedTab == 0) ViewerCyan else ViewerTextMuted
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Loves (${status.likes.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selectedTab == 1) ViewerCyan else ViewerTextMuted
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    if (status.viewers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No viewers yet",
                                color = ViewerTextMuted,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(220.dp)
                        ) {
                            items(status.viewers) { viewer ->
                                val vSuffix = PhoneUtils.extractPhoneSuffix(viewer.userId)
                                val matchingMember = familyMembers.firstOrNull { m ->
                                    m.id == viewer.userId || m.firebaseUid == viewer.userId || (vSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == vSuffix)
                                }
                                val viewerAvatar = matchingMember?.avatarUrl ?: viewer.userAvatarUrl
                                val viewerName = matchingMember?.name ?: viewer.userName

                                Surface(
                                    color = ViewerCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ViewerElevated),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val targetMember = matchingMember ?: FamilyMember(
                                                id = viewer.userId,
                                                name = viewerName,
                                                relation = "Contact",
                                                avatarUrl = viewerAvatar,
                                                status = "Available on Talkly 💬",
                                                phone = viewer.userId,
                                                isRegisteredOnTalkly = true,
                                                firebaseUid = if (!viewer.userId.startsWith("contact_")) viewer.userId else null
                                            )
                                            onDismiss()
                                            onSelectMemberProfile?.invoke(targetMember)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(ViewerElevated),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!viewerAvatar.isNullOrBlank()) {
                                                val mediaModel = remember(viewerAvatar) {
                                                    PhoneUtils.getCoilMediaModel(viewerAvatar)
                                                }
                                                AsyncImage(
                                                    model = mediaModel,
                                                    contentDescription = viewerName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(
                                                    text = viewerName.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = ViewerCyan
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = viewerName,
                                                fontWeight = FontWeight.Bold,
                                                color = ViewerTextPrimary,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = viewer.timeAgo,
                                                color = ViewerTextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = ViewerCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (status.likes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No loves yet",
                                color = ViewerTextMuted,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(220.dp)
                        ) {
                            items(status.likes) { liker ->
                                val lSuffix = PhoneUtils.extractPhoneSuffix(liker.userId)
                                val matchingMember = familyMembers.firstOrNull { m ->
                                    m.id == liker.userId || m.firebaseUid == liker.userId || (lSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == lSuffix)
                                }
                                val likerAvatar = matchingMember?.avatarUrl ?: liker.userAvatarUrl
                                val likerName = matchingMember?.name ?: liker.userName

                                Surface(
                                    color = ViewerCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ViewerElevated),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val targetMember = matchingMember ?: FamilyMember(
                                                id = liker.userId,
                                                name = likerName,
                                                relation = "Contact",
                                                avatarUrl = likerAvatar,
                                                status = "Available on Talkly 💬",
                                                phone = liker.userId,
                                                isRegisteredOnTalkly = true,
                                                firebaseUid = if (!liker.userId.startsWith("contact_")) liker.userId else null
                                            )
                                            onDismiss()
                                            onSelectMemberProfile?.invoke(targetMember)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(ViewerHeart.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!likerAvatar.isNullOrBlank()) {
                                                val mediaModel = remember(likerAvatar) {
                                                    PhoneUtils.getCoilMediaModel(likerAvatar)
                                                }
                                                AsyncImage(
                                                    model = mediaModel,
                                                    contentDescription = likerName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(
                                                    text = likerName.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = ViewerHeart
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = likerName,
                                            fontWeight = FontWeight.Bold,
                                            color = ViewerTextPrimary,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = ViewerHeart,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
