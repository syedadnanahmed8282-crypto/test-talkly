@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.family.talkly.ui.screens

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
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.MessageRequest
import com.family.talkly.data.models.MessageType
import androidx.compose.foundation.layout.navigationBarsPadding
import com.family.talkly.data.models.ReactionUtils
import com.family.talkly.data.models.UserProfile
import com.family.talkly.ui.components.AudioPlayerItem
import com.family.talkly.ui.components.ContactProfileDetailsDialog
import com.family.talkly.ui.components.FullMediaViewerDialog
import com.family.talkly.ui.components.MediaAttachmentDialog
import com.family.talkly.ui.components.MediaGroupCluster
import com.family.talkly.ui.components.MediaMessageItem
import com.family.talkly.ui.components.MessageLoadingState
import com.family.talkly.ui.components.OnlinePresenceIndicator
import com.family.talkly.ui.components.ParticleDissolveWrapper
import com.family.talkly.ui.components.rememberParticleDissolveManager
import com.family.talkly.ui.components.WallpaperSelectionDialog
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
    onRefreshMemberProfile: (() -> Unit)? = null
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

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStarredMessagesDialog by remember { mutableStateOf(false) }

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
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var localClearedMessages by remember { mutableStateOf(false) }
    var chatWindowScreenHeight by remember { mutableFloatStateOf(1000f) }
    var selectedMsgIsTopHalf by remember { mutableStateOf(false) }

    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    val isSelectionMode = selectedMessageIds.isNotEmpty()
    val dissolveManager = rememberParticleDissolveManager()

    // Intercept back presses to close overlays or go back to chat list
    BackHandler(enabled = true) {
        when {
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
    var conversationId by remember(member.id) { mutableStateOf<String?>(null) }

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

    if (showContactProfile) {
        ContactProfileDetailsDialog(
            member = member,
            onDismiss = { showContactProfile = false },
            onStartChat = { showContactProfile = false },
            onStartCall = { _, callType ->
                showContactProfile = false
                if (!isMutualContact) {
                    Toast.makeText(context, "Cannot call: Message request must be accepted first", Toast.LENGTH_SHORT).show()
                } else {
                    onStartCall(callType)
                }
            },
            isMutualContact = isMutualContact
        )
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
                                    Text(
                                        text = msg.textContent.ifEmpty { "[Media message]" },
                                        fontSize = 14.sp,
                                        color = TalklyTextPrimary
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
                            val activeConvId = conversationId ?: chatRepo.getOrCreateConversationIdForMember(
                                memberId = member.id,
                                memberFirebaseUid = member.firebaseUid,
                                memberPhone = member.phone
                            )
                            if (activeConvId.isNullOrBlank()) {
                                throw java.io.IOException("Unable to resolve conversation ID for wallpaper")
                            }
                            withContext(Dispatchers.Main) {
                                conversationId = activeConvId
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
                            val activeConvId = conversationId ?: chatRepo.getOrCreateConversationIdForMember(
                                memberId = member.id,
                                memberFirebaseUid = member.firebaseUid,
                                memberPhone = member.phone
                            )
                            if (!activeConvId.isNullOrBlank()) {
                                withContext(Dispatchers.Main) {
                                    conversationId = activeConvId
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

    var topHeaderHeightPx by remember { mutableIntStateOf(0) }
    var bottomComposerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topPadding = remember(topHeaderHeightPx, density) {
        if (topHeaderHeightPx > 0) {
            with(density) { topHeaderHeightPx.toDp() } + 6.dp
        } else {
            90.dp
        }
    }
    val bottomPadding = remember(bottomComposerHeightPx, density) {
        if (bottomComposerHeightPx > 0) {
            with(density) { bottomComposerHeightPx.toDp() } + 6.dp
        } else {
            72.dp
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                chatWindowScreenHeight = coords.size.height.toFloat()
            },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        val isWallpaperImage = wallpaperValue.startsWith("http://") ||
                wallpaperValue.startsWith("https://") ||
                wallpaperValue.startsWith("content://") ||
                wallpaperValue.startsWith("file://") ||
                wallpaperValue.startsWith("android.resource://")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TalklyChatBg)
        ) {
            // TALKLY CONTINUOUS CHAT BACKGROUND
            // Draws continuously edge-to-edge behind the status bar, floating header, messages, floating composer, and navigation bar
            if (isWallpaperImage) {
                AsyncImage(
                    model = wallpaperValue,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
            } else if (wallpaperValue.startsWith("gradient:")) {
                val hexList = wallpaperValue.removePrefix("gradient:").split(",")
                val colors = hexList.mapNotNull {
                    try {
                        Color(android.graphics.Color.parseColor(it.trim()))
                    } catch (e: Exception) {
                        null
                    }
                }.ifEmpty { listOf(TalklyChatBg, TalklyCard) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(colors))
                )
            } else if (wallpaperValue.startsWith("#") && wallpaperValue != "#080B10") {
                val col = try {
                    Color(android.graphics.Color.parseColor(wallpaperValue))
                } catch (e: Exception) {
                    TalklyChatBg
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(col)
                )
            } else {
                // Subtle Talkly abstract geometric ambient backdrop
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(TalklyCyan.copy(alpha = 0.04f), Color.Transparent),
                            center = Offset(width * 0.85f, height * 0.2f),
                            radius = width * 0.6f
                        ),
                        center = Offset(width * 0.85f, height * 0.2f),
                        radius = width * 0.6f
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(TalklyAqua.copy(alpha = 0.035f), Color.Transparent),
                            center = Offset(width * 0.15f, height * 0.75f),
                            radius = width * 0.55f
                        ),
                        center = Offset(width * 0.15f, height * 0.75f),
                        radius = width * 0.55f
                    )
                }
            }

            // 2. FLOATING TRANSLUCENT HEADER OVERLAY (TOP)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
                    .onGloballyPositioned { coords ->
                        topHeaderHeightPx = coords.size.height
                    }
            ) {
                if (isSelectionMode) {
                    // SELECTION TOOLBAR
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xEE11161D),
                            border = BorderStroke(1.dp, TalklyCyan.copy(alpha = 0.6f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { selectedMessageIds = emptySet() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel selection",
                                        tint = TalklyTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = "${selectedMessageIds.size}",
                                    color = TalklyTextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                // Quick actions if exactly 1 message selected
                                if (selectedMessageIds.size == 1) {
                                    val singleMsgId = selectedMessageIds.first()
                                    val singleMsg = combinedMessages.firstOrNull { it.id == singleMsgId }
                                    if (singleMsg != null && !singleMsg.isDeletedForEveryone) {
                                        IconButton(
                                            onClick = {
                                                replyingToMessage = singleMsg
                                                selectedMessageIds = emptySet()
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                                contentDescription = "Reply",
                                                tint = TalklyCyan,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                onToggleStarMessage(singleMsg.id)
                                                val wasStarred = singleMsg.isStarred
                                                Toast.makeText(
                                                    context,
                                                    if (wasStarred) "Unstarred message" else "Starred message ⭐",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                selectedMessageIds = emptySet()
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (singleMsg.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Star",
                                                tint = Color(0xFFFFD54F),
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { showBulkDeleteDialog = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = TalklyError,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (selectedMessageIds.size == 1) {
                        val singleMsgId = selectedMessageIds.first()
                        val singleMsg = combinedMessages.firstOrNull { it.id == singleMsgId }
                        if (singleMsg != null && !singleMsg.isDeletedForEveryone) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = TalklyCard,
                                border = BorderStroke(1.dp, TalklyCyan.copy(alpha = 0.35f)),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 2.dp)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("❤️", "🔥", "😂", "👍", "😮", "😭", "🥰", "👏", "🎉", "💯", "✨", "💙").forEach { emoji ->
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (singleMsg.reaction == emoji) TalklyCyan.copy(alpha = 0.25f) else Color.Transparent
                                                )
                                                .clickable {
                                                    onToggleReaction(singleMsg.id, emoji)
                                                    selectedMessageIds = emptySet()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = emoji, fontSize = 20.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 2.dp)
                    ) {
                        LiquidGlassHeaderCapsule(
                            wallpaperValue = wallpaperValue,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        isSearchActive = false
                                        searchQuery = ""
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Close search",
                                        tint = TalklyTextPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = TalklyTextPrimary, fontSize = 14.sp),
                                    cursorBrush = SolidColor(TalklyCyan),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (searchQuery.isEmpty()) {
                                                Text("Search messages...", color = TalklyTextSecondary, fontSize = 14.sp)
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = TalklyTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TALKLY FLOATING LIQUID GLASS CAPSULE CONVERSATION HEADER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 2.dp)
                    ) {
                        LiquidGlassHeaderCapsule(
                            wallpaperValue = wallpaperValue,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Arrow
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onBack()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TalklyTextPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                        // Contact Avatar & Info (clickable to view profile)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showContactProfile = true }
                        ) {
                            // Avatar with gradient ring and online indicator
                            Box(
                                modifier = Modifier.size(38.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(TalklyCyan, TalklyAqua, TalklyMint, TalklyCyan)
                                            )
                                        )
                                        .padding(1.5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(TalklyCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!isMutualContact) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Masked Profile",
                                                tint = TalklyCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else if (!member.avatarUrl.isNullOrBlank()) {
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
                                                color = TalklyCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )
                                        }
                                    }
                                }

                                // Online badge
                                if (member.isRecentlyActive() && isMutualContact && !isBlocked) {
                                    OnlinePresenceIndicator(
                                        member = member,
                                        size = 9.dp,
                                        borderColor = Color(0xFF11161D),
                                        borderWidth = 1.5.dp,
                                        greenColor = TalklySuccess,
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val displayName = member.firstName
                                    val nameFontSize = when {
                                        displayName.length > 20 -> 12.sp
                                        displayName.length > 14 -> 13.sp
                                        else -> 14.sp
                                    }
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = nameFontSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = TalklyTextPrimary
                                    )
                                    if (isMuted) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Default.VolumeMute,
                                            contentDescription = "Muted",
                                            tint = TalklyTextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                val statusSubtext = when {
                                    !isMutualContact -> "Message request required"
                                    !member.isRegisteredOnTalkly -> "Not on Payra"
                                    isBlocked -> "Blocked"
                                    member.isTyping -> "typing..."
                                    member.isOnline -> "Online"
                                    else -> member.displayLastSeen
                                }
                                Text(
                                    text = statusSubtext,
                                    fontSize = 10.sp,
                                    fontWeight = if (member.isTyping && !isBlocked) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isMutualContact || !member.isRegisteredOnTalkly || isBlocked) TalklyError
                                    else if (member.isTyping) TalklyMint
                                    else if (member.isOnline) TalklySuccess
                                    else TalklyTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right Action Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Search Action
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TalklyTextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Audio Call Action
                            IconButton(
                                onClick = {
                                    if (!isMutualContact) {
                                        Toast.makeText(context, "Cannot call: Message request must be accepted first", Toast.LENGTH_SHORT).show()
                                    } else if (member.isRegisteredOnTalkly) {
                                        onStartCall(CallType.AUDIO)
                                    } else {
                                        Toast.makeText(context, "User not registered on Payra", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (member.isRegisteredOnTalkly && isMutualContact) Color(0x1F22D3EE) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Audio Call",
                                        tint = if (member.isRegisteredOnTalkly && isMutualContact) TalklyCyan else TalklyTextSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // Video Call Action
                            IconButton(
                                onClick = {
                                    if (!isMutualContact) {
                                        Toast.makeText(context, "Cannot call: Message request must be accepted first", Toast.LENGTH_SHORT).show()
                                    } else if (member.isRegisteredOnTalkly) {
                                        android.util.Log.e("Talkly_ZegoEngine", "[CALLER_DIAGNOSTIC] Video call button tapped, passing CallType.VIDEO")
                                        onStartCall(CallType.VIDEO)
                                    } else {
                                        Toast.makeText(context, "User not registered on Payra", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (member.isRegisteredOnTalkly && isMutualContact) Color(0x1F22D3EE) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Video Call",
                                        tint = if (member.isRegisteredOnTalkly && isMutualContact) TalklyCyan else TalklyTextSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // More Menu Action
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = TalklyTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier
                                        .background(TalklySurface)
                                        .border(1.dp, TalklyElevated, RoundedCornerShape(12.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("View Contact Info", color = TalklyTextPrimary, fontWeight = FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = TalklyCyan)
                                        },
                                        onClick = {
                                            showMenu = false
                                            showContactProfile = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Starred Messages", color = TalklyTextPrimary, fontWeight = FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300))
                                        },
                                        onClick = {
                                            showMenu = false
                                            showStarredMessagesDialog = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(if (member.isPinned) "Unpin Conversation" else "Pin Conversation", color = TalklyTextPrimary, fontWeight = FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.PushPin, contentDescription = null, tint = TalklyCyan)
                                        },
                                        onClick = {
                                            showMenu = false
                                            onTogglePinMember()
                                            Toast.makeText(
                                                context,
                                                if (member.isPinned) "Unpinned conversation" else "Pinned conversation to top 📌",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Wallpaper", color = TalklyTextPrimary, fontWeight = FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Wallpaper, contentDescription = null, tint = TalklyCyan)
                                        },
                                        onClick = {
                                            showMenu = false
                                            showWallpaperDialog = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text(if (isMuted) "Unmute Notifications" else "Mute Notifications", color = TalklyTextPrimary, fontWeight = FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                                contentDescription = null,
                                                tint = if (isMuted) TalklySuccess else TalklyTextSecondary
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            isMuted = !isMuted
                                            val msg = if (isMuted) "Muted notifications for ${member.name}" else "Unmuted notifications for ${member.name}"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isBlocked) "Unblock ${member.name}" else "Block Contact",
                                                fontWeight = FontWeight.Medium,
                                                color = if (isBlocked) TalklyCyan else TalklyError
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = null,
                                                tint = if (isBlocked) TalklyCyan else TalklyError
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            if (isBlocked) {
                                                isBlocked = false
                                                onUnblockUser?.invoke()
                                                Toast.makeText(context, "${member.name} unblocked", Toast.LENGTH_SHORT).show()
                                            } else {
                                                showBlockConfirmDialog = true
                                            }
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Clear Chat", fontWeight = FontWeight.Medium, color = TalklyError) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = TalklyError
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showClearChatConfirmDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Offline Connection Warning Banner
                if (!isNetworkConnected) {
                    Surface(
                        color = TalklyError.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, TalklyError.copy(alpha = 0.3f)),
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
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = TalklyError,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Waiting for network... Messages will send when connected",
                                color = TalklyTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Pinned Message Top Banner
                pinnedMessage?.let { pinned ->
                    Surface(
                        color = TalklyCard,
                        border = BorderStroke(1.dp, TalklyCyan.copy(alpha = 0.3f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val uiIdx = uiItems.indexOfFirst {
                                    it.id == pinned.id || (it is ChatUiItem.MediaCluster && it.messages.any { m -> m.id == pinned.id })
                                }
                                if (uiIdx >= 0) {
                                    scope.launch { listState.animateScrollToItem(uiIdx + 1) }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned Message",
                                    tint = TalklyCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Pinned Announcement",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TalklyCyan
                                    )
                                    Text(
                                        text = pinned.textContent.ifEmpty { "[Media Attachment]" },
                                        fontSize = 12.sp,
                                        color = TalklyTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onTogglePinMessage(pinned.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unpin",
                                    tint = TalklyTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. FULL-SCREEN MESSAGE LIST LAYER (Renders edge-to-edge behind floating header and composer)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .zIndex(1f)
            ) {
                if (isLoadingMessages) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding, bottom = bottomPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        MessageLoadingState(
                            message = "Loading messages...",
                            subMessage = "Fetching end-to-end encrypted chat history"
                        )
                    }
                } else if (displayedMessages.isEmpty()) {
                    // TALKLY EMPTY CONVERSATION STATE
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding, bottom = bottomPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = TalklyCard,
                                border = BorderStroke(1.dp, TalklyCyan.copy(alpha = 0.35f)),
                                modifier = Modifier.size(76.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = TalklyCyan,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "Start the conversation",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TalklyTextPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Send a message to begin chatting securely with ${member.name}.",
                                fontSize = 13.sp,
                                color = TalklyTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Quick Icebreaker Pills
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("👋 Hello!", "Hey, how are you doing?", "Good to connect!").forEach { prompt ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = TalklyCard,
                                        border = BorderStroke(0.5.dp, TalklyCyan.copy(alpha = 0.3f)),
                                        modifier = Modifier.clickable {
                                            textInput = prompt
                                        }
                                    ) {
                                        Text(
                                            text = prompt,
                                            color = TalklyCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(
                            top = topPadding,
                            bottom = bottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item(key = "top_spacer") { Spacer(modifier = Modifier.height(2.dp)) }

                        items(uiItems, key = { it.id }) { item ->
                            val msg = item.primaryMessage
                            val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
                            val senderSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(msg.senderId)
                            val isMemberSender = (msg.senderId == member.id) ||
                                    (!member.firebaseUid.isNullOrBlank() && msg.senderId == member.firebaseUid) ||
                                    (member.phone.isNotBlank() && msg.senderId == member.phone) ||
                                    (memberSuffix.isNotBlank() && memberSuffix == senderSuffix)

                            val isSelf = !isMemberSender
                            var offsetX by remember { mutableFloatStateOf(0f) }
                            var showReadDetails by remember { mutableStateOf(false) }

                            // Check if this message needs a date header separator
                            val currentMsgIndex = uiItems.indexOf(item)
                            val showDateHeader = if (currentMsgIndex == 0) {
                                true
                            } else {
                                val prevItem = uiItems[currentMsgIndex - 1]
                                !isSameDay(prevItem.timestamp, item.timestamp)
                            }

                            if (showDateHeader) {
                                val dateLabel = formatTalklyDateSeparator(item.timestamp)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = TalklyCard.copy(alpha = 0.9f),
                                        border = BorderStroke(0.5.dp, TalklyElevated),
                                        shadowElevation = 1.dp
                                    ) {
                                        Text(
                                            text = dateLabel,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TalklyTextSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            val isItemSelected = when (item) {
                                is ChatUiItem.SingleMessage -> selectedMessageIds.contains(item.message.id)
                                is ChatUiItem.MediaCluster -> item.messages.any { selectedMessageIds.contains(it.id) }
                            }

                            val isItemDissolving = when (item) {
                                is ChatUiItem.SingleMessage -> dissolveManager.isDissolving(item.message.id)
                                is ChatUiItem.MediaCluster -> item.messages.any { dissolveManager.isDissolving(it.id) }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isItemSelected) {
                                            Modifier
                                                .background(TalklyCyan.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                                                .border(1.dp, TalklyCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .then(
                                        if (isSelectionMode || isItemDissolving) {
                                            Modifier
                                        } else {
                                            Modifier.pointerInput(item.id) {
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        if (offsetX > 60f) {
                                                            replyingToMessage = msg
                                                        }
                                                        offsetX = 0f
                                                    },
                                                    onHorizontalDrag = { _, dragAmount ->
                                                        if (dragAmount > 0 || offsetX > 0) {
                                                            offsetX = (offsetX + dragAmount).coerceIn(0f, 100f)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    )
                            ) {
                                if (offsetX > 10f) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = "Swipe Reply",
                                        tint = TalklyCyan,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(start = 8.dp)
                                            .size(24.dp)
                                    )
                                }

                                val hasMedia = msg.mediaUrl != null || msg.isMediaExpired(simulatedTimeOffsetMs)
                                val hasReply = msg.replyToSenderName != null
                                val isVoiceNote = msg.messageType == MessageType.VOICE_NOTE
                                val isSingleEmoji = !hasMedia && !hasReply && !msg.isDeletedForEveryone && isSingleEmojiOrSticker(msg.textContent)

                                val bubbleContainerColor = if (isSingleEmoji || isVoiceNote) {
                                    Color.Transparent
                                } else if (isSelf) {
                                    Color.Transparent // Will use gradient background
                                } else {
                                    TalklyCard
                                }

                                var itemYInWindow by remember { mutableFloatStateOf(0f) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset { IntOffset(offsetX.roundToInt(), 0) },
                                    horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode && !isSelf && !isItemDissolving) {
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 6.dp, end = 4.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(if (isItemSelected) TalklyCyan else Color(0x3322D3EE))
                                                .border(1.dp, if (isItemSelected) TalklyCyan else Color(0x6622D3EE), CircleShape)
                                                .clickable { toggleMessageSelection(msg) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isItemSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Done,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF080B10),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }

                                    val dissolveProgress = when (item) {
                                        is ChatUiItem.SingleMessage -> dissolveManager.getProgress(item.message.id)
                                        is ChatUiItem.MediaCluster -> item.messages.firstNotNullOfOrNull { dissolveManager.getProgress(it.id) }
                                    }

                                    ParticleDissolveWrapper(
                                        progress = dissolveProgress,
                                        isSelf = isSelf,
                                        messageType = msg.messageType,
                                        hasMedia = hasMedia
                                    ) {
                                        Box {
                                            when (item) {
                                            is ChatUiItem.MediaCluster -> {
                                                MediaGroupCluster(
                                                    messages = item.messages,
                                                    isSelf = isSelf,
                                                    simulatedTimeOffsetMs = simulatedTimeOffsetMs,
                                                    onMediaClick = { clickedMsg ->
                                                        if (isSelectionMode) {
                                                            toggleMessageSelection(clickedMsg)
                                                        } else if (!clickedMsg.isMediaExpired(simulatedTimeOffsetMs)) {
                                                            fullMediaViewerMessage = clickedMsg
                                                        }
                                                    },
                                                    onLongClick = { targetMsg ->
                                                        if (isSelectionMode) {
                                                            toggleMessageSelection(targetMsg)
                                                        } else {
                                                            selectedMsgIsTopHalf = (itemYInWindow < chatWindowScreenHeight / 2f)
                                                            onMessageLongPress(targetMsg)
                                                        }
                                                    },
                                                    onRetryUpload = { retryMsg ->
                                                        if (!retryMsg.mediaUrl.isNullOrBlank()) {
                                                            val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context)
                                                            val canonicalId = chatRepo.getCanonicalMemberId(member.id)
                                                            com.family.talkly.util.MediaUploadManager.enqueueMediaUpload(
                                                                context = context,
                                                                messageId = retryMsg.id,
                                                                chatKey = canonicalId,
                                                                recipientId = member.id,
                                                                senderUid = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE).getString("user_uid", null),
                                                                senderName = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE).getString("user_name", null),
                                                                messageType = retryMsg.messageType,
                                                                localMediaUrl = retryMsg.mediaUrl,
                                                                textContent = retryMsg.textContent,
                                                                replyToId = retryMsg.replyToMessageId,
                                                                replyToName = retryMsg.replyToSenderName,
                                                                replyToText = retryMsg.replyToText
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .onGloballyPositioned { coords ->
                                                            itemYInWindow = coords.positionInWindow().y
                                                        }
                                                )
                                            }
                                            is ChatUiItem.SingleMessage -> {
                                                val isVoiceNote = msg.messageType == MessageType.VOICE_NOTE
                                                val hasMedia = (msg.messageType == MessageType.IMAGE || msg.messageType == MessageType.VIDEO) &&
                                                        (msg.mediaUrl != null || msg.isMediaExpired(simulatedTimeOffsetMs))

                                                if (isVoiceNote) {
                                                    AudioPlayerItem(
                                                        message = msg,
                                                        isSelf = isSelf,
                                                        onLongClick = {
                                                            if (isSelectionMode) {
                                                                toggleMessageSelection(msg)
                                                            } else {
                                                                selectedMsgIsTopHalf = (itemYInWindow < chatWindowScreenHeight / 2f)
                                                                onMessageLongPress(msg)
                                                            }
                                                        },
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                toggleMessageSelection(msg)
                                                            } else {
                                                                showReadDetails = !showReadDetails
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .onGloballyPositioned { coords ->
                                                                itemYInWindow = coords.positionInWindow().y
                                                            }
                                                    )
                                                } else if (hasMedia) {
                                                    MediaMessageItem(
                                                        message = msg,
                                                        isSelf = isSelf,
                                                        simulatedTimeOffsetMs = simulatedTimeOffsetMs,
                                                        onMediaClick = {
                                                            if (isSelectionMode) {
                                                                toggleMessageSelection(msg)
                                                            } else if (!msg.isMediaExpired(simulatedTimeOffsetMs)) {
                                                                fullMediaViewerMessage = msg
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (isSelectionMode) {
                                                                toggleMessageSelection(msg)
                                                            } else {
                                                                selectedMsgIsTopHalf = (itemYInWindow < chatWindowScreenHeight / 2f)
                                                                onMessageLongPress(msg)
                                                            }
                                                        },
                                                        onRetryUpload = {
                                                            if (!msg.mediaUrl.isNullOrBlank()) {
                                                                val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context)
                                                                val canonicalId = chatRepo.getCanonicalMemberId(member.id)
                                                                com.family.talkly.util.MediaUploadManager.enqueueMediaUpload(
                                                                    context = context,
                                                                    messageId = msg.id,
                                                                    chatKey = canonicalId,
                                                                    recipientId = member.id,
                                                                    senderUid = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE).getString("user_uid", null),
                                                                    senderName = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE).getString("user_name", null),
                                                                    messageType = msg.messageType,
                                                                    localMediaUrl = msg.mediaUrl,
                                                                    textContent = msg.textContent,
                                                                    replyToId = msg.replyToMessageId,
                                                                    replyToName = msg.replyToSenderName,
                                                                    replyToText = msg.replyToText
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .onGloballyPositioned { coords ->
                                                                itemYInWindow = coords.positionInWindow().y
                                                            }
                                                    )
                                                } else {
                                                    // MESSAGE BUBBLE
                                                    Card(
                                            colors = CardDefaults.cardColors(containerColor = bubbleContainerColor),
                                            border = if (isSingleEmoji || isVoiceNote) null else BorderStroke(
                                                width = 0.5.dp,
                                                color = if (isSelf) TalklyCyan.copy(alpha = 0.3f) else TalklyElevated
                                            ),
                                            shape = RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isSelf) 16.dp else 4.dp,
                                                bottomEnd = if (isSelf) 4.dp else 16.dp
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSingleEmoji || isVoiceNote) 0.dp else 1.5.dp),
                                            modifier = Modifier
                                                .widthIn(min = 0.dp, max = 275.dp)
                                                .then(
                                                    if (isSelf && !isSingleEmoji && !isVoiceNote) {
                                                        Modifier.background(
                                                            brush = SentBubbleGradient,
                                                            shape = RoundedCornerShape(
                                                                topStart = 16.dp,
                                                                topEnd = 16.dp,
                                                                bottomStart = 16.dp,
                                                                bottomEnd = 4.dp
                                                            )
                                                        )
                                                    } else Modifier
                                                )
                                                .onGloballyPositioned { coords ->
                                                    itemYInWindow = coords.positionInWindow().y
                                                }
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            toggleMessageSelection(msg)
                                                        } else {
                                                            showReadDetails = !showReadDetails
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (isSelectionMode) {
                                                            toggleMessageSelection(msg)
                                                        } else {
                                                            selectedMsgIsTopHalf = (itemYInWindow < chatWindowScreenHeight / 2f)
                                                            onMessageLongPress(msg)
                                                        }
                                                    }
                                                )
                                        ) {
                                            val isShortSingleLine = !hasMedia && !hasReply && !msg.isDeletedForEveryone &&
                                                    !isVoiceNote && !isSingleEmoji &&
                                                    !msg.textContent.contains('\n') && msg.textContent.length <= 26

                                            if (isShortSingleLine) {
                                                // Ultra-slim single-line bubble (text + inline timestamp)
                                                Column(modifier = Modifier.padding(start = 9.dp, end = 7.dp, top = 5.dp, bottom = 4.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.Bottom,
                                                        horizontalArrangement = Arrangement.Start
                                                    ) {
                                                        Text(
                                                            text = msg.textContent,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                color = TalklyTextPrimary,
                                                                fontSize = 14.5.sp
                                                            )
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(bottom = 0.5.dp)
                                                        ) {
                                                            if (msg.isStarred) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Star,
                                                                    contentDescription = "Starred",
                                                                    tint = Color(0xFFFFB300),
                                                                    modifier = Modifier.size(11.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                            }
                                                            if (msg.isPinned) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PushPin,
                                                                    contentDescription = "Pinned",
                                                                    tint = TalklyCyan,
                                                                    modifier = Modifier.size(11.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                            }
                                                            Text(
                                                                text = if (msg.isEdited) "${msg.formattedTime} • Edited" else msg.formattedTime,
                                                                fontSize = 9.5.sp,
                                                                color = if (isSelf) TalklyTextSecondary.copy(alpha = 0.85f) else TalklyTextSecondary.copy(alpha = 0.7f)
                                                            )
                                                            if (isSelf) {
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                val isPendingMsg = msg.isPending || msg.id.startsWith("temp_")
                                                                val statusState = when {
                                                                    isPendingMsg -> 3
                                                                    msg.isRead -> 2
                                                                    msg.isDelivered -> 1
                                                                    else -> 0
                                                                }
                                                                Crossfade(
                                                                    targetState = statusState,
                                                                    animationSpec = tween(durationMillis = 300),
                                                                    label = "StatusFadeInline"
                                                                ) { state ->
                                                                    when (state) {
                                                                        3 -> {
                                                                            if (!isNetworkConnected) {
                                                                                Icon(
                                                                                    imageVector = Icons.Default.AccessTime,
                                                                                    contentDescription = "Queued",
                                                                                    tint = Color(0xFFF59E0B),
                                                                                    modifier = Modifier.size(12.dp)
                                                                                )
                                                                            } else {
                                                                                CircularProgressIndicator(
                                                                                    strokeWidth = 1.2.dp,
                                                                                    color = TalklyCyan,
                                                                                    modifier = Modifier.size(12.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                        2 -> {
                                                                            Icon(
                                                                                imageVector = Icons.Default.DoneAll,
                                                                                contentDescription = "Seen",
                                                                                tint = TalklyCyan,
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        }
                                                                        1 -> {
                                                                            Icon(
                                                                                imageVector = Icons.Default.DoneAll,
                                                                                contentDescription = "Delivered",
                                                                                tint = TalklyTextPrimary.copy(alpha = 0.7f),
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        }
                                                                        else -> {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Done,
                                                                                contentDescription = "Sent",
                                                                                tint = TalklyTextSecondary,
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (showReadDetails) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        val detailsText = if (msg.isRead) {
                                                            "Seen at ${msg.formattedReadTime}"
                                                        } else if (msg.isDelivered) {
                                                            "Delivered ${msg.formattedTime}"
                                                        } else {
                                                            "Sent ${msg.formattedTime}"
                                                        }
                                                        Text(
                                                            text = detailsText,
                                                            fontSize = 9.sp,
                                                            color = TalklyCyan,
                                                            modifier = Modifier.align(Alignment.End)
                                                        )
                                                    }
                                                }
                                            } else {
                                                // Standard compact multi-line / media / voice bubble
                                                Column(modifier = Modifier.padding(if (isSingleEmoji) 2.dp else if (isVoiceNote) 0.dp else 7.dp)) {
                                                    if (msg.isDeletedForEveryone) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(vertical = 3.dp, horizontal = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Block,
                                                                contentDescription = "Deleted",
                                                                tint = TalklyTextSecondary,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                            Text(
                                                                text = "This message was deleted",
                                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                                    color = TalklyTextSecondary,
                                                                    fontStyle = FontStyle.Italic,
                                                                    fontSize = 12.5.sp
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        // Quoted Reply Preview inside Bubble
                                                        if (msg.replyToSenderName != null) {
                                                            Surface(
                                                                shape = RoundedCornerShape(7.dp),
                                                                color = if (isSelf) Color.Black.copy(alpha = 0.25f) else TalklyElevated,
                                                                border = BorderStroke(0.5.dp, TalklyCyan.copy(alpha = 0.2f)),
                                                                modifier = Modifier
                                                                    .widthIn(max = 240.dp)
                                                                    .padding(bottom = 5.dp)
                                                            ) {
                                                                Row(modifier = Modifier.padding(5.dp)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(2.5.dp)
                                                                            .height(26.dp)
                                                                            .background(TalklyCyan, RoundedCornerShape(1.5.dp))
                                                                    )
                                                                    Spacer(modifier = Modifier.width(5.dp))
                                                                    Column {
                                                                        Text(
                                                                            text = msg.replyToSenderName,
                                                                            fontSize = 10.5.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = TalklyCyan
                                                                        )
                                                                        Text(
                                                                            text = msg.replyToText ?: "Media message",
                                                                            fontSize = 10.5.sp,
                                                                            color = TalklyTextSecondary,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Media Content
                                                        if (msg.mediaUrl != null || msg.isMediaExpired(simulatedTimeOffsetMs)) {
                                                            MediaMessageItem(
                                                                message = msg,
                                                                isSelf = isSelf,
                                                                simulatedTimeOffsetMs = simulatedTimeOffsetMs,
                                                                onMediaClick = {
                                                                    if (!msg.isMediaExpired(simulatedTimeOffsetMs)) {
                                                                        fullMediaViewerMessage = msg
                                                                    }
                                                                },
                                                                onRetryUpload = {
                                                                    if (!msg.mediaUrl.isNullOrBlank()) {
                                                                        val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository.getInstance(context)
                                                                        val canonicalId = chatRepo.getCanonicalMemberId(member.id)
                                                                        com.family.talkly.util.MediaUploadManager.enqueueMediaUpload(
                                                                            context = context,
                                                                            messageId = msg.id,
                                                                            chatKey = canonicalId,
                                                                            recipientId = member.id,
                                                                            senderUid = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE).getString("user_uid", null),
                                                                            senderName = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE).getString("user_name", null),
                                                                            messageType = msg.messageType,
                                                                            localMediaUrl = msg.mediaUrl,
                                                                            textContent = msg.textContent,
                                                                            replyToId = msg.replyToMessageId,
                                                                            replyToName = msg.replyToSenderName,
                                                                            replyToText = msg.replyToText
                                                                        )
                                                                    }
                                                                }
                                                            )
                                                        }

                                                        // Text Content
                                                        if (msg.textContent.isNotEmpty() && !isVoiceNote) {
                                                            Text(
                                                                text = msg.textContent,
                                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                                    color = TalklyTextPrimary,
                                                                    fontSize = if (isSingleEmoji) 42.sp else 14.5.sp
                                                                )
                                                            )
                                                        }
                                                    }

                                                    if (!isVoiceNote) {
                                                        Spacer(modifier = Modifier.height(2.dp))

                                                        // Timestamp & Delivery Status
                                                        Row(
                                                            modifier = Modifier.align(Alignment.End),
                                                            horizontalArrangement = Arrangement.End,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (msg.isStarred) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Star,
                                                                    contentDescription = "Starred",
                                                                    tint = Color(0xFFFFB300),
                                                                    modifier = Modifier.size(11.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                            }
                                                            if (msg.isPinned) {
                                                                Icon(
                                                                    imageVector = Icons.Default.PushPin,
                                                                    contentDescription = "Pinned",
                                                                    tint = TalklyCyan,
                                                                    modifier = Modifier.size(11.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                            }
                                                            Text(
                                                                text = if (msg.isEdited) "${msg.formattedTime} • Edited" else msg.formattedTime,
                                                                fontSize = 9.5.sp,
                                                                color = TalklyTextSecondary
                                                            )
                                                            if (isSelf) {
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                val isPendingMsg = msg.isPending || msg.id.startsWith("temp_") ||
                                                                        (!msg.mediaUrl.isNullOrEmpty() && (msg.mediaUrl.startsWith("content://") || msg.mediaUrl.startsWith("file://") || msg.mediaUrl.startsWith("/")))
                                                                val statusState = when {
                                                                    isPendingMsg -> 3
                                                                    msg.isRead -> 2
                                                                    msg.isDelivered -> 1
                                                                    else -> 0
                                                                }
                                                                Crossfade(
                                                                    targetState = statusState,
                                                                    animationSpec = tween(durationMillis = 300),
                                                                    label = "StatusFadeAnimation"
                                                                ) { state ->
                                                                    when (state) {
                                                                        3 -> {
                                                                            if (!isNetworkConnected) {
                                                                                Icon(
                                                                                    imageVector = Icons.Default.AccessTime,
                                                                                    contentDescription = "Queued",
                                                                                    tint = Color(0xFFF59E0B),
                                                                                    modifier = Modifier.size(12.dp)
                                                                                )
                                                                            } else {
                                                                                CircularProgressIndicator(
                                                                                    strokeWidth = 1.2.dp,
                                                                                    color = TalklyCyan,
                                                                                    modifier = Modifier.size(12.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                        2 -> {
                                                                            // SEEN/READ (Cyan Double Check)
                                                                            Icon(
                                                                                imageVector = Icons.Default.DoneAll,
                                                                                contentDescription = "Seen",
                                                                                tint = TalklyCyan,
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        }
                                                                        1 -> {
                                                                            // DELIVERED (White Double Check)
                                                                            Icon(
                                                                                imageVector = Icons.Default.DoneAll,
                                                                                contentDescription = "Delivered",
                                                                                tint = TalklyTextPrimary.copy(alpha = 0.7f),
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        }
                                                                        else -> {
                                                                            // SENT (Single Check)
                                                                            Icon(
                                                                                imageVector = Icons.Default.Done,
                                                                                contentDescription = "Sent",
                                                                                tint = TalklyTextSecondary,
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        if (showReadDetails) {
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            val detailsText = if (msg.isRead) {
                                                                "Seen at ${msg.formattedReadTime}"
                                                            } else if (msg.isDelivered) {
                                                                "Delivered ${msg.formattedTime}"
                                                            } else {
                                                                "Sent ${msg.formattedTime}"
                                                            }
                                                            Text(
                                                                text = detailsText,
                                                                fontSize = 9.sp,
                                                                color = TalklyCyan,
                                                                modifier = Modifier.align(Alignment.End)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (isSelectionMode && isSelf) {
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 4.dp, end = 6.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(if (isItemSelected) TalklyCyan else Color(0x3322D3EE))
                                                .border(1.dp, if (isItemSelected) TalklyCyan else Color(0x6622D3EE), CircleShape)
                                                .clickable { toggleMessageSelection(msg) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isItemSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Done,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF080B10),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                                        // REACTION BADGE OVERLAY
                                        msg.reaction?.let { reactEmoji ->
                                            val entries = remember(reactEmoji) { ReactionUtils.parseReactions(reactEmoji, msg.senderId, msg.senderName, msg.timestamp) }
                                            if (entries.isNotEmpty()) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = TalklyCard,
                                                    border = BorderStroke(0.5.dp, TalklyCyan.copy(alpha = 0.35f)),
                                                    tonalElevation = 4.dp,
                                                    shadowElevation = 2.dp,
                                                    modifier = Modifier
                                                        .align(if (isSelf) Alignment.BottomStart else Alignment.BottomEnd)
                                                        .offset(y = 8.dp, x = if (isSelf) (-6).dp else 6.dp)
                                                        .clickable { reactionDetailsMessage = msg }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        val distinctEmojis = entries.map { it.emoji }.distinct()
                                                        distinctEmojis.forEach { emoji ->
                                                            Text(text = emoji, fontSize = 13.sp)
                                                        }
                                                        if (entries.size > 1) {
                                                            Text(
                                                                text = "${entries.size}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = TalklyTextPrimary
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

                        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // FLOATING SCROLL TO BOTTOM BUTTON (MANUAL USER ACTION ONLY)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isScrolledUp && displayedMessages.isNotEmpty(),
                        enter = scaleIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200)),
                        exit = scaleOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(150)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = bottomPadding + 8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TalklyCard,
                            border = BorderStroke(1.dp, TalklyCyan.copy(alpha = 0.6f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(42.dp)
                                .clickable {
                                    scope.launch {
                                        val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                        listState.animateScrollToItem(target)
                                        unreadCountWhileScrolledUp = 0
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(TalklyCyan.copy(alpha = 0.2f), Color.Transparent)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll to bottom",
                                    tint = TalklyCyan,
                                    modifier = Modifier.size(26.dp)
                                )
                                if (unreadCountWhileScrolledUp > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-2).dp)
                                            .background(TalklyCyan, CircleShape)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (unreadCountWhileScrolledUp > 99) "99+" else "$unreadCountWhileScrolledUp",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF080B10)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. FLOATING TRANSLUCENT COMPOSER OVERLAY (BOTTOM)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .zIndex(2f)
                    .onGloballyPositioned { coords ->
                        bottomComposerHeightPx = coords.size.height
                    }
            ) {
                // LIVE TYPING INDICATOR BUBBLE (Visually anchored immediately ABOVE message composer)
                AnimatedVisibility(
                    visible = member.isTyping,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(180)),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(150))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                            color = TalklyCard.copy(alpha = 0.95f),
                            border = BorderStroke(0.5.dp, TalklyCyan.copy(alpha = 0.35f)),
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${member.name} is typing",
                                    fontSize = 11.5.sp,
                                    color = TalklyTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                AnimatedTypingDotsIndicator(
                                    dotColor = TalklyCyan,
                                    dotSize = 4.5.dp
                                )
                            }
                        }
                    }
                }

                // EDITING BANNER BAR
                AnimatedVisibility(
                    visible = editingMessage != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    editingMessage?.let { editMsg ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xCC18212B),
                            border = BorderStroke(0.75.dp, TalklyMint.copy(alpha = 0.4f)),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .background(TalklyMint, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Editing Message ✏️",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TalklyMint
                                        )
                                        Text(
                                            text = editMsg.textContent,
                                            fontSize = 11.5.sp,
                                            color = TalklyTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        editingMessage = null
                                        textInput = ""
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel edit",
                                        tint = TalklyTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // REPLYING BANNER BAR
                AnimatedVisibility(
                    visible = replyingToMessage != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    replyingToMessage?.let { replyMsg ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xCC18212B),
                            border = BorderStroke(0.75.dp, TalklyCyan.copy(alpha = 0.4f)),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .background(TalklyCyan, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Replying to ${replyMsg.senderName}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TalklyCyan
                                        )
                                        Text(
                                            text = replyMsg.textContent.ifEmpty { "Media photo/video" },
                                            fontSize = 11.5.sp,
                                            color = TalklyTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { replyingToMessage = null },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel reply",
                                        tint = TalklyTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // BOTTOM COMPOSER OR SPECIAL STATE BANNER
                if (!member.isRegisteredOnTalkly) {
                    Surface(
                        color = TalklyError.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, TalklyError.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = TalklyError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "User not registered on Payra",
                                color = TalklyError,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isBlocked) {
                    Surface(
                        color = TalklyError.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, TalklyError.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isBlocked = false
                                onUnblockUser?.invoke()
                                Toast.makeText(context, "${member.name} unblocked", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = TalklyError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You blocked this contact. Tap to unblock.",
                                color = TalklyError,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (!isMutualContact) {
                    Surface(
                        color = TalklySurface,
                        border = BorderStroke(1.dp, TalklyElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isRequestSentByMe) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = TalklyCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Message Request Sent",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TalklyTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Waiting for ${member.name} to accept your request and save your contact.",
                                    fontSize = 13.sp,
                                    color = TalklyTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            } else if (pendingMessageRequest != null) {
                                Text(
                                    text = "${member.name} sent you a message request",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TalklyTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\"${pendingMessageRequest.initialMessage}\"",
                                    fontSize = 13.sp,
                                    color = TalklyCyan,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = { onAcceptMessageRequest(pendingMessageRequest) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TalklyCyan),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text("Accept & Save Contact", color = Color(0xFF080B10), fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onDeclineMessageRequest(pendingMessageRequest.id) },
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, TalklyError)
                                    ) {
                                        Text("Decline", color = TalklyError, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                var requestInputText by remember { mutableStateOf("Hello, I would like to connect on Payra!") }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = TalklyCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Send Message Request",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TalklyTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "You and ${member.name} are not mutual contacts. Send a message request to unlock chat, calls, and status updates.",
                                    fontSize = 12.sp,
                                    color = TalklyTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = requestInputText,
                                    onValueChange = { requestInputText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Write message request...", color = TalklyTextSecondary) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TalklyTextPrimary,
                                        unfocusedTextColor = TalklyTextPrimary,
                                        focusedContainerColor = TalklyCard,
                                        unfocusedContainerColor = TalklyCard,
                                        focusedBorderColor = TalklyCyan,
                                        unfocusedBorderColor = TalklyElevated
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onSendMessageRequest(requestInputText) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = TalklyCyan),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF080B10), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Send Message Request", color = Color(0xFF080B10), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (isPreviewingVoiceNote && currentAudioFile != null) {
                    VoiceNotePreviewBar(
                        audioFile = currentAudioFile!!,
                        durationSec = recordingDurationSec,
                        onCancel = { cancelVoicePreview() },
                        onSend = { sendPreviewedVoiceNote() }
                    )
                } else if (isRecording) {
                    // TALKLY ACTIVE VOICE RECORDING PILL
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xCC18212B),
                        border = BorderStroke(0.75.dp, TalklyError.copy(alpha = 0.5f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(TalklyError, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = TalklyError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "Recording %d:%02d", recordingDurationSec / 60, recordingDurationSec % 60),
                                    color = TalklyError,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { cancelVoicePreview() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Cancel Recording",
                                        tint = TalklyTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Surface(
                                    shape = CircleShape,
                                    color = TalklyCyan,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable { stopAndPreparePreview() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = "Finish Recording & Preview",
                                            tint = Color(0xFF080B10),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TALKLY THREE-PART REAL FLOATING MESSAGE COMPOSER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Standalone Floating Glass Attachment Button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC18212B),
                            border = BorderStroke(0.75.dp, Color(0x3322D3EE)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(40.dp)
                        ) {
                            IconButton(
                                onClick = { showAttachmentDialog = true },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = "Attach",
                                    tint = TalklyCyan,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // 2. Standalone Floating Rounded Glass Text Capsule
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xCC18212B),
                            border = BorderStroke(0.75.dp, Color(0x3322D3EE)),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = textInput,
                                    onValueChange = {
                                        textInput = it
                                        onTypingStateChanged(it.isNotBlank())
                                    },
                                    textStyle = TextStyle(
                                        color = TalklyTextPrimary,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(TalklyCyan),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (textInput.isNotBlank()) {
                                                if (editingMessage != null) {
                                                    val success = onEditMessage(editingMessage!!.id, textInput)
                                                    if (!success) {
                                                        Toast.makeText(context, "১০ মিনিট পার হয়ে যাওয়ায় এডিট করা সম্ভব নয়", Toast.LENGTH_SHORT).show()
                                                    }
                                                    editingMessage = null
                                                } else {
                                                    if (isUserAtBottom || wasNearBottomBeforeIme) {
                                                        ownSendPendingScroll = true
                                                    }
                                                    onSendMessage(
                                                        textInput, MessageType.TEXT, null,
                                                        replyingToMessage?.id,
                                                        replyingToMessage?.senderName,
                                                        replyingToMessage?.textContent?.ifEmpty { "Media" }
                                                    )
                                                    replyingToMessage = null
                                                }
                                                textInput = ""
                                                onTypingStateChanged(false)
                                            }
                                        }
                                    ),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 4,
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (textInput.isEmpty()) {
                                                Text(
                                                    text = "Type a message...",
                                                    fontSize = 14.sp,
                                                    color = TalklyTextSecondary
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }

                        // 3. Standalone Floating Glass Send / Mic Button
                        AnimatedContent(
                            targetState = textInput.isNotBlank(),
                            transitionSpec = {
                                scaleIn(animationSpec = tween(150)) togetherWith scaleOut(animationSpec = tween(150))
                            },
                            label = "SendMicTransition"
                        ) { hasText ->
                            if (hasText) {
                                Surface(
                                    shape = CircleShape,
                                    color = TalklyCyan,
                                    border = BorderStroke(0.75.dp, TalklyMint.copy(alpha = 0.5f)),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (editingMessage != null) {
                                                val success = onEditMessage(editingMessage!!.id, textInput)
                                                if (!success) {
                                                    Toast.makeText(context, "১০ মিনিট পার হয়ে যাওয়ায় এডিট করা সম্ভব নয়", Toast.LENGTH_SHORT).show()
                                                }
                                                editingMessage = null
                                            } else {
                                                if (isUserAtBottom || wasNearBottomBeforeIme) {
                                                    ownSendPendingScroll = true
                                                }
                                                onSendMessage(
                                                    textInput, MessageType.TEXT, null,
                                                    replyingToMessage?.id,
                                                    replyingToMessage?.senderName,
                                                    replyingToMessage?.textContent?.ifEmpty { "Media" }
                                                )
                                                replyingToMessage = null
                                            }
                                            textInput = ""
                                            onTypingStateChanged(false)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = Color(0xFF080B10),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xCC18212B),
                                    border = BorderStroke(0.75.dp, Color(0x3322D3EE)),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = { startVoiceRecording() },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Mic,
                                            contentDescription = "Record voice note",
                                            tint = TalklyCyan,
                                            modifier = Modifier.size(20.dp)
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

private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun formatTalklyDateSeparator(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    return if (now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    ) {
        "Today"
    } else if (now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1
    ) {
        "Yesterday"
    } else {
        val format = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        format.format(Date(timestamp))
    }
}

private fun isSingleEmojiOrSticker(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false

    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(trimmed)
    var count = 0
    var singleCluster = ""
    var start = iterator.first()
    var end = iterator.next()

    while (end != java.text.BreakIterator.DONE) {
        count++
        if (count == 1) {
            singleCluster = trimmed.substring(start, end)
        } else {
            return false
        }
        start = end
        end = iterator.next()
    }

    if (count != 1 || singleCluster.isEmpty()) return false

    val firstCodePoint = singleCluster.codePointAt(0)
    val type = Character.getType(firstCodePoint)

    val isEmojiOrSymbol = type == Character.OTHER_SYMBOL.toInt() ||
            type == Character.SURROGATE.toInt() ||
            firstCodePoint in 0x1F600..0x1F64F ||
            firstCodePoint in 0x1F300..0x1F5FF ||
            firstCodePoint in 0x1F680..0x1F6FF ||
            firstCodePoint in 0x1F1E6..0x1F1FF ||
            firstCodePoint in 0x2600..0x27BF ||
            firstCodePoint in 0x1F900..0x1F9FF ||
            firstCodePoint in 0x1FA70..0x1FAFF

    val isPlainAscii = (firstCodePoint in 'a'.code..'z'.code) ||
            (firstCodePoint in 'A'.code..'Z'.code) ||
            (firstCodePoint in '0'.code..'9'.code) ||
            (singleCluster.length == 1 && firstCodePoint < 128)

    return isEmojiOrSymbol && !isPlainAscii
}

@Composable
fun AnimatedTypingDotsIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = TalklyCyan,
    dotSize: androidx.compose.ui.unit.Dp = 5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.2f at 0
                1.0f at 250
                0.2f at 500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )

    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.2f at 200
                1.0f at 450
                0.2f at 700
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )

    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.2f at 400
                1.0f at 650
                0.2f at 900
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha1))
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha2))
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha3))
        )
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

// =========================================================================
// REALISTIC iPhone-STYLE LIQUID GLASS PROFILE HEADER ENGINE
// =========================================================================

/**
 * Realistic iPhone-style Liquid Glass capsule surface for the conversation header.
 * Optically integrates with the ACTUAL underlying wallpaper/background without
 * any fixed artificial cyan, blue, or colored tint in the glass body.
 *
 * Visual Stack:
 * 1. ACTUAL BACKGROUND/WALLPAPER (Diffused & blurred backdrop layer)
 * 2. SUBTLE OPTICAL REFRACTION (Neutral luminance transmission & thickness variation)
 * 3. TRANSPARENT GLASS BODY (Center remains clear and transparent to the backdrop)
 * 4. INTERNAL LIGHT RESPONSE & CONVEX SHEEN (Cylindrical ambient reflection)
 * 5. DIRECTIONAL SPECULAR EDGE REFLECTION (Top-left keylight glints, curved corner reflections, soft dark bottom rim)
 * 6. PHYSICAL DEPTH & SHADOW (Soft floating elevation above chat wallpaper)
 */
@Composable
private fun LiquidGlassHeaderCapsule(
    wallpaperValue: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    content: @Composable () -> Unit
) {
    val cleanVal = wallpaperValue.trim()
    val isWallpaperImage = cleanVal.startsWith("http://") ||
            cleanVal.startsWith("https://") ||
            cleanVal.startsWith("content://") ||
            cleanVal.startsWith("file://") ||
            cleanVal.startsWith("android.resource://")

    Box(
        modifier = modifier
            // Physical soft depth / drop shadow separating the glass from underlying wallpaper
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color(0x60000000),
                spotColor = Color(0x75000000)
            )
            .clip(shape)
    ) {
        // =========================================================================
        // 1. ACTUAL BACKGROUND/WALLPAPER OPTICAL DIFFUSION (BACKDROP)
        // Inherits directly from the background; reacts naturally to pink, blue, green,
        // photo images, or dark themes with NO artificial color tint overlay.
        // =========================================================================
        if (isWallpaperImage) {
            AsyncImage(
                model = cleanVal,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                26f, 26f, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    }
            )
            // Ambient contrast veil matching chat background scrim (ensures icons & text legibility)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.48f))
            )
        } else if (cleanVal.startsWith("gradient:")) {
            val hexList = cleanVal.removePrefix("gradient:").split(",")
            val colors = hexList.mapNotNull {
                try {
                    Color(android.graphics.Color.parseColor(it.trim()))
                } catch (_: Exception) {
                    null
                }
            }.ifEmpty { listOf(TalklyChatBg, TalklyCard) }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(colors))
            )
            // Soft optical diffusion veil
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.20f))
            )
        } else if (cleanVal.startsWith("#") && cleanVal != "#080B10") {
            val col = try {
                Color(android.graphics.Color.parseColor(cleanVal))
            } catch (_: Exception) {
                TalklyChatBg
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(col)
            )
            // If the wallpaper color is bright (e.g. pastel/pink/bright tones), add ambient contrast so text is clear
            val lum = (col.red * 0.299f + col.green * 0.587f + col.blue * 0.114f)
            if (lum > 0.35f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = (lum * 0.50f).coerceIn(0.20f, 0.55f)))
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.15f))
                )
            }
        } else {
            // Default Talkly ambient backdrop slice: TalklyChatBg with soft ambient tone
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(TalklyChatBg)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.025f), Color.Transparent),
                            center = Offset(0.5f, 0.2f),
                            radius = 400f
                        )
                    )
            )
        }

        // =========================================================================
        // 2, 3, 4, 5, 6, 7: OPTICAL REFRACTION, INTERNAL LIGHT RESPONSE & SPECULAR RIM
        // Pure neutral optical glass with NO fixed color tint.
        // =========================================================================
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val cornerRadiusPx = 22.dp.toPx()
                    val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    val w = size.width
                    val h = size.height

                    // -----------------------------------------------------------------
                    // A. PHYSICAL CURVATURE & THICKNESS (Neutral Light Response)
                    // Top receives ambient overhead light; center is transparent;
                    // bottom has subtle ambient thickness shading.
                    // -----------------------------------------------------------------
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.07f),
                                Color.White.copy(alpha = 0.015f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.08f)
                            ),
                            startY = 0f,
                            endY = h
                        ),
                        cornerRadius = cornerRadius
                    )

                    // -----------------------------------------------------------------
                    // B. SUBTLE OPTICAL REFRACTION / CAUSTIC SCATTERING
                    // Delicate neutral radial dispersion simulating light traversing curved glass.
                    // Absolutely NO color tint (pure white/ambient).
                    // -----------------------------------------------------------------
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.045f),
                                Color.White.copy(alpha = 0.012f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.45f, h * 0.30f),
                            radius = w * 0.55f
                        )
                    )

                    // -----------------------------------------------------------------
                    // C. CONVEX OPTICAL SHEEN (Surface Polish)
                    // Diagonal light sweep across polished curved glass face.
                    // -----------------------------------------------------------------
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0.00f to Color.White.copy(alpha = 0.08f),
                            0.28f to Color.White.copy(alpha = 0.025f),
                            0.55f to Color.Transparent,
                            0.82f to Color.White.copy(alpha = 0.015f),
                            1.00f to Color.Transparent,
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.85f, h)
                        ),
                        cornerRadius = cornerRadius
                    )

                    // Top cylindrical horizon reflection (upper 42%)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = h * 0.42f
                        ),
                        cornerRadius = cornerRadius
                    )

                    // -----------------------------------------------------------------
                    // D. INNER FRESNEL SCATTERING LIP (Glass Wall Depth)
                    // Delicate inner bevel giving the glass tangible physical thickness.
                    // -----------------------------------------------------------------
                    val insetPx = 1.2.dp.toPx()
                    val innerCornerRadius = CornerRadius(
                        (cornerRadiusPx - insetPx).coerceAtLeast(0f),
                        (cornerRadiusPx - insetPx).coerceAtLeast(0f)
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.16f)
                            ),
                            startY = insetPx,
                            endY = h - insetPx
                        ),
                        topLeft = Offset(insetPx, insetPx),
                        size = Size(w - insetPx * 2, h - insetPx * 2),
                        cornerRadius = innerCornerRadius,
                        style = Stroke(width = 0.75.dp.toPx())
                    )

                    // -----------------------------------------------------------------
                    // E. DIRECTIONAL SPECULAR EDGE REFLECTION (Physical Beveled Rim)
                    // Irregular natural optical variation:
                    // - Stronger top-left keylight glint & curved corner reflection
                    // - Soft bright top horizon edge
                    // - Subtle side grazing reflections
                    // - Soft darker refraction hairline along the bottom rim
                    // -----------------------------------------------------------------
                    // 1. Perimeter directional sweep
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0.00f to Color.White.copy(alpha = 0.75f), // Top-left corner: strongest glint
                            0.28f to Color.White.copy(alpha = 0.45f), // Top edge: clean highlight
                            0.55f to Color.White.copy(alpha = 0.15f), // Right curve: subtle grazing catch
                            0.78f to Color.Black.copy(alpha = 0.35f), // Bottom edge: soft dark refraction hairline
                            1.00f to Color.White.copy(alpha = 0.22f), // Left curve: gentle secondary reflection
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.90f, h)
                        ),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 1.1.dp.toPx())
                    )

                    // 2. Concentrated top-edge specular horizon glint
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            0.00f to Color.Transparent,
                            0.06f to Color.White.copy(alpha = 0.30f),
                            0.18f to Color.White.copy(alpha = 0.85f), // Peak glint near top-left curvature
                            0.45f to Color.White.copy(alpha = 0.50f),
                            0.78f to Color.White.copy(alpha = 0.25f),
                            1.00f to Color.Transparent,
                            startX = 0f,
                            endX = w
                        ),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 0.9.dp.toPx())
                    )

                    // 3. Lower edge dark refraction hairline
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.30f)
                            ),
                            startY = 0f,
                            endY = h
                        ),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                }
        )

        // =========================================================================
        // 8. CRISP PROFILE / HEADER CONTENT (Untouched)
        // =========================================================================
        content()
    }
}

