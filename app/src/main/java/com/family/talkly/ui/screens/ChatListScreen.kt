@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.family.talkly.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.family.talkly.data.models.CallDirection
import com.family.talkly.data.models.CallLog
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.UserProfile
import com.family.talkly.data.models.UserStatusGroup
import com.family.talkly.ui.components.AddContactDialog
import com.family.talkly.ui.components.BlockedContactsDialog
import com.family.talkly.ui.components.ContactProfileDetailsDialog
import com.family.talkly.ui.components.OnlinePresenceIndicator
import com.family.talkly.ui.components.PostStatusDialog
import com.family.talkly.ui.components.StatusViewerDialog
import com.family.talkly.ui.components.UserProfileDetailsDialog
import com.family.talkly.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.util.Calendar

// ==========================================
// TALKLY SIGNATURE COLOR PALETTE
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
private val BorderSubtle = Color(0xFF1E293B)
private val BorderElevated = Color(0xFF24303E)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    familyMembers: List<FamilyMember>,
    messagesMap: Map<String, List<ChatMessage>>,
    simulatedTimeOffsetMs: Long,
    currentUserProfile: UserProfile? = null,
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: ((ThemeMode) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onSaveProfile: ((name: String, bio: String, photoUrl: String, coverPhotoUrl: String) -> Unit)? = null,
    onSelectMember: (FamilyMember) -> Unit,
    onStartCall: (FamilyMember, CallType) -> Unit,
    onTriggerIncomingDemo: (FamilyMember) -> Unit,
    onTogglePinMember: ((String) -> Unit)? = null,
    onSearchUserByPhone: ((phone: String, onResult: (UserProfile?) -> Unit) -> Unit)? = null,
    onAddContact: ((name: String, phone: String, relation: String, bio: String, avatarUrl: String?) -> Unit)? = null,
    onDeleteContact: ((String) -> Unit)? = null,
    onDeleteChatHistory: ((String) -> Unit)? = null,
    onClearDemoContacts: (() -> Unit)? = null,
    statusGroups: List<UserStatusGroup> = emptyList(),
    onPostStatus: ((textContent: String?, photoUrl: String?, backgroundColorHex: String) -> Unit)? = null,
    onDeleteStatus: ((statusId: String) -> Unit)? = null,
    onMarkStatusSeen: ((statusId: String) -> Unit)? = null,
    onToggleLikeStatus: ((statusId: String) -> Unit)? = null,
    onSendStatusReply: ((targetUserId: String, replyText: String) -> Unit)? = null,
    blockedUserIds: Set<String> = emptySet(),
    onBlockUser: ((String) -> Unit)? = null,
    onUnblockUser: ((String) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    isNetworkConnected: Boolean = true,
    callLogs: List<CallLog> = emptyList()
) {
    val context = LocalContext.current
    var selectedBottomNavTab by remember { mutableIntStateOf(0) } // 0: Chats, 1: Stories, 2: Calls, 3: Contacts
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showBlockedContactsDialog by remember { mutableStateOf(false) }
    var showPostStatusDialog by remember { mutableStateOf(false) }
    var activeViewerGroupIndex by remember { mutableStateOf<Int?>(null) }
    var selectedContactForProfile by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToDeleteHistory by remember { mutableStateOf<FamilyMember?>(null) }

    val isAnyOverlayOpen = selectedContactForProfile != null ||
            activeViewerGroupIndex != null ||
            showAddContactDialog ||
            showProfileDialog ||
            showThemeDialog ||
            showBlockedContactsDialog ||
            showPostStatusDialog ||
            memberToDeleteHistory != null ||
            selectedBottomNavTab != 0 ||
            isSearchExpanded

    BackHandler(enabled = isAnyOverlayOpen) {
        when {
            selectedContactForProfile != null -> selectedContactForProfile = null
            activeViewerGroupIndex != null -> activeViewerGroupIndex = null
            showAddContactDialog -> showAddContactDialog = false
            showProfileDialog -> showProfileDialog = false
            showThemeDialog -> showThemeDialog = false
            showBlockedContactsDialog -> showBlockedContactsDialog = false
            showPostStatusDialog -> showPostStatusDialog = false
            memberToDeleteHistory != null -> memberToDeleteHistory = null
            isSearchExpanded -> {
                isSearchExpanded = false
                searchQuery = ""
            }
            selectedBottomNavTab != 0 -> selectedBottomNavTab = 0
        }
    }

    val currentUid = currentUserProfile?.uid ?: ""
    val currentPhone = currentUserProfile?.phoneNumber ?: ""
    val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)

    val deduplicatedMembers = remember(familyMembers, currentUid, currentPhone, currentSuffix) {
        familyMembers
            .filter { member ->
                val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
                val isSelfByUid = (currentUid.isNotBlank() && (member.id == currentUid || member.firebaseUid == currentUid))
                val isSelfByPhone = (currentPhone.isNotBlank() && member.phone == currentPhone)
                val isSelfBySuffix = (currentSuffix.isNotBlank() && memberSuffix.isNotBlank() && memberSuffix == currentSuffix)
                !(isSelfByUid || isSelfByPhone || isSelfBySuffix)
            }
            .distinctBy { member ->
                val digits = member.phone.filter { it.isDigit() }
                val suffix = if (digits.length >= 10) digits.takeLast(10) else digits
                if (suffix.isNotBlank()) "suffix_$suffix"
                else if (!member.firebaseUid.isNullOrBlank()) "uid_${member.firebaseUid}"
                else "id_${member.id}"
            }
    }

    // Filtered Chat List for Tab 0
    val activeChatMembers = remember(deduplicatedMembers, messagesMap, searchQuery) {
        deduplicatedMembers
            .filter { member ->
                val msgs = getMemberMessages(member, messagesMap)
                val hasHistory = member.isPinned || msgs.isNotEmpty()
                if (!hasHistory) return@filter false

                if (searchQuery.isBlank()) true
                else {
                    member.name.contains(searchQuery, ignoreCase = true) ||
                            member.phone.contains(searchQuery) ||
                            msgs.any { it.textContent.contains(searchQuery, ignoreCase = true) }
                }
            }
            .sortedWith(
                compareByDescending<FamilyMember> { it.isPinned }
                    .thenByDescending { member ->
                        getMemberMessages(member, messagesMap).lastOrNull()?.timestamp ?: 0L
                    }
            )
    }

    // Partition into Pinned and Regular
    val (pinnedMembers, regularMembers) = remember(activeChatMembers) {
        activeChatMembers.partition { it.isPinned }
    }

    // Filtered Contacts for Tab 3
    val filteredContacts = remember(deduplicatedMembers, searchQuery) {
        if (searchQuery.isBlank()) deduplicatedMembers
        else deduplicatedMembers.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) || it.relation.contains(searchQuery, ignoreCase = true)
        }
    }

    // ==========================================
    // DIALOG OVERLAYS
    // ==========================================
    if (showAddContactDialog && onSearchUserByPhone != null && onAddContact != null) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onSearchUserByPhone = { phone, callback ->
                onSearchUserByPhone(phone, callback)
            },
            onAddContact = { name, phone, relation, bio, avatarUrl ->
                onAddContact(name, phone, relation, bio, avatarUrl)
            }
        )
    }

    if (showBlockedContactsDialog) {
        BlockedContactsDialog(
            allMembers = familyMembers,
            blockedUserIds = blockedUserIds,
            onDismiss = { showBlockedContactsDialog = false },
            onUnblockUser = { id -> onUnblockUser?.invoke(id) },
            onBlockUser = { id -> onBlockUser?.invoke(id) }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("App Theme", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                }
            },
            text = {
                Column {
                    val options = listOf(
                        Triple(ThemeMode.DARK, "Dark Mode 🌙", "Obsidian & Electric Cyan canvas"),
                        Triple(ThemeMode.SYSTEM, "System Default 📱", "Match device system theme"),
                        Triple(ThemeMode.LIGHT, "Light Mode ☀️", "Bright visual theme")
                    )
                    options.forEach { (mode, label, subtitle) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onThemeModeChange?.invoke(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentThemeMode == mode),
                                onClick = {
                                    onThemeModeChange?.invoke(mode)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ElectricCyan,
                                    unselectedColor = TextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = label,
                                    fontWeight = if (currentThemeMode == mode) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = TextPrimary
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
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Done", color = ElectricCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showProfileDialog && currentUserProfile != null) {
        UserProfileDetailsDialog(
            userProfile = currentUserProfile,
            onDismiss = { showProfileDialog = false },
            onSaveProfile = { name, bio, photoUrl, coverPhotoUrl ->
                onSaveProfile?.invoke(name, bio, photoUrl, coverPhotoUrl)
            },
            onLogout = onLogout
        )
    }

    if (selectedContactForProfile != null) {
        ContactProfileDetailsDialog(
            member = selectedContactForProfile!!,
            onDismiss = { selectedContactForProfile = null },
            onStartChat = { member ->
                selectedContactForProfile = null
                onSelectMember(member)
            },
            onStartCall = { member, callType ->
                selectedContactForProfile = null
                onStartCall(member, callType)
            },
            onDeleteContact = { id ->
                selectedContactForProfile = null
                onDeleteContact?.invoke(id)
            }
        )
    }

    if (memberToDeleteHistory != null) {
        AlertDialog(
            onDismissRequest = { memberToDeleteHistory = null },
            containerColor = SurfaceCard,
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = ErrorColor
                )
            },
            title = {
                Text(
                    text = "Delete Chat History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all chat history with ${memberToDeleteHistory?.name}? This action cannot be undone.",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = memberToDeleteHistory?.id
                        if (targetId != null) {
                            onDeleteChatHistory?.invoke(targetId)
                            Toast.makeText(context, "Chat history deleted", Toast.LENGTH_SHORT).show()
                        }
                        memberToDeleteHistory = null
                    }
                ) {
                    Text("Delete", color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDeleteHistory = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showPostStatusDialog) {
        PostStatusDialog(
            onDismiss = { showPostStatusDialog = false },
            onPostStatus = { text, photoUrl, bgHex ->
                onPostStatus?.invoke(text, photoUrl, bgHex)
                Toast.makeText(context, "Story shared! Disappears in 24 hours.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (activeViewerGroupIndex != null && statusGroups.isNotEmpty()) {
        StatusViewerDialog(
            statusGroups = statusGroups,
            initialGroupIndex = activeViewerGroupIndex!!,
            currentUserId = currentUserProfile?.uid ?: "self",
            familyMembers = familyMembers,
            onDismiss = { activeViewerGroupIndex = null },
            onMarkStatusSeen = { statusId -> onMarkStatusSeen?.invoke(statusId) },
            onAddStatusClick = { showPostStatusDialog = true },
            onDeleteStatus = onDeleteStatus,
            onToggleLikeStatus = { statusId -> onToggleLikeStatus?.invoke(statusId) },
            onSendStatusReply = { targetUserId, replyText -> onSendStatusReply?.invoke(targetUserId, replyText) },
            onSelectMemberProfile = { member ->
                activeViewerGroupIndex = null
                selectedContactForProfile = member
            }
        )
    }

    // ==========================================
    // MAIN APP CANVAS
    // ==========================================
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .drawBehind {
                    // Ambient radial glow from top-left (Electric Cyan accent)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ElectricCyan.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = center.copy(x = size.width * 0.15f, y = size.height * 0.05f),
                            radius = size.width * 0.7f
                        )
                    )
                }
        ) {
            var isRefreshing by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        onRefresh?.invoke()
                        kotlinx.coroutines.delay(1000)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Network status banner if disconnected
                    if (!isNetworkConnected) {
                        Surface(
                            color = Color(0xFF3F1118),
                            border = BorderStroke(1.dp, ErrorColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = "No Internet",
                                    tint = ErrorColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Offline • Messages will send when connected",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // ==========================================
                    // A. PERSONAL HEADER (Asymmetric & Layered)
                    // ==========================================
                    TalklyPersonalHeader(
                        currentUserProfile = currentUserProfile,
                        onAvatarClick = { showProfileDialog = true },
                        onSearchToggle = { isSearchExpanded = !isSearchExpanded },
                        isSearchExpanded = isSearchExpanded,
                        onMoreMenuClick = { showMenu = true },
                        showMenu = showMenu,
                        onDismissMenu = { showMenu = false },
                        onAddNewContact = { showAddContactDialog = true },
                        onOpenBlocked = { showBlockedContactsDialog = true },
                        onClearDemo = { onClearDemoContacts?.invoke() },
                        onOpenTheme = { showThemeDialog = true },
                        onOpenProfile = { showProfileDialog = true },
                        onLogout = onLogout,
                        currentThemeMode = currentThemeMode
                    )

                    // ==========================================
                    // EXPANDABLE SEARCH CONTROL
                    // ==========================================
                    AnimatedVisibility(
                        visible = isSearchExpanded || (selectedBottomNavTab == 3 && searchQuery.isNotEmpty()),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        SmartSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClear = {
                                searchQuery = ""
                                isSearchExpanded = false
                            },
                            placeholder = if (selectedBottomNavTab == 3) "Search contacts..." else "Search conversations, people & messages..."
                        )
                    }

                    // ==========================================
                    // MAIN CONTENT CONTAINER
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (selectedBottomNavTab) {
                            0 -> {
                                // ==========================================
                                // TAB 0: REBUILT CHAT DASHBOARD
                                // ==========================================
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
                                ) {
                                    // B. QUICK ACTION TILES
                                    item {
                                        TalklyQuickActionPanel(
                                            onNewChat = { showAddContactDialog = true },
                                            onNewContact = { showAddContactDialog = true },
                                            onAddStory = { showPostStatusDialog = true },
                                            onBrowseContacts = { selectedBottomNavTab = 3 }
                                        )
                                    }

                                    // C. STORIES / MOMENTS MEDIA-CARD CAROUSEL
                                    item {
                                        TalklyMomentsCarousel(
                                            currentUserProfile = currentUserProfile,
                                            statusGroups = statusGroups,
                                            familyMembers = familyMembers,
                                            currentUid = currentUid,
                                            onMyStatusClick = { hasStatus, selfIndex ->
                                                if (hasStatus && selfIndex >= 0) {
                                                    activeViewerGroupIndex = selfIndex
                                                } else {
                                                    showPostStatusDialog = true
                                                }
                                            },
                                            onContactStatusClick = { groupIndex ->
                                                activeViewerGroupIndex = groupIndex
                                            },
                                            onAddStory = { showPostStatusDialog = true },
                                            onViewAll = { selectedBottomNavTab = 1 }
                                        )
                                    }

                                    // D. PINNED CONVERSATIONS (If Any)
                                    if (pinnedMembers.isNotEmpty()) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = "Pinned",
                                                    tint = ElectricCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Pinned",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ElectricCyan,
                                                    letterSpacing = 0.2.sp
                                                )
                                            }
                                        }

                                        items(pinnedMembers, key = { "pinned_${it.id}" }) { member ->
                                            val memberMessages = getMemberMessages(member, messagesMap)
                                            val lastMessage = memberMessages.lastOrNull()

                                            TalklyConversationCard(
                                                member = member,
                                                lastMessage = lastMessage,
                                                simulatedTimeOffsetMs = simulatedTimeOffsetMs,
                                                isPinned = true,
                                                onClick = { onSelectMember(member) },
                                                onLongClick = { memberToDeleteHistory = member },
                                                onAvatarClick = { selectedContactForProfile = member },
                                                onAudioCall = { onStartCall(member, CallType.AUDIO) }
                                            )
                                        }
                                    }

                                    // CONVERSATIONS HEADER
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 20.dp, end = 20.dp, top = if (pinnedMembers.isNotEmpty()) 16.dp else 12.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Conversations",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    letterSpacing = (-0.3).sp
                                                )
                                                if (activeChatMembers.isNotEmpty()) {
                                                    val unreadTotal = activeChatMembers.sumOf { it.unreadCount }
                                                    if (unreadTotal > 0) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(ElectricCyan)
                                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = unreadTotal.toString(),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF040E14)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Text(
                                                text = "${activeChatMembers.size} total",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    // EMPTY OR LIST OF CONVERSATIONS
                                    if (activeChatMembers.isEmpty()) {
                                        item {
                                            TalklyEmptyState(
                                                title = if (searchQuery.isNotBlank()) "No matching conversations" else "No conversations yet",
                                                subtitle = if (searchQuery.isNotBlank()) "Try searching a different name or message." else "Start a conversation and stay connected with your contacts.",
                                                buttonText = "Start chatting",
                                                onAction = {
                                                    selectedBottomNavTab = 3 // Go to contacts
                                                }
                                            )
                                        }
                                    } else {
                                        items(regularMembers, key = { it.id }) { member ->
                                            val memberMessages = getMemberMessages(member, messagesMap)
                                            val lastMessage = memberMessages.lastOrNull()

                                            TalklyConversationCard(
                                                member = member,
                                                lastMessage = lastMessage,
                                                simulatedTimeOffsetMs = simulatedTimeOffsetMs,
                                                isPinned = false,
                                                onClick = { onSelectMember(member) },
                                                onLongClick = { memberToDeleteHistory = member },
                                                onAvatarClick = { selectedContactForProfile = member },
                                                onAudioCall = { onStartCall(member, CallType.AUDIO) }
                                            )
                                        }
                                    }
                                }
                            }

                            1 -> {
                                // TAB 1: STORIES TAB
                                FullStoriesTab(
                                    currentUserProfile = currentUserProfile,
                                    statusGroups = statusGroups,
                                    familyMembers = familyMembers,
                                    currentUid = currentUid,
                                    onPostStory = { showPostStatusDialog = true },
                                    onViewStatusGroup = { idx -> activeViewerGroupIndex = idx }
                                )
                            }

                            2 -> {
                                // TAB 2: CALLS TAB
                                CallsTab(
                                    callLogs = callLogs,
                                    familyMembers = deduplicatedMembers,
                                    onStartCall = onStartCall
                                )
                            }

                            3 -> {
                                // TAB 3: CONTACTS TAB
                                ContactsTab(
                                    contacts = filteredContacts,
                                    blockedUserIds = blockedUserIds,
                                    onSelectContact = { selectedContactForProfile = it },
                                    onStartChat = { onSelectMember(it) },
                                    onStartCall = onStartCall,
                                    onUnblockUser = { onUnblockUser?.invoke(it) },
                                    onAddNewContact = { showAddContactDialog = true }
                                )
                            }
                        }

                        // ==========================================
                        // G. FLOATING NEW MESSAGE ACTION (Pill Capsule)
                        // ==========================================
                        if (selectedBottomNavTab == 0 || selectedBottomNavTab == 3) {
                            val fabInteractionSource = remember { MutableInteractionSource() }
                            val isFabPressed by fabInteractionSource.collectIsPressedAsState()
                            val fabScale by animateFloatAsState(
                                targetValue = if (isFabPressed) 0.93f else 1f,
                                animationSpec = tween(150, easing = FastOutSlowInEasing),
                                label = "fabScale"
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 20.dp, bottom = 92.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .scale(fabScale)
                                        .shadow(
                                            elevation = 16.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            spotColor = ElectricCyan.copy(alpha = 0.5f)
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable(
                                            interactionSource = fabInteractionSource,
                                            indication = null
                                        ) {
                                            showAddContactDialog = true
                                        },
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(ElectricCyan, DeepAqua)
                                                )
                                            )
                                            .padding(horizontal = 18.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = if (selectedBottomNavTab == 3) Icons.Default.PersonAdd else Icons.Default.ChatBubble,
                                                contentDescription = null,
                                                tint = Color(0xFF040E14),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (selectedBottomNavTab == 3) "Add contact" else "New message",
                                                color = Color(0xFF040E14),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // H. FLOATING TRANSLUCENT BOTTOM NAVIGATION
                // ==========================================
                TalklyFloatingBottomBar(
                    selectedTab = selectedBottomNavTab,
                    onTabSelected = { selectedBottomNavTab = it },
                    unreadChatsCount = activeChatMembers.sumOf { it.unreadCount },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

// ==========================================
// A. PERSONAL HEADER (Asymmetric & Layered)
// ==========================================
@Composable
private fun TalklyPersonalHeader(
    currentUserProfile: UserProfile?,
    onAvatarClick: () -> Unit,
    onSearchToggle: () -> Unit,
    isSearchExpanded: Boolean,
    onMoreMenuClick: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onAddNewContact: () -> Unit,
    onOpenBlocked: () -> Unit,
    onClearDemo: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenProfile: () -> Unit,
    onLogout: (() -> Unit)?,
    currentThemeMode: ThemeMode
) {
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greetingText = when (currentHour) {
        in 5..11 -> "Good morning,"
        in 12..16 -> "Good afternoon,"
        in 17..21 -> "Good evening,"
        else -> "Good night,"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Large Avatar + Greeting Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onAvatarClick() }
        ) {
            // Large User Avatar with Cyan-Mint Gradient Ring
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan)
                            )
                        )
                        .padding(2.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentUserProfile?.profilePicUrl?.isNotBlank() == true) {
                            val mediaModel = remember(currentUserProfile.profilePicUrl) {
                                com.family.talkly.util.PhoneUtils.getCoilMediaModel(currentUserProfile.profilePicUrl)
                            }
                            AsyncImage(
                                model = mediaModel,
                                contentDescription = "My Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = (currentUserProfile?.name?.take(1) ?: "T").uppercase(),
                                color = ElectricCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                        }
                    }
                }

                // Active Online Dot
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .background(SuccessColor, CircleShape)
                        .border(2.5.dp, BackgroundDark, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = greetingText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    letterSpacing = 0.2.sp
                )
                Text(
                    text = currentUserProfile?.name?.ifBlank { "Stay connected" } ?: "Stay connected",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            }
        }

        // Right Controls: Compact Circular Action Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Search Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isSearchExpanded) ElectricCyan.copy(alpha = 0.2f) else SurfaceCard)
                    .border(1.dp, if (isSearchExpanded) ElectricCyan else BorderElevated, CircleShape)
                    .clickable { onSearchToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isSearchExpanded) ElectricCyan else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Action / More Options Menu
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard)
                    .border(1.dp, BorderElevated, CircleShape)
                    .clickable { onMoreMenuClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onDismissMenu,
                    modifier = Modifier.background(SurfaceCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add New Contact", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = ElectricCyan)
                        },
                        onClick = {
                            onDismissMenu()
                            onAddNewContact()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Blocked Contacts", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Block, contentDescription = null, tint = ErrorColor)
                        },
                        onClick = {
                            onDismissMenu()
                            onOpenBlocked()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Demo Contacts", color = TextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = TextMuted)
                        },
                        onClick = {
                            onDismissMenu()
                            onClearDemo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("App Theme (${currentThemeMode.name})", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MintAccent)
                        },
                        onClick = {
                            onDismissMenu()
                            onOpenTheme()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("My Profile", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = DeepAqua)
                        },
                        onClick = {
                            onDismissMenu()
                            onOpenProfile()
                        }
                    )
                    if (onLogout != null) {
                        DropdownMenuItem(
                            text = { Text("Log Out Session", color = ErrorColor) },
                            leadingIcon = {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = ErrorColor)
                            },
                            onClick = {
                                onDismissMenu()
                                onLogout()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// B. QUICK ACTION PANEL (Horizontal Action Area)
// ==========================================
@Composable
private fun TalklyQuickActionPanel(
    onNewChat: () -> Unit,
    onNewContact: () -> Unit,
    onAddStory: () -> Unit,
    onBrowseContacts: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickActionTile(
            icon = Icons.Default.ChatBubble,
            label = "New chat",
            accentColor = ElectricCyan,
            onClick = onNewChat,
            modifier = Modifier.weight(1f)
        )
        QuickActionTile(
            icon = Icons.Default.PersonAdd,
            label = "Add contact",
            accentColor = MintAccent,
            onClick = onNewContact,
            modifier = Modifier.weight(1f)
        )
        QuickActionTile(
            icon = Icons.Default.Add,
            label = "Story",
            accentColor = DeepAqua,
            onClick = onAddStory,
            modifier = Modifier.weight(1f)
        )
        QuickActionTile(
            icon = Icons.Default.People,
            label = "Contacts",
            accentColor = ElectricCyan,
            onClick = onBrowseContacts,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tileScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "tileScale"
    )

    Surface(
        modifier = modifier
            .scale(tileScale)
            .height(72.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// C. STORIES / MOMENTS MEDIA-CARD CAROUSEL
// ==========================================
@Composable
private fun TalklyMomentsCarousel(
    currentUserProfile: UserProfile?,
    statusGroups: List<UserStatusGroup>,
    familyMembers: List<FamilyMember>,
    currentUid: String,
    onMyStatusClick: (hasStatus: Boolean, selfIndex: Int) -> Unit,
    onContactStatusClick: (groupIndex: Int) -> Unit,
    onAddStory: () -> Unit,
    onViewAll: () -> Unit
) {
    val selfGroup = statusGroups.firstOrNull {
        it.userId == "self" || it.userId == currentUid || (currentUserProfile?.uid != null && it.userId == currentUserProfile.uid)
    }
    val hasMyStatus = selfGroup != null && selfGroup.statuses.isNotEmpty()
    val contactGroups = remember(statusGroups, currentUid, currentUserProfile?.uid) {
        statusGroups
            .filter { it.userId != "self" && it.userId != currentUid && (currentUserProfile?.uid == null || it.userId != currentUserProfile.uid) && it.statuses.isNotEmpty() }
            .sortedByDescending { it.hasUnseen }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Section Header: "Moments" & "View all"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Moments",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${contactGroups.size + (if (hasMyStatus) 1 else 0)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }
            }

            Text(
                text = "View all",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ElectricCyan,
                modifier = Modifier.clickable { onViewAll() }
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First Card: "Your Story"
            item {
                Surface(
                    modifier = Modifier
                        .width(155.dp)
                        .height(115.dp)
                        .clickable {
                            val selfIdx = if (selfGroup != null) statusGroups.indexOf(selfGroup) else -1
                            onMyStatusClick(hasMyStatus, selfIdx)
                        },
                    color = SurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.2.dp,
                        if (hasMyStatus) ElectricCyan.copy(alpha = 0.5f) else BorderElevated
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background gradient accent
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            SurfaceElevated.copy(alpha = 0.6f),
                                            SurfaceCard
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top: Avatar with add badge
                            Box(
                                modifier = Modifier.size(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (hasMyStatus) Brush.sweepGradient(listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan))
                                            else SolidColor(Color(0xFF24303E))
                                        )
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(SurfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentUserProfile?.profilePicUrl?.isNotBlank() == true) {
                                            val mediaModel = remember(currentUserProfile.profilePicUrl) {
                                                com.family.talkly.util.PhoneUtils.getCoilMediaModel(currentUserProfile.profilePicUrl)
                                            }
                                            AsyncImage(
                                                model = mediaModel,
                                                contentDescription = "Your Story",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = (currentUserProfile?.name?.take(1) ?: "U").uppercase(),
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }

                                if (!hasMyStatus) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(ElectricCyan)
                                            .border(1.5.dp, BackgroundDark, CircleShape)
                                            .align(Alignment.BottomEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color(0xFF040E14),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }

                            // Bottom: Labels
                            Column {
                                Text(
                                    text = "Your Story",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (hasMyStatus) "${selfGroup?.statuses?.size ?: 0} active" else "Tap to add",
                                    fontSize = 11.sp,
                                    color = if (hasMyStatus) ElectricCyan else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Contact Stories (Media-Cards)
            items(contactGroups) { group ->
                val member = familyMembers.firstOrNull { it.id == group.userId }
                val hasUnseen = group.hasUnseen
                val latestStatus = group.statuses.maxByOrNull { it.timestamp }
                val photoUrl = latestStatus?.photoUrl
                val formattedTime = latestStatus?.let {
                    val diff = (System.currentTimeMillis() - it.timestamp) / (60 * 1000)
                    when {
                        diff < 1 -> "Just now"
                        diff < 60 -> "${diff}m ago"
                        diff < 24 * 60 -> "${diff / 60}h ago"
                        else -> "Today"
                    }
                } ?: ""

                Surface(
                    modifier = Modifier
                        .width(155.dp)
                        .height(115.dp)
                        .clickable {
                            val idx = statusGroups.indexOf(group)
                            if (idx >= 0) onContactStatusClick(idx)
                        },
                    color = SurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.2.dp,
                        if (hasUnseen) ElectricCyan.copy(alpha = 0.6f) else BorderElevated
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background Photo Preview (if available) or Atmospheric Backdrop
                        if (!photoUrl.isNullOrBlank()) {
                            val mediaModel = remember(photoUrl) {
                                com.family.talkly.util.PhoneUtils.getCoilMediaModel(photoUrl)
                            }
                            AsyncImage(
                                model = mediaModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF1E2D3D),
                                                SurfaceElevated
                                            )
                                        )
                                    )
                            )
                        }

                        // Gradient Scrim for text legibility
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.2f),
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )

                        // Content Layout
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top: Mini Avatar with Status Ring
                            Box(
                                modifier = Modifier.size(34.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (hasUnseen) Brush.sweepGradient(listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan))
                                            else SolidColor(Color(0xFF24303E))
                                        )
                                        .padding(1.5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(SurfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val avatar = group.userAvatarUrl ?: member?.avatarUrl
                                        if (avatar?.isNotBlank() == true) {
                                            val mediaModel = remember(avatar) {
                                                com.family.talkly.util.PhoneUtils.getCoilMediaModel(avatar)
                                            }
                                            AsyncImage(
                                                model = mediaModel,
                                                contentDescription = group.userName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = group.userName.take(2).uppercase(),
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                if (hasUnseen) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(ElectricCyan, CircleShape)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }

                            // Bottom Info: Name and Time
                            Column {
                                Text(
                                    text = group.userName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedTime,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (hasUnseen) MintAccent else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// D. PREMIUM CONVERSATION CARD
// ==========================================
@Composable
private fun TalklyConversationCard(
    member: FamilyMember,
    lastMessage: ChatMessage?,
    simulatedTimeOffsetMs: Long,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAudioCall: () -> Unit
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "cardScale"
    )

    Surface(
        color = if (isPinned) SurfaceCard.copy(alpha = 0.95f) else SurfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (isPinned) ElectricCyan.copy(alpha = 0.35f) else BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .scale(cardScale)
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Status Badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, BorderElevated, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (member.avatarUrl?.isNotBlank() == true) {
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
                            fontSize = 18.sp
                        )
                    }
                }

                // Online indicator
                if (member.isRecentlyActive()) {
                    OnlinePresenceIndicator(
                        member = member,
                        size = 13.dp,
                        borderColor = SurfaceCard,
                        borderWidth = 2.dp,
                        greenColor = SuccessColor,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = 3.dp)
                            )
                        }
                        Text(
                            text = member.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val displayTime = if (lastMessage != null) lastMessage.formattedTime else (if (member.isOnline) "Online" else member.displayLastSeen)
                    Text(
                        text = if (member.isTyping) "typing..." else displayTime,
                        fontSize = 11.sp,
                        fontWeight = if (member.isTyping) FontWeight.Bold else FontWeight.Normal,
                        color = if (member.isTyping) ElectricCyan else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (member.isTyping) {
                        Text(
                            text = "typing...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val rawText = lastMessage?.textContent ?: member.status
                        val isMissedCall = rawText.contains("Missed", ignoreCase = true)

                        if (isMissedCall) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallMissed,
                                    contentDescription = "Missed Call",
                                    tint = ErrorColor,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(end = 4.dp)
                                )
                                Text(
                                    text = rawText,
                                    fontSize = 13.sp,
                                    color = ErrorColor,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            val previewText = when {
                                lastMessage != null && lastMessage.isMediaExpired(simulatedTimeOffsetMs) -> "⚠️ Media expired after 48h"
                                lastMessage?.mediaUrl != null -> "📷 Photo / Media"
                                lastMessage != null -> lastMessage.textContent
                                else -> member.status
                            }

                            Text(
                                text = previewText,
                                fontSize = 13.sp,
                                color = if (lastMessage?.isMediaExpired(simulatedTimeOffsetMs) == true) Color(0xFFFFD54F) else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (member.unreadCount > 0 && !member.isTyping) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ElectricCyan)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.unreadCount.toString(),
                                color = Color(0xFF040E14),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick Call Action
            IconButton(
                onClick = onAudioCall,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Quick Audio Call",
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==========================================
// SMART SEARCH BAR
// ==========================================
@Composable
private fun SmartSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String = "Search conversations..."
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) ElectricCyan else BorderElevated,
        animationSpec = tween(200),
        label = "searchBorder"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .height(50.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) ElectricCyan else TextMuted,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(ElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// F. TALKLY EMPTY STATE
// ==========================================
@Composable
private fun TalklyEmptyState(
    title: String,
    subtitle: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard)
                    .border(1.5.dp, BorderElevated, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color(0xFF040E14)
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ==========================================
// H. FLOATING TRANSLUCENT BOTTOM NAVIGATION BAR
// ==========================================
@Composable
private fun TalklyFloatingBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    unreadChatsCount: Int,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        Triple("Chats", Icons.Default.Chat, unreadChatsCount),
        Triple("Stories", Icons.Default.ChatBubble, 0),
        Triple("Calls", Icons.Default.Call, 0),
        Triple("Contacts", Icons.Default.People, 0)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .height(64.dp)
            .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = ElectricCyan.copy(alpha = 0.25f)),
        color = Color(0xF211161D), // Dark Translucent Glass
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.2.dp, BorderElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, (label, icon, badgeCount) ->
                val isSelected = selectedTab == index

                val tabBgColor by animateColorAsState(
                    targetValue = if (isSelected) ElectricCyan.copy(alpha = 0.15f) else Color.Transparent,
                    animationSpec = tween(200),
                    label = "tabBg"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) ElectricCyan else TextSecondary,
                    animationSpec = tween(200),
                    label = "contentColor"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(tabBgColor)
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            if (badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .offset(x = 3.dp, y = (-2).dp)
                                        .clip(CircleShape)
                                        .background(ElectricCyan)
                                )
                            }
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = ElectricCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 1: FULL STORIES TAB
// ==========================================
@Composable
private fun FullStoriesTab(
    currentUserProfile: UserProfile?,
    statusGroups: List<UserStatusGroup>,
    familyMembers: List<FamilyMember>,
    currentUid: String,
    onPostStory: () -> Unit,
    onViewStatusGroup: (Int) -> Unit
) {
    val selfGroup = statusGroups.firstOrNull {
        it.userId == "self" || it.userId == currentUid || (currentUserProfile?.uid != null && it.userId == currentUserProfile.uid)
    }
    val hasMyStatus = selfGroup != null && selfGroup.statuses.isNotEmpty()

    val contactGroups = remember(statusGroups, currentUid, currentUserProfile?.uid) {
        statusGroups
            .filter { it.userId != "self" && it.userId != currentUid && (currentUserProfile?.uid == null || it.userId != currentUserProfile.uid) && it.statuses.isNotEmpty() }
    }

    val unviewedGroups = contactGroups.filter { it.hasUnseen }
    val viewedGroups = contactGroups.filter { !it.hasUnseen }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // My Story Feature Card
        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (hasMyStatus) ElectricCyan.copy(alpha = 0.35f) else Color(0xFF202B36)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val selfIdx = if (selfGroup != null) statusGroups.indexOf(selfGroup) else -1
                        if (hasMyStatus && selfIdx >= 0) {
                            onViewStatusGroup(selfIdx)
                        } else {
                            onPostStory()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasMyStatus) Brush.sweepGradient(listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan))
                                    else SolidColor(Color(0xFF202B36))
                                )
                                .padding(2.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentUserProfile?.profilePicUrl?.isNotBlank() == true) {
                                    val mediaModel = remember(currentUserProfile.profilePicUrl) {
                                        com.family.talkly.util.PhoneUtils.getCoilMediaModel(currentUserProfile.profilePicUrl)
                                    }
                                    AsyncImage(
                                        model = mediaModel,
                                        contentDescription = "My Story",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = (currentUserProfile?.name?.take(1) ?: "U").uppercase(),
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        if (!hasMyStatus) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan)
                                    .border(2.dp, BackgroundDark, CircleShape)
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color(0xFF040E14),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Story",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasMyStatus) "${selfGroup?.statuses?.size ?: 0} active updates • Tap to view" else "Share photos, text or moments",
                            fontSize = 12.sp,
                            color = if (hasMyStatus) ElectricCyan else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onPostStory,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(ElectricCyan, DeepAqua)))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add story",
                            tint = Color(0xFF040E14),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Active / Recent Stories Header & Items
        if (unviewedGroups.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent Updates",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "${unviewedGroups.size} new",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }
            }

            items(unviewedGroups) { group ->
                val member = familyMembers.firstOrNull { it.id == group.userId }
                val latestStatus = group.statuses.maxByOrNull { it.timestamp }
                val formattedTime = latestStatus?.let {
                    val diff = (System.currentTimeMillis() - it.timestamp) / (60 * 1000)
                    when {
                        diff < 1 -> "Just now"
                        diff < 60 -> "${diff}m ago"
                        diff < 24 * 60 -> "${diff / 60}h ago"
                        else -> "Today"
                    }
                } ?: ""

                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val idx = statusGroups.indexOf(group)
                            if (idx >= 0) onViewStatusGroup(idx)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan)))
                                .padding(2.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatar = group.userAvatarUrl ?: member?.avatarUrl
                                if (avatar?.isNotBlank() == true) {
                                    val mediaModel = remember(avatar) {
                                        com.family.talkly.util.PhoneUtils.getCoilMediaModel(avatar)
                                    }
                                    AsyncImage(
                                        model = mediaModel,
                                        contentDescription = group.userName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = group.userName.take(2).uppercase(),
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.userName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${group.statuses.size} new • $formattedTime",
                                fontSize = 12.sp,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }

        // Viewed / Muted Stories Section
        if (viewedGroups.isNotEmpty()) {
            item {
                Text(
                    text = "Viewed Updates",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                )
            }

            items(viewedGroups) { group ->
                val member = familyMembers.firstOrNull { it.id == group.userId }

                Surface(
                    color = SurfaceCard.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF202B36)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val idx = statusGroups.indexOf(group)
                            if (idx >= 0) onViewStatusGroup(idx)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF202B36))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatar = group.userAvatarUrl ?: member?.avatarUrl
                                if (avatar?.isNotBlank() == true) {
                                    val mediaModel = remember(avatar) {
                                        com.family.talkly.util.PhoneUtils.getCoilMediaModel(avatar)
                                    }
                                    AsyncImage(
                                        model = mediaModel,
                                        contentDescription = group.userName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = group.userName.take(2).uppercase(),
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.userName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${group.statuses.size} updates viewed",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Empty state when no contact stories exist
        if (contactGroups.isEmpty()) {
            item {
                TalklyEmptyState(
                    title = "No stories right now",
                    subtitle = "When your contacts post photos or text updates, they will appear here.",
                    buttonText = "Share a Story",
                    onAction = onPostStory
                )
            }
        }
    }
}

// ==========================================
// TAB 2: CALLS TAB
// ==========================================
@Composable
private fun CallsTab(
    callLogs: List<CallLog>,
    familyMembers: List<FamilyMember>,
    onStartCall: (FamilyMember, CallType) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick Call Contacts Horizontal Carousel
        if (familyMembers.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = "Quick Call",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(familyMembers.take(8), key = { "call_tab_quick_${it.id}" }) { member ->
                            Surface(
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(130.dp),
                                color = SurfaceCard,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, BorderElevated)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceElevated)
                                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (member.avatarUrl?.isNotBlank() == true) {
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
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Text(
                                        text = member.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(ElectricCyan.copy(alpha = 0.15f))
                                                .clickable { onStartCall(member, CallType.AUDIO) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "Audio call",
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MintAccent.copy(alpha = 0.15f))
                                                .clickable {
                                                    android.util.Log.e("Talkly_ZegoEngine", "[CALLER_DIAGNOSTIC] Video call button tapped, passing CallType.VIDEO")
                                                    onStartCall(member, CallType.VIDEO)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = "Video call",
                                                tint = MintAccent,
                                                modifier = Modifier.size(14.dp)
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

        // Section Title: Recent Calls
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Calls",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (callLogs.isNotEmpty()) {
                    Text(
                        text = "${callLogs.size} total",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (callLogs.isEmpty()) {
            item {
                TalklyEmptyState(
                    title = "No call history yet",
                    subtitle = "Place high quality voice and HD video calls with your family and contacts.",
                    buttonText = "Start a Call",
                    onAction = {}
                )
            }
        } else {
            items(callLogs, key = { "tab_call_${it.id}" }) { log ->
                val targetMember = familyMembers.firstOrNull { it.id == log.memberId }
                val (directionIcon, directionColor) = when (log.direction) {
                    CallDirection.INCOMING -> Icons.Default.CallReceived to ElectricCyan
                    CallDirection.OUTGOING -> Icons.Default.CallMade to MintAccent
                    CallDirection.MISSED -> Icons.Default.CallMissed to ErrorColor
                }

                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (log.direction == CallDirection.MISSED) ErrorColor.copy(alpha = 0.25f) else BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderElevated, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (targetMember?.avatarUrl?.isNotBlank() == true) {
                                    val mediaModel = remember(targetMember.avatarUrl) {
                                        com.family.talkly.util.PhoneUtils.getCoilMediaModel(targetMember.avatarUrl)
                                    }
                                    AsyncImage(
                                        model = mediaModel,
                                        contentDescription = log.memberName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = log.memberName.take(2).uppercase(),
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(directionColor)
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = directionIcon,
                                    contentDescription = null,
                                    tint = Color(0xFF040E14),
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.memberName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (log.callType == CallType.VIDEO) "Video call" else "Voice call",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (log.direction == CallDirection.MISSED) ErrorColor else TextSecondary
                                )
                                Text(
                                    text = " • ",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = log.formattedTime,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                if (log.durationSeconds > 0) {
                                    Text(
                                        text = " (${log.formattedDuration})",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        if (targetMember != null) {
                            IconButton(
                                onClick = { onStartCall(targetMember, log.callType) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated)
                            ) {
                                Icon(
                                    imageVector = if (log.callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Redial",
                                    tint = ElectricCyan,
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

// ==========================================
// TAB 3: CONTACTS TAB
// ==========================================
@Composable
private fun ContactsTab(
    contacts: List<FamilyMember>,
    blockedUserIds: Set<String>,
    onSelectContact: (FamilyMember) -> Unit,
    onStartChat: (FamilyMember) -> Unit,
    onStartCall: (FamilyMember, CallType) -> Unit,
    onUnblockUser: (String) -> Unit,
    onAddNewContact: () -> Unit
) {
    var contactSearchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, contactSearchQuery) {
        if (contactSearchQuery.isBlank()) {
            contacts
        } else {
            val q = contactSearchQuery.trim().lowercase()
            contacts.filter {
                it.name.lowercase().contains(q) ||
                    it.phone.contains(q) ||
                    it.relation.lowercase().contains(q)
            }
        }
    }

    val activeContacts = remember(contacts, blockedUserIds) {
        contacts.filter { it.isRecentlyActive() && !blockedUserIds.contains(it.id) }
    }

    val groupedContacts = remember(filteredContacts) {
        filteredContacts
            .sortedBy { it.name.lowercase() }
            .groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search Bar
        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                OutlinedTextField(
                    value = contactSearchQuery,
                    onValueChange = { contactSearchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search people...",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (contactSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { contactSearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Quick Actions Row
        item {
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddNewContact() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color(0xFF040E14),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Contact",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Search phone number on Talkly",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Active Contacts Carousel
        if (contactSearchQuery.isBlank() && activeContacts.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Circle",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${activeContacts.size} online",
                            fontSize = 11.sp,
                            color = SuccessColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(activeContacts, key = { "active_tab_${it.id}" }) { member ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(60.dp)
                                    .clickable { onSelectContact(member) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Brush.sweepGradient(listOf(ElectricCyan, MintAccent, DeepAqua, ElectricCyan)))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(SurfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (member.avatarUrl?.isNotBlank() == true) {
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
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(SuccessColor)
                                            .border(2.dp, SurfaceCard, CircleShape)
                                            .align(Alignment.BottomEnd)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = member.name.split(" ").firstOrNull() ?: member.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredContacts.isEmpty()) {
            item {
                TalklyEmptyState(
                    title = if (contactSearchQuery.isNotBlank()) "No people match search" else "No people yet",
                    subtitle = if (contactSearchQuery.isNotBlank()) "Try checking the spelling or phone number." else "Add people to start calling and messaging.",
                    buttonText = "Add Contact",
                    onAction = onAddNewContact
                )
            }
        } else {
            groupedContacts.forEach { (letter, contactsInLetter) ->
                item(key = "group_hdr_$letter") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(BorderSubtle)
                        )
                    }
                }

                items(contactsInLetter, key = { it.id }) { member ->
                    val isBlocked = blockedUserIds.contains(member.id)

                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isBlocked) ErrorColor.copy(alpha = 0.4f) else BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectContact(member) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                if (member.avatarUrl?.isNotBlank() == true) {
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
                                        fontSize = 16.sp
                                    )
                                }

                                if (member.isRecentlyActive() && !isBlocked) {
                                    OnlinePresenceIndicator(
                                        member = member,
                                        size = 11.dp,
                                        borderColor = SurfaceCard,
                                        borderWidth = 2.dp,
                                        greenColor = SuccessColor,
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = member.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isBlocked) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Blocked",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ErrorColor
                                        )
                                    } else if (member.relation.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(ElectricCyan.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = member.relation,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = ElectricCyan
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (member.phone.isNotBlank()) member.phone else member.status,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isBlocked) {
                                TextButton(
                                    onClick = { onUnblockUser(member.id) }
                                ) {
                                    Text("Unblock", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onStartChat(member) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = "Chat",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onStartCall(member, CallType.AUDIO) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Call",
                                            tint = TextSecondary,
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

// ==========================================
// HELPER: GET MESSAGES
// ==========================================
private fun getMemberMessages(member: FamilyMember, messagesMap: Map<String, List<ChatMessage>>): List<ChatMessage> {
    val msgsById = messagesMap[member.id]
    if (!msgsById.isNullOrEmpty()) return msgsById

    val targetFirebaseUid = member.firebaseUid
    if (!targetFirebaseUid.isNullOrBlank()) {
        val msgsByUid = messagesMap[targetFirebaseUid]
        if (!msgsByUid.isNullOrEmpty()) return msgsByUid
    }

    val suffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
    if (suffix.isNotBlank()) {
        val msgsBySuffix = messagesMap[suffix]
        if (!msgsBySuffix.isNullOrEmpty()) return msgsBySuffix
    }

    if (member.phone.isNotBlank()) {
        val msgsByPhone = messagesMap[member.phone]
        if (!msgsByPhone.isNullOrEmpty()) return msgsByPhone
    }

    return emptyList()
}
