@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.family.talkly.ui.screens

import com.talkly.designui.chat.ChatScreen
import com.talkly.designui.chat.ChatCallbacks
import com.talkly.designui.chat.TalklyUiMapper
import com.talkly.designui.chat.rememberChatUiState
import com.talkly.designui.chat.rememberComposerState
import com.talkly.designui.chat.PlaybackState
import com.talkly.designui.chat.MediaItem
import com.talkly.designui.chat.MsgKind
import com.talkly.designui.components.ArtBox
import com.talkly.designui.theme.TalklyTheme
import com.talkly.designui.theme.ChatPalettes
import com.talkly.designui.gallery.GalleryItem
import com.talkly.designui.gallery.GalleryPicker
import com.talkly.designui.viewer.MediaViewer
import com.talkly.designui.viewer.AudioPlayerScreen
import com.talkly.designui.profile.ProfileScreen
import com.talkly.designui.profile.ProfileActions
import com.talkly.designui.profile.presetPalette

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.MessageRequest
import com.family.talkly.data.models.MessageType
import androidx.compose.foundation.layout.navigationBarsPadding
import com.family.talkly.data.models.ReactionUtils
import com.family.talkly.data.models.UserProfile
import androidx.compose.material.icons.automirrored.filled.Forward
import com.family.talkly.ui.components.AudioPlayerItem
import com.family.talkly.ui.components.ContactProfileDetailsDialog
import com.family.talkly.ui.components.ForwardMessageRecipientDialog
import com.family.talkly.ui.components.FullMediaViewerDialog
import com.family.talkly.ui.components.ActiveCallHeaderControl
import com.family.talkly.data.zego.CurrentCallInfo
import com.family.talkly.data.zego.CallState
import com.family.talkly.ui.components.MediaAttachmentDialog
import com.family.talkly.ui.components.MediaGroupCluster
import com.family.talkly.ui.components.MediaMessageItem
import com.family.talkly.ui.components.MessageLoadingState
import com.family.talkly.ui.components.OnlinePresenceIndicator
import com.family.talkly.ui.components.DisintegrateBox
import com.family.talkly.ui.components.rememberDisintegrateManager
import com.family.talkly.ui.components.WallpaperSelectionDialog
import com.family.talkly.ui.components.UrlAwareMessageText
import com.family.talkly.ui.components.openUrlSafely
import com.family.talkly.util.AudioRecorder
import com.family.talkly.util.MediaCompressorAndUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

sealed interface ChatUiItem {
    val id: String
    val timestamp: Long
    val primaryMessage: ChatMessage

    data class SingleMessage(val message: ChatMessage) : ChatUiItem {
        override val id: String get() = message.id
        override val timestamp: Long get() = message.timestamp
        override val primaryMessage: ChatMessage get() = message
    }

    data class MediaCluster(val messages: List<ChatMessage>) : ChatUiItem {
        override val id: String get() = "cluster_${messages.firstOrNull()?.id ?: ""}"
        override val timestamp: Long get() = messages.last().timestamp
        override val primaryMessage: ChatMessage get() = messages.last()
    }
}

// TALKLY COLOR SYSTEM
private val TalklyChatBg = Color(0xFF080B10)
private val TalklySurface = Color(0xFF11161D)
private val TalklyCard = Color(0xFF18212B)
private val TalklyElevated = Color(0xFF202B36)
private val TalklyCyan = Color(0xFF22D3EE)
private val TalklyAqua = Color(0xFF0EA5A4)
private val TalklyMint = Color(0xFF5EEAD4)
private val TalklyTextPrimary = Color(0xFFF8FAFC)
private val TalklyTextSecondary = Color(0xFFA7B0BA)
private val TalklyError = Color(0xFFF43F5E)
private val TalklySuccess = Color(0xFF22C55E)

