package com.family.talkly

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
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
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.talkly.data.auth.AuthManager
import com.family.talkly.data.auth.AuthState
import com.family.talkly.data.firebase.FirebaseChatRepository
import com.family.talkly.data.zego.ZegoCallEngineManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        authManager = AuthManager(applicationContext)
        chatRepository = FirebaseChatRepository(applicationContext)
        zegoManager = ZegoCallEngineManager(applicationContext)
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

        setContent {
            val currentThemeMode by themePreferences.themeMode.collectAsState()

            TalklyTheme(themeMode = currentThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by authManager.authState.collectAsState()

                    when (val state = authState) {
                        is AuthState.InitialCheck -> {
                            AuthLoadingState(
                                message = "Talkly Family Messenger",
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
                                }
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
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCallIntent(intent)
    }

    private fun handleIncomingCallIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val isOpenCall = intent.getBooleanExtra("open_incoming_call", false)
        if (isOpenCall) {
            val callerName = intent.getStringExtra("caller_name") ?: "Talkly User"
            val callerUid = intent.getStringExtra("caller_uid") ?: ""
            val callerPhone = intent.getStringExtra("caller_phone") ?: ""
            val callerAvatar = intent.getStringExtra("caller_avatar") ?: ""
            val roomId = intent.getStringExtra("room_id") ?: ""
            val callTypeStr = intent.getStringExtra("call_type") ?: "VIDEO"

            val prefs = getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
            val fallbackPrefs = getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
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

            com.family.talkly.service.CallForegroundService.stopCallService(applicationContext)

            if (roomId.isNotBlank()) {
                val callType = try {
                    com.family.talkly.data.models.CallType.valueOf(callTypeStr)
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
                    Log.d("MainActivity", "App entered FOREGROUND (ProcessLifecycleOwner.onStart) -> triggering chatRepository.forceReconnectListeners")
                    chatRepository.forceReconnectListeners("app_foreground")
                }

                override fun onStop(owner: LifecycleOwner) {
                    super.onStop(owner)
                    Log.d("MainActivity", "App entered BACKGROUND (ProcessLifecycleOwner.onStop)")
                }
            })
            Log.d("MainActivity", "ProcessLifecycleOwner observer registered successfully")
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to register ProcessLifecycleOwner observer: ${e.localizedMessage}")
        }

        // 2. ConnectivityManager.NetworkCallback to detect network change / reconnect
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d("MainActivity", "Network available/reconnected (NetworkCallback.onAvailable) -> triggering chatRepository.forceReconnectListeners")
                        chatRepository.forceReconnectListeners("network_available")
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.d("MainActivity", "Network connection lost (NetworkCallback.onLost)")
                    }
                }
                connectivityManager.registerNetworkCallback(request, networkCallback!!)
                Log.d("MainActivity", "NetworkCallback registered successfully")
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to register NetworkCallback: ${e.localizedMessage}")
        }
    }

    override fun onDestroy() {
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
