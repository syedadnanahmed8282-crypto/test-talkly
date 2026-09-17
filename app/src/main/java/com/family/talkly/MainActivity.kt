package com.family.talkly

import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.family.talkly.data.models.CallType
import com.family.talkly.data.zego.CallState
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.family.talkly.data.auth.AuthManager
import com.family.talkly.data.auth.AuthState
import com.family.talkly.data.firebase.FirebaseChatRepository
import com.family.talkly.data.zego.ZegoCallEngineManager
import com.family.talkly.debug.CrashHandler
import com.family.talkly.debug.DebugLogDialog
import com.family.talkly.ui.components.AuthLoadingState
import com.family.talkly.ui.screens.MainScreen
import com.family.talkly.ui.screens.auth.PhonePasswordAuthScreen
import com.family.talkly.ui.screens.auth.ProfileSetupScreen
import com.family.talkly.ui.theme.TalklyTheme
import com.family.talkly.ui.theme.ThemePreferences
import com.family.talkly.ui.theme.WhatsappGreen
import com.family.talkly.ui.theme.WhatsappTeal
import com.family.talkly.workers.DeleteExpiredMessagesWorker
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var chatRepository: FirebaseChatRepository
    private lateinit var zegoManager: ZegoCallEngineManager
    private lateinit var themePreferences: ThemePreferences
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingOpenChatMemberId by mutableStateOf<String?>(null)
    private var isInPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ===== DEBUG: install crash handler FIRST so it catches everything after this point =====
        CrashHandler.install(applicationContext)
        com.family.talkly.service.MessageSyncForegroundService.start(applicationContext)

        enableEdgeToEdge()

        // Turn screen on and show over lockscreen for incoming call wake-up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        try {
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                FirebaseApp.initializeApp(applicationContext)
                android.util.Log.d("MainActivity", "FirebaseApp initialized in MainActivity.onCreate")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "FirebaseApp initialization check in MainActivity: ${e.localizedMessage}")
        }

        authManager = AuthManager.getInstance(applicationContext)
        chatRepository = FirebaseChatRepository.getInstance(applicationContext)
        zegoManager = ZegoCallEngineManager.getInstance(applicationContext)
        themePreferences = ThemePreferences(applicationContext)

        com.family.talkly.util.TalklyNotificationHelper.initNotificationChannels(applicationContext)
        com.family.talkly.util.FcmTokenManager.syncFcmToken(applicationContext)

        // Setup lifecycle & network-aware realtime reconnection for messages
        setupLifecycleAndNetworkSync()

        // Request battery optimization exemption for uninterrupted push delivery
        requestBatteryOptimizationExemption()

        // Schedule WorkManager job for deleting expired Firestore messages (>48 hours old)
        DeleteExpiredMessagesWorker.schedulePeriodicCleanup(applicationContext)

        handleIncomingCallIntent(intent)
        handleOpenChatIntent(intent)

        // Setup native Picture-in-Picture sync for active video calls
        setupPipLifecycleSync()

        setContent {
            val currentThemeMode by themePreferences.themeMode.collectAsState()

            TalklyTheme(themeMode = currentThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // ===== DEBUG: floating log button + dialog, visible on every screen =====
                    var showDebugLog by remember { mutableStateOf(false) }
                    var detectedCrashReport by remember { mutableStateOf<String?>(null) }

                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        val previousCrash = com.family.talkly.debug.CrashHandler.getLastCrash(applicationContext)
                        if (!previousCrash.isNullOrBlank()) {
                            detectedCrashReport = previousCrash
                        }
                    }

                    if (detectedCrashReport != null) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = {
                                com.family.talkly.debug.CrashHandler.clearLastCrash(applicationContext)
                                detectedCrashReport = null
                            },
                            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            com.family.talkly.debug.CrashDisplayScreen(
                                crashInfo = detectedCrashReport!!,
                                onCopy = {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Payra Crash Log", detectedCrashReport)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(applicationContext, "ক্র্যাশ রিপোর্ট কপি করা হয়েছে!", android.widget.Toast.LENGTH_LONG).show()
                                },
                                onRestartApp = {
                                    com.family.talkly.debug.CrashHandler.clearLastCrash(applicationContext)
                                    detectedCrashReport = null
                                }
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        val authState by authManager.authState.collectAsState()

                        when (val state = authState) {
                            is AuthState.InitialCheck -> {
                                AuthLoadingState(
                                    message = "Payra Family Messenger",
                                    subMessage = "Checking authentication session..."
                                )
                            }

                            is AuthState.Unauthenticated -> {
                                PhonePasswordAuthScreen(
                                    isLoading = false,
                                    errorMessage = null,
                                    onSignIn = { phone, password ->
                                        authManager.signInWithPhoneAndPassword(phone, password)
                                    },
                                    onSignUp = { phone, password, name ->
                                        authManager.signUpWithPhoneAndPassword(
                                            phoneNumber = phone,
                                            password = password,
                                            name = name
                                        )
                                    },
                                    onForgotPassword = { phone, onSuccess, onError ->
                                        authManager.sendPasswordResetForPhone(
                                            phoneNumber = phone,
                                            onSuccess = onSuccess,
                                            onError = onError
                                        )
                                    },
                                    onClearError = {
                                        authManager.clearError()
                                    }
                                )
                            }

                            is AuthState.VerificationInProgress -> {
                                AuthLoadingState(
                                    message = state.message,
                                    subMessage = "Please wait a moment while we process your request"
                                )
                            }

                            is AuthState.ProfileSetupRequired -> {
                                ProfileSetupScreen(
                                    phoneNumber = state.phoneNumber,
                                    isLoading = false,
                                    errorMessage = null,
                                    onSaveProfile = { name, picUrl ->
                                        authManager.saveUserProfile(
                                            name = name,
                                            profilePicUrl = picUrl,
                                            onSuccess = {},
                                            onError = {}
                                        )
                                    }
                                )
                            }

                            is AuthState.Authenticated -> {
                                MainScreen(
                                    chatRepository = chatRepository,
                                    zegoManager = zegoManager,
                                    currentUserProfile = state.profile,
                                    currentThemeMode = currentThemeMode,
                                    onThemeModeChange = { mode ->
                                        themePreferences.setThemeMode(mode)
                                    },
                                    onLogout = {
                                        chatRepository.resetSessionOnLogout()
                                        zegoManager.clearSession()
                                        authManager.logout()

                                        val intent = android.content.Intent(this@MainActivity, MainActivity::class.java).apply {
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        }
                                        startActivity(intent)
                                        finish()
                                    },
                                    onSaveProfile = { name, bio, picUrl, coverUrl ->
                                        authManager.saveUserProfile(
                                            name = name,
                                            profilePicUrl = picUrl,
                                            bio = bio,
                                            coverPhotoUrl = coverUrl
                                        )
                                    },
                                    initialOpenChatMemberId = pendingOpenChatMemberId,
                                    onClearOpenChatMemberId = { pendingOpenChatMemberId = null },
                                    isInPipMode = isInPipMode
                                )
                            }

                            is AuthState.Error -> {
                                PhonePasswordAuthScreen(
                                    isLoading = false,
                                    errorMessage = state.message,
                                    onSignIn = { phone, password ->
                                        authManager.signInWithPhoneAndPassword(phone, password)
                                    },
                                    onSignUp = { phone, password, name ->
                                        authManager.signUpWithPhoneAndPassword(
                                            phoneNumber = phone,
                                            password = password,
                                            name = name
                                        )
                                    },
                                    onForgotPassword = { phone, onSuccess, onError ->
                                        authManager.sendPasswordResetForPhone(
                                            phoneNumber = phone,
                                            onSuccess = onSuccess,
                                            onError = onError
                                        )
                                    },
                                    onClearError = {
                                        authManager.clearError()
                                    }
                                )
                            }
                        }

                        // Small floating debug button: visible ONLY on Login/Auth screens, hidden after authentication and in PiP mode
                        val isAuthenticated = authState is AuthState.Authenticated
                        if (!isAuthenticated && !isInPipMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 48.dp, end = 12.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xAA000000))
                                    .clickable { showDebugLog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🐞", fontSize = 16.sp)
                            }
                        }

                        if (showDebugLog) {
                            DebugLogDialog(onDismiss = { showDebugLog = false })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCallIntent(intent)
        handleOpenChatIntent(intent)
    }

    private fun handleOpenChatIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val chatMemberId = intent.getStringExtra("open_chat_member_id")
        if (!chatMemberId.isNullOrBlank()) {
            pendingOpenChatMemberId = chatMemberId
        }
    }

    private fun handleIncomingCallIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val isOpenCall = intent.getBooleanExtra("open_incoming_call", false)
        if (isOpenCall) {
            val callerName = intent.getStringExtra("caller_name") ?: "Payra User"
            val callerUid = intent.getStringExtra("caller_uid") ?: ""
            val callerPhone = intent.getStringExtra("caller_phone") ?: ""
            val callerAvatar = intent.getStringExtra("caller_avatar") ?: ""
            val roomId = intent.getStringExtra("room_id") ?: ""
            val callTypeStr = intent.getStringExtra("call_type") ?: "VIDEO"

            val prefs = getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
            val fallbackPrefs = getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
            val currentUid = com.family.talkly.data.supabase.SupabaseClientProvider.auth.currentUserOrNull()?.id
                ?: prefs.getString("user_uid", null)
                ?: fallbackPrefs.getString("user_uid", null) ?: ""
            val currentPhone = prefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", null) ?: ""
            val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)
            val callerSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(callerPhone)

            val isSelfCall = (currentUid.isNotBlank() && currentUid != "self" && callerUid == currentUid) ||
                    (currentPhone.isNotBlank() && callerPhone.isNotBlank() && callerPhone == currentPhone) ||
                    (currentSuffix.isNotBlank() && callerSuffix.isNotBlank() && callerSuffix == currentSuffix)

            if (isSelfCall) {
                android.util.Log.d("MainActivity", "CLIENT-SIDE GUARD: Discarding open_incoming_call intent for self-call (callerUid=$callerUid)")
                return
            }

            val currentCall = zegoManager.callState.value
            val isUserBusy = (currentCall.state != com.family.talkly.data.zego.CallState.IDLE &&
                    currentCall.state != com.family.talkly.data.zego.CallState.ENDED &&
                    currentCall.roomID.isNotBlank() &&
                    currentCall.roomID != roomId)

            if (isUserBusy) {
                android.util.Log.w("MainActivity", "[CALL_BUSY] Discarding open_incoming_call intent for room $roomId because user is already in call (${currentCall.state}, room=${currentCall.roomID})")
                return
            }

            com.family.talkly.service.CallForegroundService.stopCallService(applicationContext, roomId)

            if (roomId.isNotBlank()) {
                val callType = try {
                    com.family.talkly.data.models.CallType.valueOf(callTypeStr.uppercase())
                } catch (e: Exception) {
                    com.family.talkly.data.models.CallType.VIDEO
                }
                val incomingMember = com.family.talkly.data.models.FamilyMember(
                    id = if (callerPhone.isNotBlank()) com.family.talkly.util.PhoneUtils.extractPhoneSuffix(callerPhone) else callerUid,
                    name = callerName,
                    phone = callerPhone,
                    relation = "Family Member",
                    status = "Incoming call...",
                    avatarUrl = callerAvatar.ifBlank { null },
                    isOnline = true,
                    firebaseUid = callerUid,
                    isRegisteredOnTalkly = true
                )
                zegoManager.setIncomingCallFromKilledState(incomingMember, roomId, callType)
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Battery optimization request failed/ignored: ${e.localizedMessage}")
        }
    }

    private fun setupLifecycleAndNetworkSync() {
        // 1. ProcessLifecycleOwner observer to detect app background -> foreground transitions
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    super.onStart(owner)
                    val authState = authManager.authState.value
                    if (authState is AuthState.Authenticated) {
                        val profile = authState.profile
                        if (profile.uid.isNotBlank() && profile.uid != "self") {
                            Log.d("MainActivity", "App entered FOREGROUND -> triggering chatRepository.forceReconnectListeners and zegoManager.reconnectCallSync for ${profile.uid}")
                            chatRepository.forceReconnectListeners("app_foreground")
                            zegoManager.reconnectCallSync()
                            chatRepository.setMemberPresence(
                                memberId = profile.uid,
                                isOnline = true,
                                lastSeen = "Online",
                                lastActiveTimestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }

                override fun onStop(owner: LifecycleOwner) {
                    super.onStop(owner)
                    Log.d("MainActivity", "App entered BACKGROUND (ProcessLifecycleOwner.onStop)")
                    val authState = authManager.authState.value
                    if (authState is AuthState.Authenticated) {
                        val profile = authState.profile
                        if (profile.uid.isNotBlank() && profile.uid != "self") {
                            chatRepository.setMemberPresence(
                                memberId = profile.uid,
                                isOnline = false,
                                lastSeen = com.family.talkly.util.PhoneUtils.formatLastSeenTime(System.currentTimeMillis()),
                                lastActiveTimestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }
            })
            Log.d("MainActivity", "ProcessLifecycleOwner observer registered successfully")
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to register ProcessLifecycleOwner observer: ${e.localizedMessage}")
        }

        // 2. Network connectivity monitoring is centralized in FirebaseChatRepository.
        // ProcessLifecycleOwner foreground observer (above) handles app foreground transitions.
    }

    private fun setupPipLifecycleSync() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                zegoManager.callState.collect { callInfo ->
                    val isActiveVideo = (callInfo.state == CallState.ACTIVE && callInfo.callType == CallType.VIDEO)
                    updatePipParams(isActiveVideo)
                    if (isInPipMode && (callInfo.state == CallState.ENDED || callInfo.state == CallState.IDLE)) {
                        restoreFromPipIfEnded()
                    }
                }
            }
        }
    }

    private fun updatePipParams(isActiveVideoCall: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams(autoEnter = isActiveVideoCall)
                if (params != null) {
                    setPictureInPictureParams(params)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to set PictureInPictureParams: ${e.message}")
            }
        }
    }

    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(autoEnter)
                builder.setSeamlessResizeEnabled(true)
            }
            return builder.build()
        }
        return null
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isAlreadyInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
            if (!isAlreadyInPip) {
                val call = zegoManager.callState.value
                if (call.state == CallState.ACTIVE && call.callType == CallType.VIDEO) {
                    try {
                        val params = buildPipParams(autoEnter = true)
                        if (params != null) {
                            enterPictureInPictureMode(params)
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "onUserLeaveHint enterPictureInPictureMode failed: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        Log.d("MainActivity", "onPictureInPictureModeChanged: isInPipMode=$isInPictureInPictureMode")
    }

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        @Suppress("DEPRECATION")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        isInPipMode = isInPictureInPictureMode
    }

    private fun restoreFromPipIfEnded() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to restore from PiP: ${e.message}")
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            Log.d("MainActivity", "Activity destroyed while in PiP -> ending active call")
            try {
                zegoManager.endCall()
                com.family.talkly.service.CallForegroundService.stopCallService(applicationContext)
            } catch (e: Exception) {
                Log.w("MainActivity", "Error ending call on PiP destroy: ${e.localizedMessage}")
            }
        }
        super.onDestroy()
        try {
            networkCallback?.let {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Error unregistering NetworkCallback: ${e.localizedMessage}")
        }
    }
}
