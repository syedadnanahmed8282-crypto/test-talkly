@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.family.talkly.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import com.family.talkly.util.AudioRecorder
import com.family.talkly.util.MediaCompressorAndUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import android.media.MediaPlayer
import android.net.Uri
import java.util.Locale
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.Divider
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.foundation.BorderStroke
import com.family.talkly.ui.theme.LocalIsDarkTheme
import com.family.talkly.ui.theme.WhatsappDarkBg
import com.family.talkly.ui.theme.WhatsappDarkBubble
import com.family.talkly.ui.theme.WhatsappDarkSurface
import com.family.talkly.ui.theme.WhatsappLightGreen
import com.family.talkly.ui.components.WallpaperSelectionDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.ReactionUtils
import com.family.talkly.data.models.ReactionEntry
import com.family.talkly.data.models.UserProfile
import androidx.compose.foundation.layout.wrapContentHeight
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.MessageRequest
import com.family.talkly.data.models.MessageType
import com.family.talkly.ui.components.ContactProfileDetailsDialog
import com.family.talkly.ui.components.FullMediaViewerDialog
import com.family.talkly.ui.components.MediaAttachmentDialog
import com.family.talkly.ui.components.MediaMessageItem
import com.family.talkly.ui.components.MessageLoadingState
import com.family.talkly.ui.theme.PrimaryDarkPurple
import com.family.talkly.ui.theme.ReceivedBubbleWhite
import com.family.talkly.ui.theme.ReceiverBubblePlum
import com.family.talkly.ui.theme.SecondaryLightSage
import com.family.talkly.ui.theme.SenderBubbleSage
import com.family.talkly.ui.theme.SentBubbleGreen
import com.family.talkly.ui.theme.WhatsappChatBg
import com.family.talkly.ui.theme.WhatsappGreen
import com.family.talkly.ui.theme.WhatsappTeal
import kotlin.math.roundToInt

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
    currentUserProfile: UserProfile? = null
) {
    val isDarkTheme = LocalIsDarkTheme.current
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
    val uploader = remember { MediaCompressorAndUploader(context) }
    val audioRecorder = remember { AudioRecorder(context) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var currentAudioFile by remember { mutableStateOf<File?>(null) }
    var isUploadingAudio by remember { mutableStateOf(false) }
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

    // Intercept back presses to close overlays or go back to chat list
    BackHandler(enabled = true) {
        when {
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

    // Auto debounce typing state (emit typing_start immediately, typing_stop after 1.5s inactivity)
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

    fun sendPendingMediaMessage(
        textContent: String,
        messageType: MessageType,
        localMediaUrl: String?
    ) {
        if (localMediaUrl.isNullOrBlank()) return
        val tempId = "temp_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val replyId = replyingToMessage?.id
        val replyName = replyingToMessage?.senderName
        val replyText = replyingToMessage?.textContent?.ifEmpty { "Media/Voice Message" }

        replyingToMessage = null

        val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository(context)
        val canonicalId = chatRepo.getCanonicalMemberId(member.id)

        com.family.talkly.util.MediaUploadManager.enqueueMediaUpload(
            context = context,
            messageId = tempId,
            chatKey = canonicalId,
            recipientId = member.id,
            messageType = messageType,
            localMediaUrl = localMediaUrl,
            textContent = textContent,
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

    val prefs = remember(context) { context.getSharedPreferences("talkly_prefs", Context.MODE_PRIVATE) }
    var wallpaperValue by remember(member.id) {
        mutableStateOf(
            prefs.getString("wallpaper_${member.id}", null)
                ?: prefs.getString("wallpaper_global", "#E5DDD5")
                ?: "#E5DDD5"
        )
    }

    val activeMessages = if (localClearedMessages) emptyList() else messages

    val combinedMessages = remember(activeMessages, localPendingMessages) {
        val serverIds = activeMessages.map { it.id }.toSet()
        activeMessages + localPendingMessages.filter { it.id !in serverIds }
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

    val pinnedMessage = remember(combinedMessages) {
        combinedMessages.lastOrNull { it.isPinned }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Mark active chat in notification helper & clear notifications on enter/exit
    androidx.compose.runtime.DisposableEffect(member.id) {
        com.family.talkly.util.TalklyNotificationHelper.activeChatMemberId = member.id
        com.family.talkly.util.TalklyNotificationHelper.cancelNotificationsForChat(context, member.id)
        onReadMessages()
        onDispose {
            if (com.family.talkly.util.TalklyNotificationHelper.activeChatMemberId == member.id) {
                com.family.talkly.util.TalklyNotificationHelper.activeChatMemberId = null
            }
        }
    }

    // Mark messages as read when opening or receiving new messages in chat screen
    LaunchedEffect(member.id, messages.size) {
        onReadMessages()
    }

    // Auto-scroll to latest message whenever new message arrives or user is typing
    var isInitialScrollDone by remember(member.id) { mutableStateOf(false) }
    var previousMessageCount by remember(member.id) { mutableStateOf(0) }

    LaunchedEffect(displayedMessages.size, member.isTyping) {
        if (displayedMessages.isNotEmpty()) {
            val targetIndex = displayedMessages.size - 1 + (if (member.isTyping) 1 else 0)
            if (targetIndex >= 0) {
                if (!isInitialScrollDone) {
                    listState.scrollToItem(targetIndex)
                    isInitialScrollDone = true
                } else if (displayedMessages.size > previousMessageCount || member.isTyping) {
                    listState.animateScrollToItem(targetIndex)
                } else {
                    listState.scrollToItem(targetIndex)
                }
            }
        }
        previousMessageCount = displayedMessages.size
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

    // Reaction & Reply Long-Click Dialog with 70% opacity translucent container, floating scrollable reactions, clean options and scale animation
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
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { reactionDialogMessage = null },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isAnimatedVisible,
                    enter = fadeIn(animationSpec = tween(250)) + scaleIn(
                        initialScale = 0.05f,
                        transformOrigin = if (selectedMsgIsTopHalf) TransformOrigin(0.5f, 0.2f) else TransformOrigin(0.5f, 0.8f),
                        animationSpec = tween(250)
                    ),
                    exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                        targetScale = 0.05f,
                        transformOrigin = if (selectedMsgIsTopHalf) TransformOrigin(0.5f, 0.2f) else TransformOrigin(0.5f, 0.8f),
                        animationSpec = tween(200)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .widthIn(max = 330.dp)
                            .clickable(enabled = false) {}, // Prevent dismiss when tapping inside popup content
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. FLOATING REACTION EMOJIS (ONLY FOR NON-DELETED MESSAGES)
                        if (!selectedMsg.isDeletedForEveryone) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("❤️", "😭", "😡", "😮", "👍", "👎", "🔥", "😂", "🥰", "🙏", "👏", "🎉", "💯", "😍", "✨", "💙").forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selectedMsg.reaction == emoji) Color.White.copy(alpha = 0.3f) else Color.Transparent
                                            )
                                            .clickable {
                                                onToggleReaction(selectedMsg.id, emoji)
                                                reactionDialogMessage = null
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 28.sp)
                                    }
                                }
                            }
                        }

                        // 2. MAIN POPUP CONTAINER (70% TRANSLUCENT SURFACE: COLOR 0xB3180E26 / 70% OPACITY)
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xB3180E26), // 70% opacity translucent dark purple
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            shadowElevation = 10.dp,
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
                                            color = WhatsappGreen.copy(alpha = 0.25f),
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
                                                    color = WhatsappGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }

                                        // Star / Unstar
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFFD54F).copy(alpha = 0.22f),
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
                                                    text = if (selectedMsg.isStarred) "Unstar" else "Star",
                                                    color = Color(0xFFFFC107),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }

                                        // Pin / Unpin
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = WhatsappTeal.copy(alpha = 0.25f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    val pinResult = onTogglePinMessage(selectedMsg.id)
                                                    reactionDialogMessage = null
                                                    if (pinResult) {
                                                        Toast.makeText(
                                                            context,
                                                            if (selectedMsg.isPinned) "Unpinned message" else "Pinned to top",
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
                                                    text = if (selectedMsg.isPinned) "Unpin" else "Pin",
                                                    color = Color(0xFF4DD0E1),
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
                                            color = WhatsappTeal.copy(alpha = 0.2f),
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
                                                Text(
                                                    text = "Edit message",
                                                    color = Color(0xFF80DEEA),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    // Delete for me (always available for all messages)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFE53935).copy(alpha = 0.18f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onDeleteForYou(selectedMsg.id)
                                                reactionDialogMessage = null
                                                Toast.makeText(context, "Deleted for you", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Delete for me",
                                                color = Color(0xFFEF5350),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    // Delete for everyone (only if sender and within 10 mins)
                                    if (isSelfMsg && isWithin10Mins && !selectedMsg.isDeletedForEveryone) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFE53935).copy(alpha = 0.22f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val success = onDeleteForEveryone(selectedMsg.id)
                                                    reactionDialogMessage = null
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
                                                Text(
                                                    text = "Delete for everyone",
                                                    color = Color(0xFFEF5350),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Bottom Right Cancel Button
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    TextButton(
                                        onClick = { reactionDialogMessage = null }
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold,
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
                color = if (isDarkTheme) WhatsappDarkSurface else Color.White,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reactions (${entries.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                        IconButton(
                            onClick = { reactionDetailsMessage = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Gray
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
                                label = { Text("All ${entries.size}") }
                            )
                            distinctEmojis.forEach { emoji ->
                                val count = entries.count { it.emoji == emoji }
                                FilterChip(
                                    selected = selectedEmojiFilter == emoji,
                                    onClick = { selectedEmojiFilter = emoji },
                                    label = { Text("$emoji $count") }
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
                            color = Color.Gray,
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
                                        .background(
                                            if (isDarkTheme) Color.White.copy(alpha = 0.05f)
                                            else Color.Black.copy(alpha = 0.03f)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(WhatsappTeal.copy(alpha = 0.2f)),
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
                                                color = WhatsappTeal
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isCurrentUser) "You" else entry.userName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = if (isDarkTheme) Color.White else Color.Black
                                        )
                                        Text(
                                            text = if (isCurrentUser) "Tap to remove" else entry.formattedTime,
                                            fontSize = 11.sp,
                                            color = if (isCurrentUser) WhatsappTeal else Color.Gray
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
                                                tint = Color.Red.copy(alpha = 0.7f),
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
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Starred Messages", fontWeight = FontWeight.Bold, color = WhatsappTeal)
                }
            },
            text = {
                val starredList = activeMessages.filter { it.isStarred }
                if (starredList.isEmpty()) {
                    Text(
                        "No starred messages in this chat yet.\n\nLong-press any message and tap Star ⭐ to save important notes!",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(starredList, key = { it.id }) { msg ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFF8E1),
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
                                            color = WhatsappTeal
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
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = msg.formattedTime,
                                        fontSize = 10.sp,
                                        color = Color.Gray,
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
                    Text("Close", color = WhatsappTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = {
                Text(
                    text = "Block ${member.name}?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111B21)
                )
            },
            text = {
                Text(
                    text = "Blocked contacts will no longer be able to send you messages or call you.",
                    color = Color.DarkGray,
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
                    Text("Block", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showClearChatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmDialog = false },
            title = {
                Text(
                    text = "Clear this chat?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111B21)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all messages in this conversation with ${member.name}?",
                    color = Color.DarkGray,
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
                    Text("Clear Chat", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showWallpaperDialog) {
        WallpaperSelectionDialog(
            currentValue = wallpaperValue,
            contactName = member.name,
            onDismiss = { showWallpaperDialog = false },
            onWallpaperSelected = { newValue, applyToAll ->
                showWallpaperDialog = false
                wallpaperValue = newValue
                if (applyToAll) {
                    prefs.edit()
                        .putString("wallpaper_global", newValue)
                        .putString("wallpaper_${member.id}", newValue)
                        .apply()
                } else {
                    prefs.edit()
                        .putString("wallpaper_${member.id}", newValue)
                        .apply()
                }
                Toast.makeText(context, "Wallpaper updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Scaffold(
        modifier = Modifier.onGloballyPositioned { coords ->
            chatWindowScreenHeight = coords.size.height.toFloat()
        },
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search messages...", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDarkPurple)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showContactProfile = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isMutualContact) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Masked Profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
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
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val displayName = member.firstName
                                    val nameFontSize = when {
                                        displayName.length > 22 -> 11.sp
                                        displayName.length > 16 -> 13.sp
                                        displayName.length > 10 -> 14.sp
                                        else -> 16.sp
                                    }
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = nameFontSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White
                                    )
                                    if (isMuted) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.VolumeMute,
                                            contentDescription = "Muted",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                val statusSubtext = when {
                                    !isMutualContact -> "Message request required"
                                    !member.isRegisteredOnTalkly -> "User not registered on Talkly"
                                    isBlocked -> "Blocked"
                                    member.isTyping -> "typing..."
                                    member.isRecentlyActive() -> "Online"
                                    else -> member.displayLastSeen
                                }
                                Text(
                                    text = statusSubtext,
                                    fontSize = 11.sp,
                                    fontWeight = if (member.isTyping && !isBlocked) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isMutualContact || !member.isRegisteredOnTalkly || isBlocked) Color(0xFFFFCDD2) else if (member.isTyping) Color(0xFF25D366) else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (!isMutualContact) {
                                Toast.makeText(context, "Cannot call: Message request must be accepted first", Toast.LENGTH_SHORT).show()
                            } else if (member.isRegisteredOnTalkly) {
                                onStartCall(CallType.AUDIO)
                            } else {
                                Toast.makeText(context, "User not registered on Talkly", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Audio Call",
                                tint = if (member.isRegisteredOnTalkly && isMutualContact) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = {
                            if (!isMutualContact) {
                                Toast.makeText(context, "Cannot call: Message request must be accepted first", Toast.LENGTH_SHORT).show()
                            } else if (member.isRegisteredOnTalkly) {
                                onStartCall(CallType.VIDEO)
                            } else {
                                Toast.makeText(context, "User not registered on Talkly", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video Call",
                                tint = if (member.isRegisteredOnTalkly && isMutualContact) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Contact Info", fontWeight = FontWeight.Medium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = WhatsappTeal
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showContactProfile = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Starred Messages", fontWeight = FontWeight.Medium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showStarredMessagesDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(if (member.isPinned) "Unpin Conversation" else "Pin Conversation", fontWeight = FontWeight.Medium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = WhatsappTeal
                                        )
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
                                    text = { Text("Wallpaper", fontWeight = FontWeight.Medium) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Wallpaper,
                                            contentDescription = null,
                                            tint = WhatsappTeal
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showWallpaperDialog = true
                                    }
                                )

                            DropdownMenuItem(
                                text = { Text(if (isMuted) "Unmute Notifications" else "Mute Notifications", fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                        contentDescription = null,
                                        tint = if (isMuted) WhatsappGreen else Color.Gray
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
                                        color = if (isBlocked) WhatsappTeal else Color(0xFFD32F2F)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isBlocked) WhatsappTeal else Color(0xFFD32F2F)
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
                                text = { Text("Clear Chat", fontWeight = FontWeight.Medium, color = Color(0xFFD32F2F)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showClearChatConfirmDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDarkPurple)
            )
            }
        }
    ) { innerPadding ->
        val isDarkTheme = LocalIsDarkTheme.current

        val isWallpaperImage = wallpaperValue.startsWith("http://") ||
                wallpaperValue.startsWith("https://") ||
                wallpaperValue.startsWith("content://") ||
                wallpaperValue.startsWith("file://")

        val parsedWallpaperColor = remember(wallpaperValue, isDarkTheme) {
            if (wallpaperValue.startsWith("#")) {
                try {
                    Color(android.graphics.Color.parseColor(wallpaperValue))
                } catch (e: Exception) {
                    if (isDarkTheme) WhatsappDarkBg else WhatsappChatBg
                }
            } else {
                if (isDarkTheme) WhatsappDarkBg else WhatsappChatBg
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .then(
                    if (!isWallpaperImage) Modifier.background(parsedWallpaperColor)
                    else Modifier
                )
        ) {
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
                        .background(Color.Black.copy(alpha = if (isDarkTheme) 0.38f else 0.12f))
                )
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Pinned Message Top Banner
            pinnedMessage?.let { pinned ->
                Surface(
                    color = Color(0xFFFFF9C4),
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val idx = displayedMessages.indexOfFirst { it.id == pinned.id }
                            if (idx >= 0) {
                                scope.launch { listState.animateScrollToItem(idx) }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
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
                                tint = WhatsappTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Pinned Announcement",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsappTeal
                                )
                                Text(
                                    text = pinned.textContent.ifEmpty { "[Media Attachment]" },
                                    fontSize = 12.sp,
                                    color = Color.Black,
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
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Chat Messages List
            if (isLoadingMessages) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    MessageLoadingState(
                        message = "Loading messages...",
                        subMessage = "Fetching end-to-end encrypted chat history"
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(displayedMessages, key = { it.id }) { msg ->
                        val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
                        val senderSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(msg.senderId)
                        val isMemberSender = (msg.senderId == member.id) ||
                                (!member.firebaseUid.isNullOrBlank() && msg.senderId == member.firebaseUid) ||
                                (member.phone.isNotBlank() && msg.senderId == member.phone) ||
                                (memberSuffix.isNotBlank() && memberSuffix == senderSuffix)

                        val isSelf = !isMemberSender
                        var offsetX by remember { mutableFloatStateOf(0f) }
                        var showReadDetails by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(msg.id) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (offsetX > 60f) {
                                                // Swipe left to right triggered reply
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
                        ) {
                            // Reply indicator icon when swiping
                            if (offsetX > 10f) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Swipe Reply",
                                    tint = WhatsappGreen,
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
                            } else {
                                if (isSelf) SenderBubbleSage else ReceiverBubblePlum
                            }

                            val bubbleTextColor = Color.White
                            val subTextColor = if (isSingleEmoji || isVoiceNote) {
                                if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF616161)
                            } else {
                                if (isSelf) Color(0xFFD8E6D6) else Color(0xFFD8C4DE)
                            }
                            val replyBgColor = Color.White.copy(alpha = 0.12f)
                            val replySenderColor = if (isSelf) Color(0xFFE0F0DE) else Color(0xFFE2C4EE)
                            val replySubTextColor = Color(0xFFF0F0F0)
                            var itemYInWindow by remember { mutableFloatStateOf(0f) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(offsetX.roundToInt(), 0) },
                                horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
                            ) {
                                Box {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = bubbleContainerColor
                                        ),
                                        border = if (isSingleEmoji || isVoiceNote) null else BorderStroke(
                                            width = 0.5.dp,
                                            color = Color.White.copy(alpha = 0.12f)
                                        ),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isSelf) 16.dp else 2.dp,
                                            bottomEnd = if (isSelf) 2.dp else 16.dp
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSingleEmoji || isVoiceNote) 0.dp else 1.dp),
                                        modifier = Modifier
                                            .widthIn(min = if (isSingleEmoji) 0.dp else 90.dp, max = 280.dp)
                                            .onGloballyPositioned { coords ->
                                                itemYInWindow = coords.positionInWindow().y
                                            }
                                            .combinedClickable(
                                                onClick = {
                                                    showReadDetails = !showReadDetails
                                                },
                                                onLongClick = {
                                                    selectedMsgIsTopHalf = (itemYInWindow < chatWindowScreenHeight / 2f)
                                                    reactionDialogMessage = msg
                                                }
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(if (isSingleEmoji) 2.dp else if (isVoiceNote) 0.dp else 10.dp)) {
                                            if (msg.isDeletedForEveryone) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Block,
                                                        contentDescription = "Deleted",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "This message was deleted",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            color = Color.Gray,
                                                            fontStyle = FontStyle.Italic,
                                                            fontSize = 14.sp
                                                        )
                                                    )
                                                }
                                            } else {
                                                // Quoted Reply Preview inside Message Bubble
                                                if (msg.replyToSenderName != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = replyBgColor,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(6.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(3.dp)
                                                                .height(28.dp)
                                                                .background(replySenderColor, RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Column {
                                                            Text(
                                                                text = msg.replyToSenderName,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = replySenderColor
                                                            )
                                                            Text(
                                                                text = msg.replyToText ?: "Media message",
                                                                fontSize = 11.sp,
                                                                color = replySubTextColor,
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
                                                            val chatRepo = com.family.talkly.data.firebase.FirebaseChatRepository(context)
                                                            val canonicalId = chatRepo.getCanonicalMemberId(member.id)
                                                            com.family.talkly.util.MediaUploadManager.enqueueMediaUpload(
                                                                context = context,
                                                                messageId = msg.id,
                                                                chatKey = canonicalId,
                                                                recipientId = member.id,
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
                                                        color = bubbleTextColor,
                                                        fontSize = if (isSingleEmoji) 44.sp else 15.sp
                                                    )
                                                )
                                            }
                                        }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Timestamp & Read Receipt Double Tick
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
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                }
                                                if (msg.isPinned) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = "Pinned",
                                                        tint = if (isDarkTheme) WhatsappLightGreen else WhatsappTeal,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                }
                                                Text(
                                                    text = if (msg.isEdited) "${msg.formattedTime} • Edited" else msg.formattedTime,
                                                    fontSize = 10.sp,
                                                    color = subTextColor
                                                )
                                                if (isSelf) {
                                                    Spacer(modifier = Modifier.width(4.dp))
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
                                                        animationSpec = tween(durationMillis = 350),
                                                        label = "StatusFadeAnimation"
                                                    ) { state ->
                                                        when (state) {
                                                            3 -> {
                                                                CircularProgressIndicator(
                                                                    strokeWidth = 1.5.dp,
                                                                    color = subTextColor,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                            }
                                                            2 -> {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(16.dp)
                                                                        .background(Color(0xFF25D366), CircleShape),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.DoneAll,
                                                                        contentDescription = "Seen",
                                                                        tint = Color.White,
                                                                        modifier = Modifier.size(11.dp)
                                                                    )
                                                                }
                                                            }
                                                            1 -> {
                                                                Icon(
                                                                    imageVector = Icons.Default.DoneAll,
                                                                    contentDescription = "Delivered",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                            else -> {
                                                                Icon(
                                                                    imageVector = Icons.Default.Done,
                                                                    contentDescription = "Sent",
                                                                    tint = subTextColor,
                                                                    modifier = Modifier.size(15.dp)
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
                                                    color = subTextColor,
                                                    modifier = Modifier.align(Alignment.End)
                                                )
                                            }
                                        }
                                    }

                                    // Reaction Badge Pill Overlay
                                    msg.reaction?.let { reactEmoji ->
                                        val entries = remember(reactEmoji) { ReactionUtils.parseReactions(reactEmoji, msg.senderId, msg.senderName, msg.timestamp) }
                                        if (entries.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isDarkTheme) WhatsappDarkSurface else Color.White,
                                                border = BorderStroke(0.5.dp, if (isDarkTheme) Color.White.copy(alpha = 0.20f) else Color.LightGray.copy(alpha = 0.5f)),
                                                tonalElevation = 4.dp,
                                                shadowElevation = 2.dp,
                                                modifier = Modifier
                                                    .align(if (isSelf) Alignment.BottomStart else Alignment.BottomEnd)
                                                    .offset(y = 8.dp, x = if (isSelf) (-6).dp else 6.dp)
                                                    .clickable {
                                                        reactionDetailsMessage = msg
                                                    }
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
                                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.7f)
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

                    // Live Typing Bubble Indicator
                    if (member.isTyping) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ReceivedBubbleWhite),
                                    shape = RoundedCornerShape(18.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${member.name} is typing",
                                            fontSize = 13.sp,
                                            color = WhatsappGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        AnimatedTypingDotsIndicator(dotColor = WhatsappGreen)
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            // Editing Banner Bar directly above input field
            AnimatedVisibility(
                visible = editingMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                editingMessage?.let { editMsg ->
                    Surface(
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, WhatsappGreen.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(36.dp)
                                        .background(WhatsappGreen, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Editing Message ✏️",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WhatsappGreen
                                    )
                                    Text(
                                        text = editMsg.textContent,
                                        fontSize = 12.sp,
                                        color = Color.DarkGray,
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
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel edit",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Replying Banner Bar directly above input field
            AnimatedVisibility(
                visible = replyingToMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                replyingToMessage?.let { replyMsg ->
                    Surface(
                        color = Color(0xFFF0F2F5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color.LightGray)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(36.dp)
                                        .background(WhatsappGreen, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Replying to ${replyMsg.senderName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WhatsappGreen
                                    )
                                    Text(
                                        text = replyMsg.textContent.ifEmpty { "Media photo/video" },
                                        fontSize = 12.sp,
                                        color = Color.DarkGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { replyingToMessage = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel reply",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Input Bar or Blocked/Unregistered Contact Banner
            if (!member.isRegisteredOnTalkly) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    tonalElevation = 4.dp,
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
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "User not registered on Talkly",
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (isBlocked) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    tonalElevation = 4.dp,
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
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "You blocked this contact. Tap to unblock.",
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (!isMutualContact) {
                Surface(
                    color = Color(0xFFF0F2F5),
                    tonalElevation = 6.dp,
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
                                    tint = WhatsappTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Message Request Sent",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF111B21)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Waiting for ${member.name} to accept your request and save your contact.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        } else if (pendingMessageRequest != null) {
                            Text(
                                text = "${member.name} sent you a message request",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF111B21)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"${pendingMessageRequest.initialMessage}\"",
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { onAcceptMessageRequest(pendingMessageRequest) },
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsappGreen),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text("Accept & Save Contact", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onDeclineMessageRequest(pendingMessageRequest.id) },
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text("Decline", color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            var requestInputText by remember { mutableStateOf("Hello, I would like to connect on Talkly!") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = WhatsappTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send Message Request",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF111B21)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "You and ${member.name} are not mutual contacts. Send a message request to unlock chat, calls, and status updates.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = requestInputText,
                                onValueChange = { requestInputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Write message request...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onSendMessageRequest(requestInputText) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsappTeal),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Message Request", color = Color.White, fontWeight = FontWeight.Bold)
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
                Surface(
                    color = Color(0xFFFFF3F3),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = String.format("Recording %d:%02d", recordingDurationSec / 60, recordingDurationSec % 60),
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { cancelVoicePreview() }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Cancel Recording",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FloatingActionButton(
                                onClick = { stopAndPreparePreview() },
                                containerColor = WhatsappGreen,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Finish Recording & Preview",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showAttachmentDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = "Attach",
                                tint = WhatsappTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = {
                                textInput = it
                                onTypingStateChanged(it.isNotBlank())
                            },
                            placeholder = { Text("Type family message...", fontSize = 14.sp, color = Color.Gray) },
                            textStyle = TextStyle(
                                color = Color(0xFF111B21),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
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
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF111B21),
                                unfocusedTextColor = Color(0xFF111B21),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedBorderColor = WhatsappGreen
                            ),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        FloatingActionButton(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    if (editingMessage != null) {
                                        val success = onEditMessage(editingMessage!!.id, textInput)
                                        if (!success) {
                                            Toast.makeText(context, "১০ মিনিট পার হয়ে যাওয়ায় এডিট করা সম্ভব নয়", Toast.LENGTH_SHORT).show()
                                        }
                                        editingMessage = null
                                    } else {
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
                                } else {
                                    startVoiceRecording()
                                }
                            },
                            containerColor = WhatsappGreen,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (textInput.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Outlined.Mic,
                                contentDescription = if (textInput.isNotBlank()) "Send" else "Record Audio",
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
    dotColor: Color = WhatsappGreen,
    dotSize: androidx.compose.ui.unit.Dp = 5.dp
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing_dots")

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 1000
                0.2f at 0
                1.0f at 250
                0.2f at 500
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "dot1"
    )

    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 1000
                0.2f at 200
                1.0f at 450
                0.2f at 700
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "dot2"
    )

    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 1000
                0.2f at 400
                1.0f at 650
                0.2f at 900
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
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
        color = Color(0xFFF0F4F2),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = WhatsappGreen,
                modifier = Modifier
                    .size(38.dp)
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
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preview Voice Note",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WhatsappTeal
                    )
                    val dispMs = if (isPlaying || currentPosMs.value > 0) currentPosMs.value else totalDurationMs.value
                    val secs = (dispMs / 1000).toInt()
                    Text(
                        text = String.format(Locale.getDefault(), "%d:%02d", secs / 60, secs % 60),
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                com.family.talkly.ui.components.AudioWaveformBar(
                    progress = progress,
                    isPlaying = isPlaying,
                    seed = audioFile.hashCode(),
                    activeColor = WhatsappGreen,
                    inactiveColor = Color.Gray.copy(alpha = 0.35f),
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

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Discard Recording",
                    tint = Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            FloatingActionButton(
                onClick = onSend,
                containerColor = WhatsappGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Voice Note",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