// Sent bubble gradient
private val SentBubbleGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF0C3848), Color(0xFF114E5E))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    member: FamilyMember,
    messages: List<ChatMessage>,
    simulatedTimeOffsetMs: Long,
    isLoadingMessages: Boolean = false,
    onBack: () -> Unit,
    onSendMessage: (
        textContent: String,
        type: MessageType,
        mediaUrl: String?,
        replyToId: String?,
        replyToName: String?,
        replyToText: String?
    ) -> Unit,
    onToggleReaction: (messageId: String, reactionEmoji: String) -> Unit = { _, _ -> },
    onDeleteForYou: (messageId: String) -> Unit = {},
    onDeleteForEveryone: (messageId: String) -> Boolean = { false },
    onDeleteMessagesForYou: ((messageIds: Set<String>) -> Unit)? = null,
    onDeleteMessagesForEveryone: ((messageIds: Set<String>) -> Int)? = null,
    onEditMessage: (messageId: String, newText: String) -> Boolean = { _, _ -> false },
    onToggleStarMessage: (messageId: String) -> Unit = {},
    onTogglePinMessage: (messageId: String) -> Boolean = { false },
    onTogglePinMember: () -> Unit = {},
    onTypingStateChanged: (Boolean) -> Unit,
    onToggleFastForward: () -> Unit,
    onAddExpiredDemo: () -> Unit,
    onStartCall: (CallType) -> Unit,
    onReadMessages: () -> Unit = {},
    isInitiallyBlocked: Boolean = false,
    onBlockUser: (() -> Unit)? = null,
    onUnblockUser: (() -> Unit)? = null,
    isMutualContact: Boolean = true,
    pendingMessageRequest: MessageRequest? = null,
    isRequestSentByMe: Boolean = false,
    onSendMessageRequest: (initialText: String) -> Unit = {},
    onAcceptMessageRequest: (request: MessageRequest) -> Unit = {},
    onDeclineMessageRequest: (requestId: String) -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    currentUserProfile: UserProfile? = null,
    isNetworkConnected: Boolean = true,
    onRefreshMemberProfile: (() -> Unit)? = null,
    activeCallInfo: CurrentCallInfo? = null,
    onRestoreCall: (() -> Unit)? = null,
    onEndCall: (() -> Unit)? = null,
    allFamilyMembers: List<FamilyMember> = emptyList()
) {
    LaunchedEffect(member.id) {
        onRefreshMemberProfile?.invoke()
    }

    var textInput by remember { mutableStateOf("") }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var fullMediaViewerMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var reactionDialogMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var reactionDetailsMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showContactProfile by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showMediaLinksDocs by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStarredMessagesDialog by remember { mutableStateOf(false) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var isForwardingMessages by remember { mutableStateOf(false) }
    var forwardProgressText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioRecorder = remember { AudioRecorder(context) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var currentAudioFile by remember { mutableStateOf<File?>(null) }
    var isPreviewingVoiceNote by remember { mutableStateOf(false) }
    var localPendingMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    var showMenu by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isBlocked by remember(isInitiallyBlocked) { mutableStateOf(isInitiallyBlocked) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showClearChatConfirmDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by rememberSaveable { mutableStateOf(false) }
    var localClearedMessages by remember { mutableStateOf(false) }
    var chatWindowScreenHeight by remember { mutableFloatStateOf(1000f) }
    var selectedMsgIsTopHalf by remember { mutableStateOf(false) }

    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedMessageIds.isNotEmpty()
    val dissolveManager = rememberDisintegrateManager()

    var viewerMedia by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var showGalleryPicker by remember { mutableStateOf(false) }

    // Intercept back presses to close overlays or go back to chat list
    BackHandler(enabled = true) {
        when {
            viewerMedia != null -> viewerMedia = null
            showGalleryPicker -> showGalleryPicker = false
            showThemePicker -> showThemePicker = false
            showMediaLinksDocs -> showMediaLinksDocs = false
            showForwardDialog -> if (!isForwardingMessages) showForwardDialog = false
            showBulkDeleteDialog -> showBulkDeleteDialog = false
            selectedMessageIds.isNotEmpty() -> selectedMessageIds = emptySet()
            fullMediaViewerMessage != null -> fullMediaViewerMessage = null
            reactionDialogMessage != null -> reactionDialogMessage = null
            reactionDetailsMessage != null -> reactionDetailsMessage = null
            showContactProfile -> showContactProfile = false
            showStarredMessagesDialog -> showStarredMessagesDialog = false
            showWallpaperDialog -> showWallpaperDialog = false
            showBlockConfirmDialog -> showBlockConfirmDialog = false
            showClearChatConfirmDialog -> showClearChatConfirmDialog = false
            showAttachmentDialog -> showAttachmentDialog = false
            showMenu -> showMenu = false
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
            }
            editingMessage != null -> {
                editingMessage = null
                textInput = ""
            }
            replyingToMessage != null -> replyingToMessage = null
            else -> onBack()
        }
    }

    // Recording timer loop
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSec++
            }
        }
    }

    // Auto debounce typing state
    LaunchedEffect(textInput) {
        if (textInput.isNotBlank()) {
            onTypingStateChanged(true)
            delay(1500L)
            onTypingStateChanged(false)
        } else {
            onTypingStateChanged(false)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = audioRecorder.startRecording()
            if (file != null) {
                currentAudioFile = file
                isRecording = true
            } else {
                Toast.makeText(context, "Could not start audio recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceRecording() {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val file = audioRecorder.startRecording()
            if (file != null) {
                currentAudioFile = file
                isRecording = true
            } else {
                Toast.makeText(context, "Could not start audio recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val prefs = remember(context) { context.getSharedPreferences("talkly_prefs", Context.MODE_PRIVATE) }
    var wallpaperValue by remember(member.id) {
        mutableStateOf(
            prefs.getString("wallpaper_${member.id}", null)
                ?: prefs.getString("wallpaper_global", "#080B10")
                ?: "#080B10"
        )
    }
    val chatRepo = remember(context) { com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context) }
    var conversationId by rememberSaveable(member.id) {
        val cached = prefs.getString("conv_id_${member.id}", null)
        mutableStateOf<String?>(cached)
    }

    // Fetch shared conversation wallpaper on enter
    LaunchedEffect(member.id, member.firebaseUid, member.phone) {
        withContext(Dispatchers.IO) {
            val resolvedConvId = chatRepo.getOrCreateConversationIdForMember(
                memberId = member.id,
                memberFirebaseUid = member.firebaseUid,
                memberPhone = member.phone
            )
            if (!resolvedConvId.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    conversationId = resolvedConvId
                    prefs.edit().putString("conv_id_${member.id}", resolvedConvId).apply()
                }
                val remoteWp = chatRepo.fetchConversationWallpaper(resolvedConvId)
                if (!remoteWp.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        if (wallpaperValue != remoteWp) {
                            wallpaperValue = remoteWp
                            prefs.edit().putString("wallpaper_${member.id}", remoteWp).apply()
                        }
                    }
                }
            }
        }
    }

    // Subscribe to Realtime wallpaper updates for this conversation
    androidx.compose.runtime.DisposableEffect(conversationId) {
        val targetConvId = conversationId
        var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
        val subJob = if (!targetConvId.isNullOrBlank()) {
            scope.launch(Dispatchers.IO) {
                realtimeChannel = com.family.talkly.data.supabase.SupabaseMessagingService.subscribeToConversationWallpaper(
                    conversationId = targetConvId,
                    coroutineScope = this,
                    onWallpaperUpdate = { newWp ->
                        scope.launch(Dispatchers.Main) {
                            if (newWp.isNotBlank() && wallpaperValue != newWp) {
                                wallpaperValue = newWp
                                prefs.edit().putString("wallpaper_${member.id}", newWp).apply()
                            }
                        }
                    }
                )
            }
        } else null

        onDispose {
            subJob?.cancel()
            val ch = realtimeChannel
            if (ch != null) {
                scope.launch(Dispatchers.IO) {
                    com.family.talkly.data.supabase.SupabaseMessagingService.unsubscribeChannel(ch)
                }
            }
        }
    }

    val activeMessages = if (localClearedMessages) emptyList() else messages

    val combinedMessages = remember(activeMessages, localPendingMessages, dissolveManager.dissolvingCount) {
        val serverIds = activeMessages.map { it.id }.toSet()
        val retainedDissolving = dissolveManager.getDissolvingMessages().filter { it.id !in serverIds }
        (activeMessages + localPendingMessages.filter { it.id !in serverIds } + retainedDissolving)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    val displayedMessages = remember(combinedMessages, isSearchActive, searchQuery) {
        if (isSearchActive && searchQuery.isNotBlank()) {
            combinedMessages.filter {
                it.textContent.contains(searchQuery, ignoreCase = true)
            }
        } else {
            combinedMessages
        }
    }

    LaunchedEffect(combinedMessages) {
        if (selectedMessageIds.isNotEmpty()) {
            val currentValidIds = combinedMessages
                .filter { !it.isDeletedForEveryone }
                .map { it.id }
                .toSet()
            val filtered = selectedMessageIds.filter { it in currentValidIds }.toSet()
            if (filtered.size != selectedMessageIds.size) {
                selectedMessageIds = filtered
            }
        }
    }

    val toggleMessageSelection: (ChatMessage) -> Unit = { targetMsg ->
        if (!targetMsg.isDeletedForEveryone) {
            selectedMessageIds = if (selectedMessageIds.contains(targetMsg.id)) {
                selectedMessageIds - targetMsg.id
            } else {
                selectedMessageIds + targetMsg.id
            }
        }
    }

    val onMessageLongPress: (ChatMessage) -> Unit = { targetMsg ->
        if (isSelectionMode) {
            toggleMessageSelection(targetMsg)
        } else {
            reactionDialogMessage = targetMsg
        }
    }

    var lastStableUiItems by remember { mutableStateOf<List<ChatUiItem>>(emptyList()) }

    val uiItems = remember(displayedMessages, simulatedTimeOffsetMs, member.id, member.phone, member.firebaseUid) {
        val items = mutableListOf<ChatUiItem>()
        var i = 0
        val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)

        fun isMsgFromMember(m: ChatMessage): Boolean {
            val sSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(m.senderId)
            return (m.senderId == member.id) ||
                    (!member.firebaseUid.isNullOrBlank() && m.senderId == member.firebaseUid) ||
                    (member.phone.isNotBlank() && m.senderId == member.phone) ||
                    (memberSuffix.isNotBlank() && memberSuffix == sSuffix)
        }

        while (i < displayedMessages.size) {
            val msg = displayedMessages[i]
            val isVisualMedia = (msg.messageType == MessageType.IMAGE || msg.messageType == MessageType.VIDEO) &&
                    !msg.isDeletedForEveryone &&
                    (msg.mediaUrl != null || msg.isMediaExpired(simulatedTimeOffsetMs))

            if (isVisualMedia) {
                val group = mutableListOf(msg)
                var j = i + 1
                while (j < displayedMessages.size) {
                    val nextMsg = displayedMessages[j]
                    val isNextVisualMedia = (nextMsg.messageType == MessageType.IMAGE || nextMsg.messageType == MessageType.VIDEO) &&
                            !nextMsg.isDeletedForEveryone &&
                            (nextMsg.mediaUrl != null || nextMsg.isMediaExpired(simulatedTimeOffsetMs))

                    val sameSender = (msg.senderId == nextMsg.senderId) || (isMsgFromMember(msg) == isMsgFromMember(nextMsg))
                    val timeDiff = kotlin.math.abs(nextMsg.timestamp - group.last().timestamp)

                    if (isNextVisualMedia &&
                        sameSender &&
                        nextMsg.replyToSenderName == null &&
                        msg.replyToSenderName == null &&
                        timeDiff <= 120_000L
                    ) {
                        group.add(nextMsg)
                        j++
                    } else {
                        break
                    }
                }
                if (group.size > 1) {
                    items.add(ChatUiItem.MediaCluster(group))
                    i = j
                } else {
                    items.add(ChatUiItem.SingleMessage(msg))
                    i++
                }
            } else {
                items.add(ChatUiItem.SingleMessage(msg))
                i++
            }
        }

        val previousSize = lastStableUiItems.size
        val newSize = items.size

        val result = if (previousSize > 5 && newSize < previousSize / 2) {
            // সাময়িক/সন্দেহজনক ড্রপ - পুরনো stable list-ই ধরে রাখো
            Log.w("Talkly_UI_GUARD", "Suspicious drop detected: previous=$previousSize, new=$newSize. Ignoring this recompute, keeping stable list.")
            lastStableUiItems
        } else {
            lastStableUiItems = items
            items
        }

        Log.e("Talkly_UI_ITEMS_DEBUG", "uiItems recomputed: size=${result.size}, ids=${result.map { it.id }}")
        result
    }

    val pinnedMessage = remember(combinedMessages) {
        combinedMessages.lastOrNull { it.isPinned }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberSaveable(member.id, saver = LazyListState.Saver) { LazyListState() }
    val nearBottomThresholdPx = with(LocalDensity.current) { 140.dp.roundToPx() }
    val isUserAtBottom by remember {
        derivedStateOf {
            isUserNearBottom(listState.layoutInfo, nearBottomThresholdPx)
        }
    }
    var ownSendPendingScroll by remember(member.id) { mutableStateOf(false) }
    var wasNearBottomBeforeIme by remember(member.id) { mutableStateOf(true) }

    fun sendPendingMediaMessage(
        textContent: String,
        messageType: MessageType,
        localMediaUrl: String?
    ) {
        if (localMediaUrl.isNullOrBlank()) return
        if (isUserAtBottom || wasNearBottomBeforeIme) {
            ownSendPendingScroll = true
        }
        val tempId = java.util.UUID.randomUUID().toString()
        val replyId = replyingToMessage?.id
        val replyName = replyingToMessage?.senderName
        val replyText = replyingToMessage?.textContent?.ifEmpty { "Media/Voice Message" }

        replyingToMessage = null

        val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context)
        val canonicalId = chatRepo.getCanonicalMemberId(member.id)

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val currentUid = sessionPrefs.getString("user_uid", null)
        val currentName = sessionPrefs.getString("user_name", null)

        com.family.talkly.util.MediaUploadManager.enqueueMediaUpload(
            context = context,
            messageId = tempId,
            chatKey = canonicalId,
            recipientId = member.id,
            messageType = messageType,
            localMediaUrl = localMediaUrl,
            textContent = textContent,
            senderUid = currentUid,
            senderName = currentName,
            replyToId = replyId,
            replyToName = replyName,
            replyToText = replyText
        )
    }

    fun stopAndPreparePreview() {
        val file = audioRecorder.stopRecording()
        isRecording = false
        if (file == null || !file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Voice recording was empty", Toast.LENGTH_SHORT).show()
            isPreviewingVoiceNote = false
            return
        }
        currentAudioFile = file
        isPreviewingVoiceNote = true
    }

    fun sendPreviewedVoiceNote() {
        val file = currentAudioFile ?: run {
            isPreviewingVoiceNote = false
            return
        }
        isPreviewingVoiceNote = false
        val durationText = "${recordingDurationSec}s"
        val localFilePath = file.absolutePath

        sendPendingMediaMessage(
            textContent = "Voice Message ($durationText)",
            messageType = MessageType.VOICE_NOTE,
            localMediaUrl = localFilePath
        )

        currentAudioFile = null
        recordingDurationSec = 0
    }

    fun cancelVoicePreview() {
        audioRecorder.cancelRecording()
        isPreviewingVoiceNote = false
        isRecording = false
        currentAudioFile = null
        recordingDurationSec = 0
        Toast.makeText(context, "Recording cancelled", Toast.LENGTH_SHORT).show()
    }

    // Mark active chat in notification helper & clear notifications on enter/exit with lifecycle tracking
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(member.id, lifecycleOwner) {
        com.family.talkly.util.TalklyNotificationHelper.setActiveChat(
            memberId = member.id,
            firebaseUid = member.firebaseUid,
            phone = member.phone,
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        )
        com.family.talkly.util.TalklyNotificationHelper.cancelNotificationsForChat(context, member.id)
        onReadMessages()

        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    com.family.talkly.util.TalklyNotificationHelper.updateActiveChatLifecycle(true)
                    com.family.talkly.util.TalklyNotificationHelper.cancelNotificationsForChat(context, member.id)
                    onReadMessages()
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    com.family.talkly.util.TalklyNotificationHelper.updateActiveChatLifecycle(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            com.family.talkly.util.TalklyNotificationHelper.clearActiveChat(member.id)
        }
    }

    // --- SAFE DETERMINISTIC SCROLL ENGINE ---
    var hasPositionedInitially by rememberSaveable(member.id) { mutableStateOf(false) }
    var knownMessageIds by remember(member.id) { mutableStateOf<Set<String>>(emptySet()) }
    var knownFinalMediaIds by remember(member.id) { mutableStateOf<Set<String>>(emptySet()) }
    var unreadCountWhileScrolledUp by remember(member.id) { mutableIntStateOf(0) }

    val currentMessageIds = remember(combinedMessages) {
        combinedMessages.map { it.id }.toSet()
    }

    val currentFinalMediaIds = remember(combinedMessages) {
        combinedMessages.filter { isFinalMediaAvailable(it) }.map { it.id }.toSet()
    }

    // Clear unread count when user reaches the bottom
    LaunchedEffect(isUserAtBottom) {
        if (isUserAtBottom) {
            unreadCountWhileScrolledUp = 0
            ownSendPendingScroll = false
        }
    }

    // Cancel ownSendPendingScroll if user intentionally scrolled away during touch gesture
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isUserAtBottom) {
            ownSendPendingScroll = false
        }
    }

    // STEP A: Initial chat opening position
    // Suspends until list is actually laid out (totalItemsCount > 0)
    // Uses the real last LazyColumn index from layoutInfo, not an assumption
    LaunchedEffect(member.id) {
        if (!hasPositionedInitially) {
            snapshotFlow {
                val count = listState.layoutInfo.totalItemsCount
                val hasItems = uiItems.isNotEmpty()
                Pair(count, hasItems)
            }.filter { (count, hasItems) ->
                hasItems && count > 0
            }.first()

            if (!isSearchActive) {
                val targetIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                listState.scrollToItem(targetIndex)
            }
            hasPositionedInitially = true
            knownMessageIds = currentMessageIds
            knownFinalMediaIds = currentFinalMediaIds
        } else {
            if (knownMessageIds.isEmpty()) {
                knownMessageIds = currentMessageIds
            }
            if (knownFinalMediaIds.isEmpty()) {
                knownFinalMediaIds = currentFinalMediaIds
            }
        }
    }

    // STEP B, C, D, G: Deterministic New Message & Media Transition Scroll Engine
    // Strictly separates new genuine messages and final media transitions from non-scroll updates
    LaunchedEffect(currentMessageIds, currentFinalMediaIds, isSearchActive) {
        if (!hasPositionedInitially) return@LaunchedEffect
        if (uiItems.isEmpty()) return@LaunchedEffect

        val newIds = currentMessageIds - knownMessageIds
        val newMediaIds = currentFinalMediaIds - knownFinalMediaIds
        val hasNewMessages = newIds.isNotEmpty()
        val hasMediaTransition = newMediaIds.isNotEmpty()

        if (hasNewMessages || hasMediaTransition) {
            val wasNearBottom = isUserAtBottom || ownSendPendingScroll
            ownSendPendingScroll = false
            knownMessageIds = currentMessageIds
            knownFinalMediaIds = currentFinalMediaIds

            if (isSearchActive) {
                return@LaunchedEffect
            }

            if (wasNearBottom) {
                // If user was near bottom or sent this message: smoothly reveal new message / media
                if (!listState.isScrollInProgress) {
                    kotlinx.coroutines.delay(40)
                    val target = maxOf(listState.layoutInfo.totalItemsCount - 1, uiItems.size + 1).coerceAtLeast(0)
                    listState.animateScrollToItem(target)
                }
            } else {
                // User intentionally scrolled upward reading older messages: DO NOT SCROLL
                if (hasNewMessages) {
                    unreadCountWhileScrolledUp += newIds.size
                }
            }
        } else {
            // Existing message updates (read/delivery receipts, reactions, edits, upload progress):
            // NEVER auto-scroll, just update known sets cleanly
            knownMessageIds = currentMessageIds
            knownFinalMediaIds = currentFinalMediaIds
        }
    }

    // STEP E: Typing indicator room adjustment
    // When typing indicator appears and user is near bottom, smoothly make room so latest messages remain visible
    LaunchedEffect(member.isTyping) {
        if (member.isTyping && hasPositionedInitially && !isSearchActive) {
            if (isUserAtBottom && !listState.isScrollInProgress) {
                kotlinx.coroutines.delay(60)
                val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                listState.animateScrollToItem(target)
            }
        }
    }

    // STEP F: Keyboard / IME adjustment
    // When keyboard opens and user is near bottom / composing, smoothly adjust so latest content remains visible
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val isImeOpen = imeBottom > 0
    var wasImeOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isImeOpen, isUserAtBottom) {
        if (!isImeOpen) {
            wasNearBottomBeforeIme = isUserAtBottom
        }
    }

    LaunchedEffect(isImeOpen) {
        if (isImeOpen && !wasImeOpen) {
            if (hasPositionedInitially && wasNearBottomBeforeIme && !isSearchActive && !listState.isScrollInProgress) {
                // Wait for the layout/IME transition to settle before scrolling
                kotlinx.coroutines.delay(140)
                if (!listState.isScrollInProgress) {
                    val target = maxOf(listState.layoutInfo.totalItemsCount - 1, uiItems.size + 1).coerceAtLeast(0)
                    listState.animateScrollToItem(target)
                }
            }
        }
        wasImeOpen = isImeOpen
    }

    // Mark messages as read when opening or receiving new messages in chat screen (debounced to avoid fighting scroll animations)
    LaunchedEffect(member.id, messages.size) {
        kotlinx.coroutines.delay(300)
        onReadMessages()
    }

    // Manual scroll-to-bottom visibility helper (when scrolled up away from bottom)
    val isScrolledUp by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisible < totalItems - 2
        }
    }

    // Attachment Dialog
    if (showAttachmentDialog) {
        MediaAttachmentDialog(
            onDismiss = { showAttachmentDialog = false },
            onSendMediaWithTag = { caption, type, url ->
                sendPendingMediaMessage(caption, type, url)
            },
            onSendExpiredDemo = {
                onAddExpiredDemo()
            }
        )
    }

    // Full Screen Media Viewer Dialog
    fullMediaViewerMessage?.let { mediaMsg ->
        FullMediaViewerDialog(
            message = mediaMsg,
            onDismiss = { fullMediaViewerMessage = null }
        )
    }

    // Reaction & Reply Long-Click Dialog with Talkly styling
    reactionDialogMessage?.let { selectedMsg ->
        var isAnimatedVisible by remember { mutableStateOf(false) }
        LaunchedEffect(selectedMsg) {
            isAnimatedVisible = true
        }

        Dialog(
            onDismissRequest = { reactionDialogMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { reactionDialogMessage = null },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isAnimatedVisible,
                    enter = fadeIn(animationSpec = tween(220)) + scaleIn(
                        initialScale = 0.05f,
                        transformOrigin = if (selectedMsgIsTopHalf) TransformOrigin(0.5f, 0.2f) else TransformOrigin(0.5f, 0.8f),
                        animationSpec = tween(220)
                    ),
                    exit = fadeOut(animationSpec = tween(180)) + scaleOut(
                        targetScale = 0.05f,
                        transformOrigin = if (selectedMsgIsTopHalf) TransformOrigin(0.5f, 0.2f) else TransformOrigin(0.5f, 0.8f),
                        animationSpec = tween(180)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .widthIn(max = 340.dp)
                            .clickable(enabled = false) {},
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. FLOATING REACTION EMOJIS (ONLY FOR NON-DELETED MESSAGES)
                        if (!selectedMsg.isDeletedForEveryone) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = TalklyCard,
                                border = BorderStroke(1.dp, TalklyCyan.copy(alpha = 0.35f)),
                                shadowElevation = 12.dp,
                                modifier = Modifier
                                    .padding(bottom = 14.dp)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("❤️", "🔥", "😂", "👍", "😮", "😭", "🥰", "👏", "🎉", "💯", "✨", "💙").forEach { emoji ->
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selectedMsg.reaction == emoji) TalklyCyan.copy(alpha = 0.25f) else Color.Transparent
                                                )
                                                .clickable {
                                                    onToggleReaction(selectedMsg.id, emoji)
                                                    reactionDialogMessage = null
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = emoji, fontSize = 24.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. MAIN POPUP CONTAINER (Talkly Surface)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = TalklySurface,
                            border = BorderStroke(1.dp, TalklyElevated),
                            shadowElevation = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // TOP ROW: Reply, Star, Pin (Only for non-deleted messages)
                                if (!selectedMsg.isDeletedForEveryone) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Reply
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = TalklyCyan.copy(alpha = 0.15f),
                                            border = BorderStroke(0.5.dp, TalklyCyan.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    replyingToMessage = selectedMsg
                                                    reactionDialogMessage = null
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Reply",
                                                    color = TalklyCyan,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }

                                        // Star / Unstar
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFFD54F).copy(alpha = 0.15f),
                                            border = BorderStroke(0.5.dp, Color(0xFFFFD54F).copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    onToggleStarMessage(selectedMsg.id)
                                                    reactionDialogMessage = null
                                                    Toast.makeText(
                                                        context,
                                                        if (selectedMsg.isStarred) "Unstarred message" else "Starred message",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (selectedMsg.isStarred) "Unstar" else "Star ⭐",
                                                    color = Color(0xFFFFC107),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }

                                        // Pin / Unpin
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = TalklyMint.copy(alpha = 0.15f),
                                            border = BorderStroke(0.5.dp, TalklyMint.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    val pinResult = onTogglePinMessage(selectedMsg.id)
                                                    reactionDialogMessage = null
                                                    if (pinResult) {
                                                        Toast.makeText(
                                                            context,
                                                            if (selectedMsg.isPinned) "Unpinned message" else "Pinned to top 📌",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Only the person who pinned this message can unpin it",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (selectedMsg.isPinned) "Unpin" else "Pin 📌",
                                                    color = TalklyMint,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                val currentUid = currentUserProfile?.uid ?: "self"
                                val currentPhone = currentUserProfile?.phoneNumber ?: ""
                                val currentPhoneSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)
                                val selectedSenderSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(selectedMsg.senderId)
                                val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
                                val isMemberSender = (selectedMsg.senderId == member.id) ||
                                        (!member.firebaseUid.isNullOrBlank() && selectedMsg.senderId == member.firebaseUid) ||
                                        (member.phone.isNotBlank() && selectedMsg.senderId == member.phone) ||
                                        (memberSuffix.isNotBlank() && memberSuffix == selectedSenderSuffix)

                                val isSelfMsg = !isMemberSender ||
                                        selectedMsg.senderId == "self" ||
                                        selectedMsg.senderId == currentUid ||
                                        selectedMsg.senderName.contains("You", ignoreCase = true) ||
                                        (currentPhoneSuffix.isNotBlank() && currentPhoneSuffix == selectedSenderSuffix)

                                val isWithin10Mins = (System.currentTimeMillis() - selectedMsg.timestamp) <= 10 * 60 * 1000L

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Edit message (only if sender and within 10 mins)
                                    if (isSelfMsg && isWithin10Mins && selectedMsg.messageType == MessageType.TEXT && !selectedMsg.isDeletedForEveryone) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = TalklyCyan.copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, TalklyCyan.copy(alpha = 0.25f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    editingMessage = selectedMsg
                                                    textInput = selectedMsg.textContent
                                                    reactionDialogMessage = null
                                                    Toast.makeText(context, "Editing message ✏️", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    tint = TalklyCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Edit message",
                                                    color = TalklyCyan,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    // Select message
                                    if (!selectedMsg.isDeletedForEveryone) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = TalklyCyan.copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, TalklyCyan.copy(alpha = 0.25f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedMessageIds = setOf(selectedMsg.id)
                                                    reactionDialogMessage = null
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Done,
                                                    contentDescription = null,
                                                    tint = TalklyCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Select message",
                                                    color = TalklyCyan,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    // Delete for me
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = TalklyError.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, TalklyError.copy(alpha = 0.25f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val msg = selectedMsg
                                                val msgId = msg.id
                                                reactionDialogMessage = null
                                                dissolveManager.startDissolve(listOf(msg))
                                                onDeleteForYou(msgId)
                                                Toast.makeText(context, "Deleted for you", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = TalklyError,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Delete for me",
                                                color = TalklyError,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    // Delete for everyone
                                    if (isSelfMsg && !selectedMsg.isDeletedForEveryone) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = TalklyError.copy(alpha = 0.18f),
                                            border = BorderStroke(0.5.dp, TalklyError.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val msg = selectedMsg
                                                    val msgId = msg.id
                                                    reactionDialogMessage = null
                                                    dissolveManager.startDissolve(listOf(msg))
                                                    val success = onDeleteForEveryone(msgId)
                                                    if (success) {
                                                        Toast.makeText(context, "Deleted for everyone", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Could not delete for everyone", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = TalklyError,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Delete for everyone",
                                                    color = TalklyError,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Cancel button
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    TextButton(onClick = { reactionDialogMessage = null }) {
                                        Text(
                                            text = "Cancel",
                                            color = TalklyTextSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
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

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteDialog && selectedMessageIds.isNotEmpty()) {
        val currentUid = currentUserProfile?.uid.orEmpty()
        val currentPhone = currentUserProfile?.phoneNumber.orEmpty()
        val currentPhoneSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)
        val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)

        fun isOwnMessage(m: ChatMessage): Boolean {
            val senderSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(m.senderId)
            val isMember = (m.senderId == member.id) ||
                    (!member.firebaseUid.isNullOrBlank() && m.senderId == member.firebaseUid) ||
                    (member.phone.isNotBlank() && m.senderId == member.phone) ||
                    (memberSuffix.isNotBlank() && memberSuffix == senderSuffix)
            return !isMember ||
                    m.senderId == "self" ||
                    (currentUid.isNotBlank() && m.senderId == currentUid) ||
                    m.senderName.contains("You", ignoreCase = true) ||
                    (currentPhoneSuffix.isNotBlank() && currentPhoneSuffix == senderSuffix)
        }

        val currentSelectedMsgs = combinedMessages.filter { it.id in selectedMessageIds }
        val currentOwnMsgs = currentSelectedMsgs.filter {
            isOwnMessage(it) && !it.isDeletedForEveryone
        }
        val hasOwn = currentOwnMsgs.isNotEmpty()
        val isAllOwn = currentSelectedMsgs.isNotEmpty() && currentSelectedMsgs.size == currentOwnMsgs.size
        val isMixed = currentSelectedMsgs.isNotEmpty() && currentOwnMsgs.isNotEmpty() && !isAllOwn
        val totalCount = currentSelectedMsgs.size
        val ownCount = currentOwnMsgs.size

        Dialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showBulkDeleteDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = TalklySurface,
                    border = BorderStroke(1.dp, TalklyElevated),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 340.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (totalCount == 1) "Delete message?" else "Delete $totalCount messages?",
                            color = TalklyTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        if (isMixed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Delete for me will remove all $totalCount selected messages. Delete for everyone will apply only to your $ownCount sent messages.",
                                color = TalklyTextSecondary,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Delete for me
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = TalklyError.copy(alpha = 0.12f),
                                border = BorderStroke(0.5.dp, TalklyError.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val idsToDelete = selectedMessageIds.toSet()
                                        val msgsToDelete = combinedMessages.filter { it.id in idsToDelete }
                                        showBulkDeleteDialog = false
                                        selectedMessageIds = emptySet()
                                        dissolveManager.startDissolve(msgsToDelete)
                                        if (onDeleteMessagesForYou != null) {
                                            onDeleteMessagesForYou(idsToDelete)
                                        } else {
                                            idsToDelete.forEach { onDeleteForYou(it) }
                                        }
                                        Toast.makeText(
                                            context,
                                            if (idsToDelete.size == 1) "Deleted for you" else "Deleted ${idsToDelete.size} messages for you",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = TalklyError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (totalCount == 1) "Delete for me" else "Delete for me ($totalCount)",
                                        color = TalklyError,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Delete for everyone
                            if (hasOwn) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = TalklyError.copy(alpha = 0.18f),
                                    border = BorderStroke(0.5.dp, TalklyError.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val ownIdsToDelete = currentOwnMsgs.map { it.id }.toSet()
                                            val ownMsgsToDelete = currentOwnMsgs.filter { it.id in ownIdsToDelete }
                                            showBulkDeleteDialog = false
                                            selectedMessageIds = emptySet()
                                            dissolveManager.startDissolve(ownMsgsToDelete)
                                            if (onDeleteMessagesForEveryone != null) {
                                                onDeleteMessagesForEveryone(ownIdsToDelete)
                                            } else {
                                                ownIdsToDelete.forEach { onDeleteForEveryone(it) }
                                            }
                                            Toast.makeText(
                                                context,
                                                if (ownIdsToDelete.size == 1) "Deleted for everyone" else "Deleted ${ownIdsToDelete.size} messages for everyone",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = TalklyError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isAllOwn) {
                                                if (totalCount == 1) "Delete for everyone" else "Delete for everyone ($totalCount)"
                                            } else {
                                                "Delete for everyone ($ownCount)"
                                            },
                                            color = TalklyError,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            TextButton(onClick = { showBulkDeleteDialog = false }) {
                                Text(
                                    text = "Cancel",
                                    color = TalklyTextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Forward Message Recipient Dialog
    if (showForwardDialog && selectedMessageIds.isNotEmpty()) {
        val selectedList = combinedMessages.filter { it.id in selectedMessageIds }
        ForwardMessageRecipientDialog(
            allMembers = if (allFamilyMembers.isNotEmpty()) allFamilyMembers else listOf(member),
            messageCount = selectedList.size,
            isForwarding = isForwardingMessages,
            progressText = forwardProgressText,
            onDismiss = { showForwardDialog = false },
            onConfirmForward = { recipientIds ->
                if (recipientIds.isEmpty()) return@ForwardMessageRecipientDialog
                isForwardingMessages = true
                forwardProgressText = "Forwarding ${selectedList.size} message(s)..."
                val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context)
                scope.launch {
                    try {
                        val (success, failed) = chatRepo.forwardMessages(
                            messagesToForward = selectedList,
                            targetMemberIds = recipientIds,
                            onProgress = { completed, total ->
                                forwardProgressText = "Forwarding $completed of $total..."
                            }
                        )
                        isForwardingMessages = false
                        showForwardDialog = false
                        selectedMessageIds = emptySet()
                        if (success > 0) {
                            val recipientText = if (recipientIds.size == 1) "1 contact" else "${recipientIds.size} contacts"
                            Toast.makeText(context, "Forwarded to $recipientText", Toast.LENGTH_SHORT).show()
                        } else if (failed > 0) {
                            Toast.makeText(context, "Failed to forward messages", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        isForwardingMessages = false
                        showForwardDialog = false
                        Toast.makeText(context, "Error forwarding: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Reaction Details Dialog
    if (reactionDetailsMessage != null) {
        val targetMsg = reactionDetailsMessage!!
        val currentUserId = currentUserProfile?.uid ?: "self"
        val entries = remember(targetMsg.reaction) {
            ReactionUtils.parseReactions(targetMsg.reaction, targetMsg.senderId, targetMsg.senderName, targetMsg.timestamp)
        }

        Dialog(
            onDismissRequest = { reactionDetailsMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = TalklySurface,
                border = BorderStroke(1.dp, TalklyElevated),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reactions (${entries.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TalklyTextPrimary
                        )
                        IconButton(
                            onClick = { reactionDetailsMessage = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TalklyTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    var selectedEmojiFilter by remember { mutableStateOf<String?>(null) }
                    val distinctEmojis = remember(entries) { entries.map { it.emoji }.distinct() }

                    if (distinctEmojis.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedEmojiFilter == null,
                                onClick = { selectedEmojiFilter = null },
                                label = { Text("All ${entries.size}", color = if (selectedEmojiFilter == null) TalklyCyan else TalklyTextSecondary) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TalklyCyan.copy(alpha = 0.2f),
                                    containerColor = TalklyCard
                                ),
                                border = BorderStroke(0.5.dp, if (selectedEmojiFilter == null) TalklyCyan else TalklyElevated)
                            )
                            distinctEmojis.forEach { emoji ->
                                val count = entries.count { it.emoji == emoji }
                                FilterChip(
                                    selected = selectedEmojiFilter == emoji,
                                    onClick = { selectedEmojiFilter = emoji },
                                    label = { Text("$emoji $count", color = if (selectedEmojiFilter == emoji) TalklyCyan else TalklyTextSecondary) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TalklyCyan.copy(alpha = 0.2f),
                                        containerColor = TalklyCard
                                    ),
                                    border = BorderStroke(0.5.dp, if (selectedEmojiFilter == emoji) TalklyCyan else TalklyElevated)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val filteredEntries = remember(entries, selectedEmojiFilter) {
                        if (selectedEmojiFilter == null) entries
                        else entries.filter { it.emoji == selectedEmojiFilter }
                    }

                    if (filteredEntries.isEmpty()) {
                        Text(
                            text = "No reactions",
                            color = TalklyTextSecondary,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredEntries, key = { "${it.userId}_${it.emoji}_${it.timestamp}" }) { entry ->
                                val isCurrentUser = entry.userId == currentUserId || entry.userId == "self" || (currentUserId == "self" && entry.userId == "You")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TalklyCard)
                                        .border(0.5.dp, TalklyElevated, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(TalklyCyan.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!entry.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = entry.avatarUrl,
                                                contentDescription = entry.userName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = entry.userName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = TalklyCyan
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isCurrentUser) "You" else entry.userName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TalklyTextPrimary
                                        )
                                        Text(
                                            text = if (isCurrentUser) "Tap to remove" else entry.formattedTime,
                                            fontSize = 11.sp,
                                            color = if (isCurrentUser) TalklyCyan else TalklyTextSecondary
                                        )
                                    }

                                    Text(
                                        text = entry.emoji,
                                        fontSize = 20.sp
                                    )

                                    if (isCurrentUser) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                onToggleReaction(targetMsg.id, entry.emoji)
                                                reactionDetailsMessage = null
                                                Toast.makeText(context, "Reaction removed", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove reaction",
                                                tint = TalklyError,
                                                modifier = Modifier.size(16.dp)
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


    if (showStarredMessagesDialog) {
        AlertDialog(
            onDismissRequest = { showStarredMessagesDialog = false },
            containerColor = TalklySurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Starred Messages", fontWeight = FontWeight.Bold, color = TalklyTextPrimary)
                }
            },
            text = {
                val starredList = combinedMessages.filter { it.isStarred }
                if (starredList.isEmpty()) {
                    Text(
                        "No starred messages in this chat yet.\n\nLong-press any message and tap Star ⭐ to save important notes!",
                        color = TalklyTextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(starredList, key = { it.id }) { msg ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = TalklyCard,
                                border = BorderStroke(0.5.dp, TalklyElevated),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = msg.senderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = TalklyCyan
                                        )
                                        IconButton(
                                            onClick = { onToggleStarMessage(msg.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Unstar",
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    UrlAwareMessageText(
                                        text = msg.textContent.ifEmpty { "[Media message]" },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            color = TalklyTextPrimary
                                        ),
                                        linkColor = TalklyCyan,
                                        onLinkClick = { url ->
                                            openUrlSafely(context, url)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = msg.formattedTime,
                                        fontSize = 10.sp,
                                        color = TalklyTextSecondary,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStarredMessagesDialog = false }) {
                    Text("Close", color = TalklyCyan, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            containerColor = TalklySurface,
            title = {
                Text(
                    text = "Block ${member.name}?",
                    fontWeight = FontWeight.Bold,
                    color = TalklyTextPrimary
                )
            },
            text = {
                Text(
                    text = "Blocked contacts will no longer be able to send you messages or call you.",
                    color = TalklyTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirmDialog = false
                        isBlocked = true
                        onBlockUser?.invoke()
                        Toast.makeText(context, "${member.name} has been blocked", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Block", color = TalklyError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel", color = TalklyTextSecondary)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showClearChatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmDialog = false },
            containerColor = TalklySurface,
            title = {
                Text(
                    text = "Clear this chat?",
                    fontWeight = FontWeight.Bold,
                    color = TalklyTextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all messages in this conversation with ${member.name}?",
                    color = TalklyTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatConfirmDialog = false
                        onClearChatHistory()
                        localClearedMessages = true
                        Toast.makeText(context, "Chat messages permanently cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear Chat", color = TalklyError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmDialog = false }) {
                    Text("Cancel", color = TalklyTextSecondary)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showWallpaperDialog) {
        WallpaperSelectionDialog(
            currentValue = wallpaperValue,
            contactName = member.name,
            onDismiss = { showWallpaperDialog = false },
            onWallpaperSelected = { newValue, applyToAll ->
                showWallpaperDialog = false
                val isGallery = newValue.startsWith("content://") || newValue.startsWith("file://")
                val previousWallpaper = wallpaperValue

                if (isGallery) {
                    // Show immediately locally for responsive UX
                    wallpaperValue = newValue
                    Toast.makeText(context, "Uploading wallpaper...", Toast.LENGTH_SHORT).show()

                    scope.launch(Dispatchers.IO) {
                        try {
                            val activeConvId = conversationId
                                ?: prefs.getString("conv_id_${member.id}", null)
                                ?: chatRepo.getOrCreateConversationIdForMember(
                                    memberId = member.id,
                                    memberFirebaseUid = member.firebaseUid,
                                    memberPhone = member.phone
                                )
                            if (activeConvId.isNullOrBlank()) {
                                throw java.io.IOException("Unable to resolve conversation ID for wallpaper")
                            }
                            withContext(Dispatchers.Main) {
                                conversationId = activeConvId
                                prefs.edit().putString("conv_id_${member.id}", activeConvId).apply()
                            }

                            val uploader = com.family.talkly.util.MediaCompressorAndUploader(context)
                            val imageUri = android.net.Uri.parse(newValue)
                            val compressedFile = uploader.compressImage(imageUri) { _, _ -> }
                            val remotePath = "chat_wallpapers/$activeConvId/wp_${System.currentTimeMillis()}.jpg"
                            val cloudUrl = uploader.uploadMediaFile(compressedFile, remotePath)

                            if (cloudUrl.isNotBlank()) {
                                val success = chatRepo.updateConversationWallpaper(activeConvId, cloudUrl)
                                if (success) {
                                    withContext(Dispatchers.Main) {
                                        wallpaperValue = cloudUrl
                                        prefs.edit().putString("wallpaper_${member.id}", cloudUrl).apply()
                                        if (applyToAll) {
                                            prefs.edit().putString("wallpaper_global", cloudUrl).apply()
                                        }
                                        Toast.makeText(context, "Wallpaper updated!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    throw java.io.IOException("Failed to save wallpaper to server")
                                }
                            } else {
                                throw java.io.IOException("Upload returned empty URL")
                            }
                        } catch (e: Exception) {
                            Log.e("ChatDetailScreen", "Gallery wallpaper upload failed: ${e.localizedMessage}", e)
                            withContext(Dispatchers.Main) {
                                wallpaperValue = previousWallpaper
                                Toast.makeText(
                                    context,
                                    "Failed to upload wallpaper: ${e.localizedMessage ?: "Network error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } else {
                    // Built-in wallpaper (Color, Gradient, Default, or Resource)
                    wallpaperValue = newValue
                    prefs.edit().putString("wallpaper_${member.id}", newValue).apply()
                    if (applyToAll) {
                        prefs.edit().putString("wallpaper_global", newValue).apply()
                    }
                    Toast.makeText(context, "Wallpaper updated!", Toast.LENGTH_SHORT).show()

                    scope.launch(Dispatchers.IO) {
                        try {
                            val activeConvId = conversationId
                                ?: prefs.getString("conv_id_${member.id}", null)
                                ?: chatRepo.getOrCreateConversationIdForMember(
                                    memberId = member.id,
                                    memberFirebaseUid = member.firebaseUid,
                                    memberPhone = member.phone
                                )
                            if (!activeConvId.isNullOrBlank()) {
                                withContext(Dispatchers.Main) {
                                    conversationId = activeConvId
                                    prefs.edit().putString("conv_id_${member.id}", activeConvId).apply()
                                }
                                chatRepo.updateConversationWallpaper(activeConvId, newValue)
                            }
                        } catch (e: Exception) {
                            Log.e("ChatDetailScreen", "Failed to sync built-in wallpaper: ${e.localizedMessage}", e)
                        }
                    }
                }
            }
        )
    }

    val currentUid = currentUserProfile?.uid
    val currentPhone = currentUserProfile?.phoneNumber
    val uiMessages = remember(displayedMessages, currentUid, currentPhone, member) {
        TalklyUiMapper.mapToUiMessages(displayedMessages, currentUid, currentPhone, member)
    }

    val subtitleText = when {
        member.isOnline -> "online"
        member.displayLastSeen.isNotBlank() -> member.displayLastSeen.lowercase(Locale.getDefault())
        else -> "offline"
    }

    var paletteIdx by rememberSaveable(member.id) {
        mutableIntStateOf(prefs.getInt("chat_theme_idx", 0).coerceIn(0, ChatPalettes.size - 1))
    }

    val chatUiState = rememberChatUiState()
    val composerState = rememberComposerState()
    var galleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var pb by remember { mutableStateOf<PlaybackState?>(null) }
    val chatMediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (chatMediaPlayer.isPlaying) chatMediaPlayer.stop()
                chatMediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    // Audio Playback progress tracking loop
    LaunchedEffect(pb?.playing, pb?.messageId) {
        while (pb?.playing == true) {
            delay(250)
            try {
                if (chatMediaPlayer.isPlaying) {
                    val curSec = chatMediaPlayer.currentPosition / 1000
                    pb = pb?.copy(seconds = curSec)
                } else {
                    pb = pb?.copy(playing = false)
                    break
                }
            } catch (_: Exception) {
                pb = pb?.copy(playing = false)
                break
            }
        }
    }

    fun toggleAudioPlayback(chatMsg: ChatMessage) {
        val url = chatMsg.mediaUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "Audio file not available", Toast.LENGTH_SHORT).show()
            return
        }
        val msgId = chatMsg.id
        if (pb?.messageId == msgId && pb?.playing == true) {
            try {
                chatMediaPlayer.pause()
                pb = pb?.copy(playing = false)
            } catch (_: Exception) {}
        } else if (pb?.messageId == msgId && pb?.playing == false) {
            try {
                chatMediaPlayer.start()
                pb = pb?.copy(playing = true)
            } catch (_: Exception) {}
        } else {
            try {
                chatMediaPlayer.reset()
                chatMediaPlayer.setDataSource(context, Uri.parse(url))
                chatMediaPlayer.prepare()
                chatMediaPlayer.start()
                chatMediaPlayer.setOnCompletionListener {
                    pb = pb?.copy(playing = false, seconds = 0)
                }
                val durSec = if (chatMediaPlayer.duration > 0) {
                    chatMediaPlayer.duration / 1000
                } else chatMsg.callDurationSec.coerceAtLeast(1)

                pb = PlaybackState(
                    messageId = msgId,
                    seconds = 0,
                    durationSec = durSec,
                    playing = true,
                    speed = 1f
                )
            } catch (e: Exception) {
                Log.e("ChatDetailScreen", "Failed to start audio playback", e)
                Toast.makeText(context, "Could not play audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleAudioSpeed() {
        pb?.let { cur ->
            val nextSpeed = when (cur.speed) {
                1f -> 1.5f
                1.5f -> 2f
                else -> 1f
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    chatMediaPlayer.playbackParams = chatMediaPlayer.playbackParams.setSpeed(nextSpeed)
                } catch (_: Exception) {}
            }
            pb = cur.copy(speed = nextSpeed)
        }
    }

    fun seekAudioFraction(fraction: Float, durationSec: Int) {
        try {
            val targetMs = (fraction * durationSec * 1000).toInt()
            chatMediaPlayer.seekTo(targetMs)
            pb = pb?.copy(seconds = targetMs / 1000)
        } catch (_: Exception) {}
    }

    fun seekAudioBySeconds(deltaSec: Int) {
        try {
            val currentMs = chatMediaPlayer.currentPosition
            val totalMs = chatMediaPlayer.duration
            val targetMs = (currentMs + deltaSec * 1000).coerceIn(0, if (totalMs > 0) totalMs else 3600000)
            chatMediaPlayer.seekTo(targetMs)
            pb = pb?.copy(seconds = targetMs / 1000)
        } catch (_: Exception) {}
    }

    LaunchedEffect(showGalleryPicker) {
        if (showGalleryPicker) {
            galleryItems = TalklyUiMapper.loadDeviceGallery(context)
        }
    }

    LaunchedEffect(composerState.text) {
        onTypingStateChanged(composerState.text.isNotBlank())
    }

    LaunchedEffect(composerState.recording) {
        if (composerState.recording) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                val file = audioRecorder.startRecording()
                if (file != null) {
                    currentAudioFile = file
                    isRecording = true
                }
            } else {
                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val callbacks = remember(displayedMessages, currentUid) {
        object : ChatCallbacks() {
            override fun onBack() {
                onBack()
            }

            override fun onSendText(text: String, replyToId: String?) {
                val repMsg = if (replyToId != null) displayedMessages.firstOrNull { it.id == replyToId } else null
                onSendMessage(
                    text,
                    MessageType.TEXT,
                    null,
                    repMsg?.id,
                    repMsg?.senderName,
                    repMsg?.textContent?.ifEmpty { "Media" }
                )
                onTypingStateChanged(false)
            }

            override fun onEditText(id: String, newText: String) {
                val success = onEditMessage(id, newText)
                if (!success) {
                    Toast.makeText(context, "Cannot edit message after 10 minutes", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onVoiceSend(seconds: Int) {
                val file = audioRecorder.stopRecording() ?: currentAudioFile
                isRecording = false
                if (file != null && file.exists() && file.length() > 0L) {
                    val durationText = "${seconds}s"
                    sendPendingMediaMessage(
                        textContent = "Voice Message ($durationText)",
                        messageType = MessageType.VOICE_NOTE,
                        localMediaUrl = file.absolutePath
                    )
                    currentAudioFile = null
                }
            }

            override fun onDelete(ids: List<String>, forEveryone: Boolean) {
                for (id in ids) {
                    val chatMsg = displayedMessages.firstOrNull { it.id == id }
                    if (chatMsg != null) {
                        if (forEveryone) {
                            val isOwn = chatMsg.senderId == "self" || chatMsg.senderId == (currentUid ?: "")
                            if (isOwn) {
                                val deleted = onDeleteForEveryone(chatMsg.id)
                                if (!deleted) {
                                    onDeleteForYou(chatMsg.id)
                                }
                            } else {
                                onDeleteForYou(chatMsg.id)
                            }
                        } else {
                            onDeleteForYou(chatMsg.id)
                        }
                    }
                }
                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
            }

            override fun onStar(ids: List<String>, starred: Boolean) {
                for (id in ids) {
                    onToggleStarMessage(id)
                }
            }

            override fun onPin(id: String?) {
                if (id != null) {
                    onTogglePinMessage(id)
                }
            }

            override fun onReact(messageId: String, emoji: String) {
                onToggleReaction(messageId, emoji)
            }

            override fun onRemoveMyReaction(messageId: String) {
                val chatMsg = displayedMessages.firstOrNull { it.id == messageId }
                val myReaction = chatMsg?.reaction?.let {
                    ReactionUtils.parseReactions(it, chatMsg.senderId, chatMsg.senderName, chatMsg.timestamp).firstOrNull { r ->
                        r.userId == "self" || (currentUid != null && r.userId == currentUid)
                    }
                }
                if (myReaction != null) {
                    onToggleReaction(messageId, myReaction.emoji)
                }
            }

            override fun onForward(ids: List<String>) {
                if (ids.isNotEmpty()) {
                    selectedMessageIds = ids.toSet()
                    showForwardDialog = true
                }
            }

            override fun onOpenMedia(messageId: String, index: Int) {
                viewerMedia = messageId to index
            }

            override fun onTogglePlay(messageId: String) {
                val chatMsg = displayedMessages.firstOrNull { it.id == messageId }
                if (chatMsg != null) {
                    toggleAudioPlayback(chatMsg)
                }
            }

            override fun onSpeed(messageId: String) {
                toggleAudioSpeed()
            }

            override fun onAudioCall() {
                onStartCall(CallType.AUDIO)
            }

            override fun onVideoCall() {
                onStartCall(CallType.VIDEO)
            }

            override fun onOpenProfile() {
                showContactProfile = true
            }

            override fun onToggleMute() {
                isMuted = !isMuted
                chatUiState.toast = if (isMuted) "Notifications muted" else "Notifications unmuted"
            }

            override fun onOpenTheme() {
                showThemePicker = true
            }

            override fun onOpenMediaList() {
                showMediaLinksDocs = true
            }

            override fun onClearChat() {
                showClearChatConfirmDialog = true
            }

            override fun onToggleBlock() {
                showBlockConfirmDialog = true
            }

            override fun onPickGallery() {
                showGalleryPicker = true
            }

            override fun onPickCamera() {
                showAttachmentDialog = true
            }

            override fun onPickDocument() {
                showAttachmentDialog = true
            }

            override fun onPickAudio() {
                showAttachmentDialog = true
            }

            override fun onPickLocation() {
                Toast.makeText(context, "Location sharing coming soon", Toast.LENGTH_SHORT).show()
            }

            override fun onPickContact() {
                Toast.makeText(context, "Contact sharing coming soon", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TalklyTheme(ChatPalettes[paletteIdx]) {
            ChatScreen(
                contactName = member.name.ifBlank { "User" },
                subtitle = subtitleText,
                messages = uiMessages,
                ui = chatUiState,
                composer = composerState,
                callbacks = callbacks,
                playback = pb?.let { mapOf(it.messageId to it) } ?: emptyMap(),
                typing = member.isTyping,
                muted = isMuted,
                blocked = isBlocked,
                imageContent = { item, modifier ->
                    if (item.url != null) {
                        AsyncImage(
                            model = item.url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = modifier
                        )
                    } else {
                        ArtBox(item.art, modifier)
                    }
                }
            )
        }

        // --- GALLERY PICKER (Multi-select, Zoom preview, Album chips, Caption & Send) ---
        if (showGalleryPicker) {
            GalleryPicker(
                items = galleryItems,
                onDismiss = { showGalleryPicker = false },
                onSend = { selected, caption ->
                    showGalleryPicker = false
                    selected.forEach { gItem ->
                        sendPendingMediaMessage(
                            textContent = caption.ifBlank { if (gItem.isVideo) "Video" else "Photo" },
                            messageType = if (gItem.isVideo) MessageType.VIDEO else MessageType.IMAGE,
                            localMediaUrl = gItem.uri
                        )
                    }
                },
                onCamera = {
                    showGalleryPicker = false
                    showAttachmentDialog = true
                },
                imageContent = { item, modifier ->
                    if (item.uri != null) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = modifier
                        )
                    } else {
                        ArtBox(item.art, modifier)
                    }
                }
            )
        }

        // --- FULL-SCREEN MEDIA VIEWER & AUDIO PLAYER (Pinch zoom, double-tap 2.6x zoom, pan, seek, speed) ---
        viewerMedia?.let { (msgId, startIndex) ->
            val chatMsg = displayedMessages.firstOrNull { it.id == msgId }
            if (chatMsg == null) {
                viewerMedia = null
            } else if (chatMsg.messageType == MessageType.VOICE_NOTE) {
                AudioPlayerScreen(
                    title = "Voice Message",
                    subtitle = "Shared by ${chatMsg.senderName}",
                    art = chatMsg.id.hashCode().mod(8),
                    durationSec = if (chatMsg.callDurationSec > 0) chatMsg.callDurationSec else 15,
                    playback = pb?.takeIf { it.messageId == msgId },
                    onClose = { viewerMedia = null },
                    onTogglePlay = { toggleAudioPlayback(chatMsg) },
                    onSeek = { f ->
                        val dur = if (chatMsg.callDurationSec > 0) chatMsg.callDurationSec else 15
                        seekAudioFraction(f, dur)
                    },
                    onSeekBy = { deltaSec -> seekAudioBySeconds(deltaSec) },
                    onSpeed = { toggleAudioSpeed() }
                )
            } else {
            val targetUiMsg = uiMessages.firstOrNull { it.id == msgId }
            val mediaItems = if (targetUiMsg != null && targetUiMsg.items.isNotEmpty()) {
                targetUiMsg.items
            } else {
                listOf(
                    MediaItem(
                        url = chatMsg.mediaUrl,
                        isVideo = chatMsg.messageType == MessageType.VIDEO,
                        durationSec = chatMsg.callDurationSec,
                        sizeBytes = chatMsg.fileSizeBytes ?: 0L
                    )
                )
            }
                MediaViewer(
                    items = mediaItems,
                    startIndex = startIndex,
                    title = if (chatMsg.messageType == MessageType.VIDEO) "Video" else "Photos",
                    onClose = { viewerMedia = null },
                    imageContent = { item, modifier ->
                        if (item.url != null) {
                            AsyncImage(
                                model = item.url,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = modifier
                            )
                        } else {
                            ArtBox(item.art, modifier)
                        }
                    },
                    playback = pb?.takeIf { it.messageId == msgId },
                    onTogglePlay = { toggleAudioPlayback(chatMsg) },
                    onSeek = { f ->
                        seekAudioFraction(f, chatMsg.callDurationSec.coerceAtLeast(1))
                    },
                    onSeekBy = { deltaSec -> seekAudioBySeconds(deltaSec) },
                    onSpeed = { toggleAudioSpeed() },
                    onDelete = { mItem ->
                        val target = displayedMessages.firstOrNull { it.mediaUrl == mItem.url || it.id == msgId }
                        if (target != null) {
                            val isOwn = target.senderId == "self" || target.senderId == (currentUid ?: "")
                            if (isOwn) {
                                val del = onDeleteForEveryone(target.id)
                                if (!del) onDeleteForYou(target.id)
                            } else {
                                onDeleteForYou(target.id)
                            }
                            viewerMedia = null
                            Toast.makeText(context, "Media deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // --- SPOTIFY-GRADE COVER-PALETTE PROFILE SCREEN ---
        if (showContactProfile) {
            val mediaCount = remember(displayedMessages) {
                displayedMessages.count { it.mediaUrl != null && !it.isDeletedForEveryone }
            }
            ProfileScreen(
                name = member.name.ifBlank { "User" },
                lastSeen = if (member.isOnline) "Active now" else member.displayLastSeen.ifBlank { "offline" },
                about = member.phone.ifBlank { "Talkly Family Contact" },
                muted = isMuted,
                blocked = isBlocked,
                mediaCount = mediaCount,
                palette = presetPalette(member.id.hashCode().mod(8)),
                actions = ProfileActions(
                    onBack = { showContactProfile = false },
                    onAudio = {
                        showContactProfile = false
                        onStartCall(CallType.AUDIO)
                    },
                    onVideo = {
                        showContactProfile = false
                        onStartCall(CallType.VIDEO)
                    },
                    onSearch = {
                        showContactProfile = false
                        chatUiState.searching = true
                    },
                    onToggleMute = {
                        callbacks.onToggleMute()
                    },
                    onTheme = {
                        showThemePicker = true
                    },
                    onMedia = {
                        showContactProfile = false
                        showMediaLinksDocs = true
                    },
                    onClear = {
                        showContactProfile = false
                        showClearChatConfirmDialog = true
                    },
                    onBlock = {
                        showContactProfile = false
                        showBlockConfirmDialog = true
                    },
                    onChangeCover = {
                        paletteIdx = (paletteIdx + 1) % ChatPalettes.size
                        prefs.edit().putInt("chat_theme_idx", paletteIdx).apply()
                        chatUiState.toast = "Theme: ${ChatPalettes[paletteIdx].name}"
                    }
                ),
                cover = { modifier ->
                    if (!member.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = member.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = modifier
                        )
                    } else {
                        ArtBox(member.id.hashCode().mod(8), modifier)
                    }
                }
            )
        }

        // --- CHAT THEME SELECTION SHEET ---
        if (showThemePicker) {
            com.talkly.designui.chat.ThemeSelectionSheet(
                currentIdx = paletteIdx,
                onSelectTheme = { newIdx ->
                    paletteIdx = newIdx
                    prefs.edit().putInt("chat_theme_idx", newIdx).apply()
                    chatUiState.toast = "Theme: ${ChatPalettes[newIdx].name}"
                },
                onDismiss = { showThemePicker = false }
            )
        }

        // --- SHARED MEDIA, LINKS & DOCS SCREEN ---
        if (showMediaLinksDocs) {
            com.talkly.designui.chat.MediaLinksDocsScreen(
                messages = displayedMessages,
                onOpenMedia = { msg ->
                    showMediaLinksDocs = false
                    viewerMedia = msg.id to 0
                },
                onClose = { showMediaLinksDocs = false }
            )
        }

        // --- IN-APP MINIMIZED CALL PILL (if call active & minimized) ---
        if (activeCallInfo != null) {
            TalklyMinimizedCallPill(
                callInfo = activeCallInfo,
                onRestoreCall = { onRestoreCall?.invoke() },
                onEndCall = { onEndCall?.invoke() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .zIndex(999f)
            )
        }
    }
}


@Composable
fun VoiceNotePreviewBar(
    audioFile: java.io.File,
    durationSec: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val currentPosMs = remember { mutableStateOf(0L) }
    val totalDurationMs = remember { mutableStateOf((durationSec * 1000L).coerceAtLeast(1000L)) }
    var isPrepared by remember { mutableStateOf(false) }

    val mediaPlayer = remember { MediaPlayer() }

    androidx.compose.runtime.DisposableEffect(audioFile) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, Uri.fromFile(audioFile))
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { mp ->
                isPrepared = true
                if (mp.duration > 0) totalDurationMs.value = mp.duration.toLong()
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                currentPosMs.value = 0L
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceNotePreview", "Error setting preview player: ${e.localizedMessage}")
        }

        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                if (mediaPlayer.isPlaying) {
                    currentPosMs.value = mediaPlayer.currentPosition.toLong()
                } else {
                    isPlaying = false
                }
            } catch (e: Exception) {
                isPlaying = false
            }
            delay(150)
        }
    }

    val progress = if (totalDurationMs.value > 0) (currentPosMs.value.toFloat() / totalDurationMs.value.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        color = Color(0xCC18212B),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.75.dp, Color(0x3322D3EE)),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = TalklyCyan,
                modifier = Modifier
                    .size(34.dp)
                    .clickable {
                        if (!isPrepared) return@clickable
                        try {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VoiceNotePreview", "Error toggling preview: ${e.localizedMessage}")
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause Preview",
                        tint = Color(0xFF080B10),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice Note Preview",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TalklyCyan
                    )
                    val dispMs = if (isPlaying || currentPosMs.value > 0) currentPosMs.value else totalDurationMs.value
                    val secs = (dispMs / 1000).toInt()
                    Text(
                        text = String.format(Locale.getDefault(), "%d:%02d", secs / 60, secs % 60),
                        fontSize = 10.5.sp,
                        color = TalklyTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                com.family.talkly.ui.components.AudioWaveformBar(
                    progress = progress,
                    isPlaying = isPlaying,
                    seed = audioFile.hashCode(),
                    activeColor = TalklyCyan,
                    inactiveColor = TalklyElevated,
                    onSeek = { seekRatio ->
                        if (isPrepared && totalDurationMs.value > 0) {
                            val seekMs = (seekRatio * totalDurationMs.value).toLong()
                            currentPosMs.value = seekMs
                            try {
                                mediaPlayer.seekTo(seekMs.toInt())
                            } catch (e: Exception) {
                                android.util.Log.e("VoiceNotePreview", "Error seeking preview: ${e.localizedMessage}")
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Discard Recording",
                    tint = TalklyTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                shape = CircleShape,
                color = TalklyCyan,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { onSend() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Voice Note",
                        tint = Color(0xFF080B10),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Checks if the user is currently looking at or near the bottom of the message list.
 * Evaluates actual pixel distance of the last visible item from the bottom of the viewport
 * to avoid arbitrary index-based jumps.
 * Allows approximately 0–3 messages below the user's current position to auto-scroll,
 * while preventing jumps when 5–8+ messages are below.
 */
private fun isUserNearBottom(
    layoutInfo: androidx.compose.foundation.lazy.LazyListLayoutInfo,
    thresholdPx: Int
): Boolean {
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 2) return true
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return true
    val lastVisible = visibleItems.last()
    // If the bottom-most item in the list (the bottom spacer) is visible:
    if (lastVisible.index == totalItems - 1) return true
    // If within ~0-3 messages of the bottom (allowing up to 3 newer messages below):
    if (lastVisible.index >= totalItems - 5) {
        val lastItemBottom = lastVisible.offset + lastVisible.size
        val viewportBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        val distanceFromBottom = lastItemBottom - viewportBottom
        return distanceFromBottom <= thresholdPx
    }
    return false
}

/**
 * Checks if an IMAGE or VIDEO message has completed its upload lifecycle and
 * has reached an available final remote URL (not temporary/local/uploading).
 */
private fun isFinalMediaAvailable(message: ChatMessage): Boolean {
    if (message.messageType != MessageType.IMAGE && message.messageType != MessageType.VIDEO) {
        return false
    }
    val url = message.mediaUrl
    if (url.isNullOrBlank()) return false
    val isLocalOrTemp = url.startsWith("content://") ||
            url.startsWith("file://") ||
            url.startsWith("/") ||
            message.isUploading
    return !isLocalOrTemp
}

