package com.family.talkly.data.zego

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import android.view.View
import com.family.talkly.BuildConfig
import com.family.talkly.data.firebase.FirebaseChatRepository
import com.family.talkly.data.models.CallDirection
import com.family.talkly.data.models.CallLog
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.UserProfile
import com.family.talkly.data.supabase.SupabaseActiveCall
import com.family.talkly.data.supabase.SupabaseCallLog
import com.family.talkly.data.supabase.SupabaseCallService
import com.family.talkly.data.supabase.SupabaseClientProvider
import com.family.talkly.data.supabase.SupabaseMessage
import com.family.talkly.data.supabase.SupabaseMessagingService
import com.family.talkly.util.CallSoundManager
import com.family.talkly.util.PhoneUtils
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.BeautyOptions
import io.agora.rtc2.video.VideoCanvas
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class CallState {
    IDLE,
    OUTGOING_CALLING,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    ACTIVE,
    ENDED
}

data class CurrentCallInfo(
    val state: CallState = CallState.IDLE,
    val callType: CallType = CallType.VIDEO,
    val targetMember: FamilyMember? = null,
    val roomID: String = "",
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isCameraOff: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isSpeakerOn: Boolean = true,
    val zegoAppId: Long = 0L,
    val zegoAppSign: String = "",
    val isZegoInitialized: Boolean = true,
    val usePrebuiltCallUi: Boolean = false,
    val zegoUIKitAppId: Long = 0L,
    val zegoUIKitAppSign: String = "",
    val stunServers: List<String> = emptyList(),
    val turnUsername: String = "openrelayproject",
    val turnPassword: String = "openrelayproject",
    val offerToReceiveAudio: Boolean = true,
    val offerToReceiveVideo: Boolean = true,
    val isRemoteAudioTrackAttached: Boolean = true,
    val isRemoteVideoTrackAttached: Boolean = true,
    val isMicrophoneMuted: Boolean = false,
    val isPublishAudioMuted: Boolean = false,
    val localStreamId: String = "",
    val remoteStreamId: String = "",
    val isRemoteStreamPlaying: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isSpeakerMuted: Boolean = false,
    val isOutgoing: Boolean = false
)

class ZegoCallEngineManager(private val context: Context) {

    companion object {
        const val TAG = "Talkly_AgoraEngine"
        val PUBLIC_STUN_SERVERS: List<String> = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302"
        )
        val ZEGO_APP_ID: Long = 196267710L
        val ZEGO_APP_SIGN: String = "620de7961f58b3a6f8390c8a484233ff60aafd59a6f4bc8a538b25c502fd4403"

        val AGORA_APP_ID: String by lazy {
            val id = try {
                BuildConfig.AGORA_APP_ID
            } catch (e: Exception) {
                ""
            }
            if (id.isNullOrBlank()) "80b874784d164d9aba38cea4626ba400" else id
        }

        @Volatile
        private var INSTANCE: ZegoCallEngineManager? = null

        fun getInstance(context: Context): ZegoCallEngineManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ZegoCallEngineManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Derives a deterministic positive 31-bit integer UID from a user UUID string.
         * Agora numeric UIDs must be 32-bit unsigned integers (1 to 2,147,483,647).
         */
        fun getAgoraNumericUid(userUid: String): Int {
            if (userUid.isBlank() || userUid == "self") {
                return ((System.currentTimeMillis() % 1_000_000) + 1).toInt()
            }
            val crc = java.util.zip.CRC32()
            crc.update(userUid.toByteArray(Charsets.UTF_8))
            val value = (crc.value and 0x7FFFFFFFL).toInt()
            return if (value == 0) 1 else value
        }
    }

    private val callSoundManager = CallSoundManager(context)
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private var rtcEngine: RtcEngine? = null
    private var isJoinedRoom = false
    private var localViewRef: View? = null
    private var remoteViewRef: View? = null
    private var remoteUid: Int = 0

    private var callsRealtimeChannel: RealtimeChannel? = null
    private var callSyncJob: Job? = null
    private var currentSyncedUserId: String? = null
    private var lastCallReconnectTimestamp = 0L
    private var lastCallSubscribedTimestamp = 0L
    @Volatile
    private var isSubscribingCalls = false

    var currentUserProfile: UserProfile? = null
    var chatRepository: FirebaseChatRepository? = null

    private val _callState = MutableStateFlow(CurrentCallInfo())
    val callState: StateFlow<CurrentCallInfo> = _callState.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLog>>(emptyList())
    val callLogs: StateFlow<List<CallLog>> = _callLogs.asStateFlow()

    private var timerJob: Job? = null
    private var ringingTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val callScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var onCallLogAdded: ((CallLog) -> Unit)? = null

    init {
        Log.i(TAG, "Agora RTC Engine Manager initialized with AppID: $AGORA_APP_ID")
        initRtcEngine(context)

        // Safety lifecycle state manager listener: force stop outgoing/incoming sounds on connected/ended state transitions
        scope.launch {
            _callState.collect { info ->
                when (info.state) {
                    CallState.ACTIVE, CallState.ENDED, CallState.IDLE -> {
                        callSoundManager.stopAllSounds()
                    }
                    else -> {}
                }
            }
        }
    }

    @Synchronized
    private fun initRtcEngine(ctx: Context) {
        if (rtcEngine != null) return
        try {
            val app = ctx.applicationContext as? Application ?: return
            val config = RtcEngineConfig().apply {
                mContext = app
                mAppId = AGORA_APP_ID
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        Log.i(TAG, "[AGORA_SEQ_5] onJoinChannelSuccess: channel=$channel, uid=$uid, elapsed=$elapsed")
                    }

                    override fun onConnectionStateChanged(state: Int, reason: Int) {
                        Log.i(
                            TAG,
                            "[AGORA_SEQ_7] onConnectionStateChanged: state=$state (${getConnectionStateName(state)}), reason=$reason (${getConnectionChangedReasonName(reason)})"
                        )
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.i(TAG, "[AGORA_SEQ_8] onUserJoined: uid=$uid, elapsed=$elapsed")
                        scope.launch(Dispatchers.Main) {
                            val currentState = _callState.value.state
                            if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING) {
                                Log.d(TAG, "Remote peer joined channel: stopping outgoing ringtone immediately")
                                ringingTimeoutJob?.cancel()
                                callSoundManager.stopAllSounds()
                                val isVideo = (_callState.value.callType == CallType.VIDEO)
                                callSoundManager.configureAudioForActiveCall(
                                    isSpeakerOn = isVideo,
                                    isMuted = _callState.value.isMuted
                                )
                                _callState.value = _callState.value.copy(
                                    state = CallState.ACTIVE,
                                    isSpeakerOn = isVideo,
                                    remoteStreamId = uid.toString(),
                                    isRemoteStreamPlaying = true
                                )
                                startCallTimer()
                            } else {
                                _callState.value = _callState.value.copy(
                                    remoteStreamId = uid.toString(),
                                    isRemoteStreamPlaying = true
                                )
                            }
                            remoteUid = uid
                            bindRemoteView(uid)
                        }
                    }

                    override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
                        Log.i(TAG, "[AGORA_SEQ_9] onFirstRemoteVideoDecoded: uid=$uid (${width}x${height}), elapsed=$elapsed")
                        scope.launch(Dispatchers.Main) {
                            remoteUid = uid
                            _callState.value = _callState.value.copy(
                                remoteStreamId = uid.toString(),
                                isRemoteStreamPlaying = true
                            )
                            bindRemoteView(uid)
                        }
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        Log.i(TAG, "[AGORA_SEQ_10] onUserOffline: uid=$uid, reason=$reason (${getUserOfflineReasonName(reason)})")
                        scope.launch(Dispatchers.Main) {
                            val currentState = _callState.value.state
                            if (currentState != CallState.IDLE && currentState != CallState.ENDED) {
                                endCallInternal("User Offline")
                            }
                        }
                    }

                    override fun onError(err: Int) {
                        Log.e(TAG, "[AGORA_SEQ_6] onError: code=$err (${getAgoraErrorCodeName(err)})")
                    }
                }
            }

            Log.i(TAG, "[DIAGNOSTIC] Calling RtcEngine.create with AppID=$AGORA_APP_ID")
            rtcEngine = RtcEngine.create(config).apply {
                enableAudio()
                enableVideo()
                setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            }
            Log.i(TAG, "Agora RtcEngine created successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create Agora RtcEngine: ${e.message}", e)
        }
    }

    private fun getConnectionStateName(state: Int): String {
        return when (state) {
            Constants.CONNECTION_STATE_DISCONNECTED -> "DISCONNECTED (1)"
            Constants.CONNECTION_STATE_CONNECTING -> "CONNECTING (2)"
            Constants.CONNECTION_STATE_CONNECTED -> "CONNECTED (3)"
            Constants.CONNECTION_STATE_RECONNECTING -> "RECONNECTING (4)"
            Constants.CONNECTION_STATE_FAILED -> "FAILED (5)"
            else -> "UNKNOWN ($state)"
        }
    }

    private fun getConnectionChangedReasonName(reason: Int): String {
        return when (reason) {
            Constants.CONNECTION_CHANGED_CONNECTING -> "CONNECTING (0)"
            Constants.CONNECTION_CHANGED_JOIN_SUCCESS -> "JOIN_SUCCESS (1)"
            Constants.CONNECTION_CHANGED_INTERRUPTED -> "INTERRUPTED (2)"
            Constants.CONNECTION_CHANGED_BANNED_BY_SERVER -> "BANNED_BY_SERVER (3)"
            Constants.CONNECTION_CHANGED_JOIN_FAILED -> "JOIN_FAILED (4)"
            Constants.CONNECTION_CHANGED_LEAVE_CHANNEL -> "LEAVE_CHANNEL (5)"
            Constants.CONNECTION_CHANGED_INVALID_APP_ID -> "INVALID_APP_ID (6)"
            Constants.CONNECTION_CHANGED_INVALID_CHANNEL_NAME -> "INVALID_CHANNEL_NAME (7)"
            Constants.CONNECTION_CHANGED_INVALID_TOKEN -> "INVALID_TOKEN (8)"
            Constants.CONNECTION_CHANGED_TOKEN_EXPIRED -> "TOKEN_EXPIRED (9)"
            Constants.CONNECTION_CHANGED_REJECTED_BY_SERVER -> "REJECTED_BY_SERVER (10)"
            Constants.CONNECTION_CHANGED_SETTING_PROXY_SERVER -> "SETTING_PROXY_SERVER (11)"
            Constants.CONNECTION_CHANGED_RENEW_TOKEN -> "RENEW_TOKEN (12)"
            Constants.CONNECTION_CHANGED_CLIENT_IP_ADDRESS_CHANGED -> "CLIENT_IP_ADDRESS_CHANGED (13)"
            Constants.CONNECTION_CHANGED_KEEP_ALIVE_TIMEOUT -> "KEEP_ALIVE_TIMEOUT (14)"
            else -> "REASON_CODE ($reason)"
        }
    }

    private fun getUserOfflineReasonName(reason: Int): String {
        return when (reason) {
            Constants.USER_OFFLINE_QUIT -> "QUIT (0)"
            Constants.USER_OFFLINE_DROPPED -> "DROPPED (1)"
            Constants.USER_OFFLINE_BECOME_AUDIENCE -> "BECOME_AUDIENCE (2)"
            else -> "OFFLINE_CODE ($reason)"
        }
    }

    private fun getAgoraErrorCodeName(err: Int): String {
        return when (err) {
            Constants.ERR_OK -> "ERR_OK (0)"
            Constants.ERR_FAILED -> "ERR_FAILED (1)"
            Constants.ERR_INVALID_ARGUMENT -> "ERR_INVALID_ARGUMENT (2)"
            Constants.ERR_NOT_READY -> "ERR_NOT_READY (3)"
            Constants.ERR_NOT_SUPPORTED -> "ERR_NOT_SUPPORTED (4)"
            Constants.ERR_REFUSED -> "ERR_REFUSED (5)"
            Constants.ERR_BUFFER_TOO_SMALL -> "ERR_BUFFER_TOO_SMALL (6)"
            Constants.ERR_NOT_INITIALIZED -> "ERR_NOT_INITIALIZED (7)"
            Constants.ERR_INVALID_APP_ID -> "ERR_INVALID_APP_ID (101)"
            Constants.ERR_INVALID_CHANNEL_NAME -> "ERR_INVALID_CHANNEL_NAME (102)"
            Constants.ERR_TOKEN_EXPIRED -> "ERR_TOKEN_EXPIRED (109)"
            Constants.ERR_INVALID_TOKEN -> "ERR_INVALID_TOKEN (110)"
            Constants.ERR_CONNECTION_INTERRUPTED -> "ERR_CONNECTION_INTERRUPTED (111)"
            Constants.ERR_CONNECTION_LOST -> "ERR_CONNECTION_LOST (112)"
            else -> "ERR_CODE ($err)"
        }
    }

    private suspend fun fetchAgoraToken(channelId: String, uid: Int): String? = withContext(Dispatchers.IO) {
        try {
            val supabaseUrl = SupabaseClientProvider.supabaseUrl
            val publishableKey = SupabaseClientProvider.supabasePublishableKey
            val session = try {
                SupabaseClientProvider.auth.currentSessionOrNull()
            } catch (e: Exception) {
                null
            }
            val sessionExists = (session != null)
            val currentSessionToken = try {
                SupabaseClientProvider.auth.currentAccessTokenOrNull()
            } catch (e: Exception) {
                null
            }
            val accessTokenExists = !currentSessionToken.isNullOrBlank()

            Log.i(
                TAG,
                "[AGORA_TOKEN_DIAGNOSTIC] Starting token fetch: channelId='$channelId', numericUid=$uid, supabaseUrl='$supabaseUrl', sessionExists=$sessionExists, accessTokenExists=$accessTokenExists"
            )

            if (!accessTokenExists) {
                Log.e(TAG, "[AGORA_TOKEN_DIAGNOSTIC] FAILED: No Supabase authenticated user session token available. Cannot authenticate request to Edge Function.")
                return@withContext null
            }

            val json = JSONObject().apply {
                put("channelId", channelId)
                put("uid", uid)
            }

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val edgeFunctionUrl = "$supabaseUrl/functions/v1/generate-agora-token"

            val request = Request.Builder()
                .url(edgeFunctionUrl)
                .addHeader("apikey", publishableKey)
                .addHeader("Authorization", "Bearer $currentSessionToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val httpStatusCode = response.code
            val isSuccess = response.isSuccessful
            val responseBody = response.body?.string() ?: ""

            Log.i(TAG, "[AGORA_TOKEN_DIAGNOSTIC] Edge Function response: HTTP status=$httpStatusCode, isSuccessful=$isSuccess")

            if (isSuccess && responseBody.isNotBlank()) {
                try {
                    val resJson = JSONObject(responseBody)
                    val returnedChannelId = resJson.optString("channelId", "")
                    val returnedUid = resJson.optLong("uid", -1L)
                    val token = resJson.optString("token", "")
                    val isTokenNonEmpty = token.isNotBlank()
                    val tokenLength = token.length

                    Log.i(
                        TAG,
                        "[AGORA_TOKEN_DIAGNOSTIC] Parsed Edge Function payload: returnedChannelId='$returnedChannelId', returnedUid=$returnedUid, isTokenNonEmpty=$isTokenNonEmpty, tokenLength=$tokenLength"
                    )

                    if (isTokenNonEmpty) {
                        return@withContext token
                    } else {
                        Log.e(TAG, "[AGORA_TOKEN_DIAGNOSTIC] FAILED: Edge Function returned HTTP 200 but token field was empty or null in response payload.")
                    }
                } catch (jsonErr: Exception) {
                    Log.e(TAG, "[AGORA_TOKEN_DIAGNOSTIC] FAILED: Could not parse response JSON from Edge Function: ${jsonErr.localizedMessage}")
                }
            } else {
                Log.e(TAG, "[AGORA_TOKEN_DIAGNOSTIC] FAILED: HTTP $httpStatusCode, body=$responseBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[AGORA_TOKEN_DIAGNOSTIC] Error fetching Agora token from Edge Function: ${e.localizedMessage}", e)
        }
        return@withContext null
    }

    fun joinCallRoom(roomID: String, isVideoCall: Boolean) {
        initRtcEngine(context)
        if (isJoinedRoom && _callState.value.roomID == roomID) {
            Log.d(TAG, "Already joined Agora room: $roomID")
            return
        }

        val myProfile = currentUserProfile ?: getLocalUserProfile()
        val myUserId = if (myProfile.uid.isNotBlank() && myProfile.uid != "self") myProfile.uid else "user_${System.currentTimeMillis()}"
        val numericUid = getAgoraNumericUid(myUserId)

        isJoinedRoom = true
        _callState.value = _callState.value.copy(
            localStreamId = numericUid.toString(),
            roomID = roomID
        )

        // Hardware permissions & explicit device controls
        if (isVideoCall) {
            rtcEngine?.enableVideo()
            rtcEngine?.startPreview()
            enableBeautyFilter(true)
        } else {
            rtcEngine?.disableVideo()
        }
        rtcEngine?.enableAudio()
        rtcEngine?.muteLocalAudioStream(false)
        rtcEngine?.setEnableSpeakerphone(isVideoCall)

        // Attach local preview if view is already bound
        localViewRef?.let { view ->
            if (isVideoCall) {
                val canvas = VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0)
                rtcEngine?.setupLocalVideo(canvas)
                rtcEngine?.startPreview()
            }
        }

        // Fetch dynamic token and join Agora channel
        callScope.launch {
            Log.i(
                TAG,
                "[AGORA_SEQ_1] Before fetchAgoraToken: channelId='$roomID', numericUid=$numericUid, userId='$myUserId', isVideoCall=$isVideoCall"
            )
            val token = fetchAgoraToken(roomID, numericUid)
            Log.i(
                TAG,
                "[AGORA_SEQ_2] After fetchAgoraToken: channelId='$roomID', numericUid=$numericUid, success=${!token.isNullOrBlank()}, tokenLength=${token?.length ?: 0}"
            )

            if (token.isNullOrBlank()) {
                Log.e(
                    TAG,
                    "[AGORA_ERROR] fetchAgoraToken returned NULL or BLANK token for roomID='$roomID', numericUid=$numericUid. ABORTING joinChannel() to prevent invalid join!"
                )
                withContext(Dispatchers.Main) {
                    isJoinedRoom = false
                    _callState.value = _callState.value.copy(localStreamId = "")
                    android.widget.Toast.makeText(context, "কল সংযোগ ব্যর্থ হয়েছে: মিডিয়া টোকেন পাওয়া যায়নি", android.widget.Toast.LENGTH_LONG).show()
                    endCallInternal("Token Generation Failed")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val options = ChannelMediaOptions().apply {
                    autoSubscribeAudio = true
                    autoSubscribeVideo = true
                    publishCameraTrack = isVideoCall
                    publishMicrophoneTrack = true
                    clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                    channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                }
                Log.i(
                    TAG,
                    "[AGORA_SEQ_3] Before joinChannel: roomID='$roomID', numericUid=$numericUid, isVideoCall=$isVideoCall, publishCameraTrack=$isVideoCall, publishMicrophoneTrack=true, autoSubscribeAudio=true, autoSubscribeVideo=true, tokenLength=${token.length}"
                )
                val joinResult = rtcEngine?.joinChannel(token, roomID, numericUid, options)
                Log.i(
                    TAG,
                    "[AGORA_SEQ_4] joinChannel return value: $joinResult (0 means success/initiated, negative means error code) for roomID='$roomID', uid=$numericUid"
                )
                if (joinResult != null && joinResult < 0) {
                    Log.e(TAG, "[AGORA_ERROR] joinChannel failed immediately with error code $joinResult (${getAgoraErrorCodeName(joinResult)})")
                }
            }
        }
    }

    fun leaveCallRoom() {
        if (!isJoinedRoom) return
        isJoinedRoom = false

        val current = _callState.value
        val roomID = current.roomID

        try {
            rtcEngine?.stopPreview()
            rtcEngine?.leaveChannel()
            Log.i(TAG, "Left Agora channel: $roomID")
        } catch (e: Exception) {
            Log.w(TAG, "Error leaving Agora channel: ${e.message}")
        }

        localViewRef = null
        remoteViewRef = null
        remoteUid = 0
        _callState.value = _callState.value.copy(
            localStreamId = "",
            remoteStreamId = "",
            isRemoteStreamPlaying = false
        )
    }

    fun setLocalVideoView(view: View?) {
        localViewRef = view
        val isVideo = (_callState.value.callType == CallType.VIDEO)
        if (view != null && isVideo) {
            rtcEngine?.enableVideo()
            val canvas = VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0)
            rtcEngine?.setupLocalVideo(canvas)
            rtcEngine?.startPreview()
            enableBeautyFilter(true)
            Log.d(TAG, "Attached local video preview to Agora VideoCanvas")
        } else if (view == null) {
            rtcEngine?.setupLocalVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        }
    }

    fun enableBeautyFilter(enable: Boolean = true) {
        if (rtcEngine == null) return
        try {
            if (enable) {
                val beautyOptions = BeautyOptions().apply {
                    lighteningContrastLevel = BeautyOptions.LIGHTENING_CONTRAST_NORMAL
                    lighteningLevel = 0.7f
                    smoothnessLevel = 0.5f
                    rednessLevel = 0.1f
                    sharpnessLevel = 0.3f
                }
                rtcEngine?.setBeautyEffectOptions(true, beautyOptions)
                Log.i(TAG, "Agora real-time Beauty & Brightness filter ENABLED")
            } else {
                rtcEngine?.setBeautyEffectOptions(false, BeautyOptions())
                Log.i(TAG, "Agora Beauty filter DISABLED")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting Agora beautify options: ${e.localizedMessage}")
        }
    }

    fun setRemoteVideoView(view: View?) {
        remoteViewRef = view
        val rUid = remoteUid
        if (view != null && rUid > 0) {
            bindRemoteView(rUid)
        } else if (view == null && rUid > 0) {
            rtcEngine?.setupRemoteVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, rUid))
        }
    }

    private fun bindRemoteView(uid: Int) {
        if (uid <= 0) return
        val view = remoteViewRef
        if (view != null) {
            val canvas = VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid)
            rtcEngine?.setupRemoteVideo(canvas)
            _callState.value = _callState.value.copy(isRemoteStreamPlaying = true)
            Log.d(TAG, "Bound remote video for uid=$uid")
        } else {
            Log.d(TAG, "Awaiting remote view binding for uid=$uid")
        }
    }

    fun getLocalUserProfile(): UserProfile {
        val prefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)

        val authUid = try {
            SupabaseClientProvider.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            null
        }

        var uid = prefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: authUid
            ?: "self"
        if (uid == "self" && !authUid.isNullOrBlank()) {
            uid = authUid
        }

        val name = prefs.getString("user_name", null) ?: fallbackPrefs.getString("user_name", "Me") ?: "Me"
        val phone = prefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val pic = prefs.getString("user_profile_pic", null) ?: fallbackPrefs.getString("user_profile_pic", "") ?: ""
        val bio = prefs.getString("user_bio", null) ?: fallbackPrefs.getString("user_bio", "Available on Talkly 💬") ?: "Available on Talkly 💬"
        val suffix = PhoneUtils.extractPhoneSuffix(phone)

        return UserProfile(
            uid = uid,
            name = name,
            phoneNumber = phone,
            phoneSuffix = suffix,
            profilePicUrl = pic,
            bio = bio
        )
    }

    fun clearSession() {
        try {
            leaveCallRoom()
            callSoundManager.stopAllSounds()
            callSyncJob?.cancel()
            callSyncJob = null

            val channelToClose = callsRealtimeChannel
            callsRealtimeChannel = null
            scope.launch(Dispatchers.IO) {
                SupabaseCallService.unsubscribeChannel(channelToClose)
            }

            currentSyncedUserId = null
            currentUserProfile = null
            _callState.value = CurrentCallInfo(state = CallState.IDLE)
            _callLogs.value = emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing session: ${e.localizedMessage}")
        }
    }

    fun startRealtimeCallSync(userProfile: UserProfile, repository: FirebaseChatRepository, force: Boolean = false) {
        this.currentUserProfile = userProfile
        this.chatRepository = repository

        val authUid = SupabaseClientProvider.auth.currentUserOrNull()?.id
        val uid = if (!authUid.isNullOrBlank()) authUid else userProfile.uid
        val channelStatusStr = callsRealtimeChannel?.status?.value?.name ?: "null"
        val now = System.currentTimeMillis()

        Log.d(
            TAG,
            "DIAGNOSTIC startRealtimeCallSync ENTRY -> uid='$uid', currentSyncedUserId='$currentSyncedUserId', channelStatus=$channelStatusStr, isSubscribingCalls=$isSubscribingCalls, lastCallReconnectTimestamp=$lastCallReconnectTimestamp, lastCallSubscribedTimestamp=$lastCallSubscribedTimestamp, force=$force, timeSinceLastReconnect=${now - lastCallReconnectTimestamp}ms"
        )

        if (uid.isBlank() || uid == "self") {
            Log.d(TAG, "DIAGNOSTIC startRealtimeCallSync GUARD EXIT: uid is blank or 'self' (uid='$uid')")
            return
        }

        val isChannelActive = callsRealtimeChannel != null && callsRealtimeChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED

        // 1. If already SUBSCRIBED for the same user, never tear down a healthy channel unless force requested
        if (currentSyncedUserId == uid && isChannelActive && !force) {
            Log.d(TAG, "DIAGNOSTIC startRealtimeCallSync GUARD EXIT: Channel is already active/SUBSCRIBED for uid='$uid' (currentSyncedUserId='$currentSyncedUserId', force=$force)")
            return
        }

        // 2. Debounce: if a connection is already in flight within the last 2.5s, don't interrupt it
        if (now - lastCallReconnectTimestamp < 2500L && isSubscribingCalls && !force) {
            Log.d(TAG, "DIAGNOSTIC startRealtimeCallSync GUARD EXIT: Debounced because isSubscribingCalls=true and elapsed time is ${now - lastCallReconnectTimestamp}ms < 2500ms for uid='$uid'")
            return
        }
        lastCallReconnectTimestamp = now

        Log.d(TAG, "DIAGNOSTIC startRealtimeCallSync PROCEEDING: Setting currentSyncedUserId='$uid', isSubscribingCalls=true, and launching realtime subscription")
        currentSyncedUserId = uid
        isSubscribingCalls = true

        // 1. Fetch persistent call history logs from Supabase
        callScope.launch {
            try {
                val logsResult = SupabaseCallService.fetchCallLogs(uid)
                val logs = logsResult.getOrDefault(emptyList()).map { it.toCallLog() }
                if (logs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _callLogs.value = logs
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Error loading initial call logs from Supabase: ${e.localizedMessage}")
            }
        }

        // 2. Subscribe to Supabase Realtime channel for active_calls
        callSyncJob?.cancel()
        callSyncJob = callScope.launch {
            try {
                Log.d(TAG, "DIAGNOSTIC callSyncJob started: unsubscribing previous channel if any, creating new Realtime channel for uid='$uid'")
                callsRealtimeChannel?.let { SupabaseCallService.unsubscribeChannel(it) }
                callsRealtimeChannel = SupabaseCallService.createCallsRealtimeChannel(
                    currentUserId = uid,
                    coroutineScope = callScope,
                    onCallAction = { action ->
                        handleRealtimeCallAction(action)
                    },
                    onStatusChange = { status ->
                        Log.d(TAG, "DIAGNOSTIC callsRealtimeChannel onStatusChange -> status=$status for uid='$uid'")
                        if (status == RealtimeChannel.Status.SUBSCRIBED) {
                            Log.d(TAG, "DIAGNOSTIC Calls Realtime channel successfully SUBSCRIBED for uid='$uid'")
                            lastCallSubscribedTimestamp = System.currentTimeMillis()
                            isSubscribingCalls = false
                        } else if (status == RealtimeChannel.Status.UNSUBSCRIBED) {
                            Log.w(TAG, "Calls channel disconnected (status=$status). Reconnecting in 3s...")
                            isSubscribingCalls = false
                            callScope.launch {
                                delay(3000)
                                if (currentSyncedUserId == uid &&
                                    callsRealtimeChannel?.status?.value != RealtimeChannel.Status.SUBSCRIBED) {
                                    currentUserProfile?.let { prof ->
                                        chatRepository?.let { repo ->
                                            startRealtimeCallSync(prof, repo, force = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "DIAGNOSTIC Error starting Supabase Realtime calls sync: ${e.localizedMessage}", e)
            } finally {
                callScope.launch {
                    delay(3000)
                    isSubscribingCalls = false
                }
            }
        }
    }

    fun reconnectCallSync() {
        val now = System.currentTimeMillis()
        var profile = currentUserProfile ?: getLocalUserProfile()
        val authUid = try {
            SupabaseClientProvider.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            null
        }
        val effectiveUid = if (!authUid.isNullOrBlank()) authUid else profile.uid
        if (effectiveUid.isNotBlank() && effectiveUid != "self" && profile.uid != effectiveUid) {
            profile = profile.copy(uid = effectiveUid)
            currentUserProfile = profile
        }

        val repo = chatRepository ?: FirebaseChatRepository.getInstance(context)
        val uid = profile.uid
        val channelStatusStr = callsRealtimeChannel?.status?.value?.name ?: "null"

        Log.d(
            TAG,
            "reconnectCallSync ENTRY -> uid='$uid', currentSyncedUserId='$currentSyncedUserId', channelStatus=$channelStatusStr, isSubscribingCalls=$isSubscribingCalls"
        )

        if (uid.isBlank() || uid == "self") {
            Log.d(TAG, "reconnectCallSync: User not logged in yet (uid='$uid'), skipping call sync")
            return
        }

        if (now - lastCallReconnectTimestamp < 2500L && isSubscribingCalls) {
            Log.d(TAG, "reconnectCallSync: debounced within 2.5s while isSubscribingCalls=true")
            return
        }

        val isChannelActive = callsRealtimeChannel != null && callsRealtimeChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
        // If the calls channel is already connected and healthy for the current user, always skip tearing it down
        if (isChannelActive && currentSyncedUserId == uid) {
            Log.d(TAG, "reconnectCallSync: Calls channel already active and healthy for $uid, skipping teardown")
            return
        }
        currentSyncedUserId = null
        startRealtimeCallSync(profile, repo)
    }

    private fun handleRealtimeCallAction(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> {
                try {
                    val activeCall = SupabaseMessagingService.json.decodeFromJsonElement<SupabaseActiveCall>(action.record)
                    scope.launch(Dispatchers.Main) {
                        handleActiveCallSignal(activeCall)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error decoding active call Insert: ${e.localizedMessage}")
                }
            }
            is PostgresAction.Update -> {
                try {
                    val activeCall = SupabaseMessagingService.json.decodeFromJsonElement<SupabaseActiveCall>(action.record)
                    scope.launch(Dispatchers.Main) {
                        handleActiveCallSignal(activeCall)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error decoding active call Update: ${e.localizedMessage}")
                }
            }
            is PostgresAction.Delete -> {
                scope.launch(Dispatchers.Main) {
                    val currentState = _callState.value.state
                    if (currentState != CallState.IDLE && currentState != CallState.ENDED) {
                        endCallInternal("Call Ended")
                    }
                }
            }
            else -> {}
        }
    }

    private fun handleActiveCallSignal(call: SupabaseActiveCall) {
        val myProfile = currentUserProfile ?: getLocalUserProfile()
        val myUid = myProfile.uid
        val myPhone = PhoneUtils.cleanPhoneNumber(myProfile.phoneNumber)
        val mySuffix = myProfile.phoneSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(myPhone) }

        val callerUid = call.callerId
        val callerPhone = PhoneUtils.cleanPhoneNumber(call.callerPhone)
        val callerSuffix = call.callerSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(callerPhone) }

        val receiverUid = call.receiverId ?: ""
        val receiverPhone = PhoneUtils.cleanPhoneNumber(call.receiverPhone)
        val receiverSuffix = call.receiverSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(receiverPhone) }

        val callType = try { CallType.valueOf(call.callType.uppercase()) } catch (e: Exception) { CallType.VIDEO }
        val status = call.status.uppercase()
        val roomID = call.roomId.ifBlank { call.id }

        val isMeCaller = (myUid.isNotBlank() && myUid != "self" && callerUid == myUid) ||
                (mySuffix.isNotBlank() && callerSuffix.isNotBlank() && callerSuffix == mySuffix) ||
                (myPhone.isNotBlank() && callerPhone.isNotBlank() && callerPhone == myPhone) ||
                _callState.value.isOutgoing ||
                ((_callState.value.state == CallState.OUTGOING_CALLING || _callState.value.state == CallState.OUTGOING_RINGING) && _callState.value.roomID == roomID)

        val isMeReceiver = !isMeCaller && (
                (myUid.isNotBlank() && myUid != "self" && receiverUid == myUid) ||
                (mySuffix.isNotBlank() && receiverSuffix.isNotBlank() && receiverSuffix == mySuffix) ||
                (myPhone.isNotBlank() && receiverPhone.isNotBlank() && receiverPhone == myPhone)
        )

        Log.e(TAG, "[DIAGNOSTIC] handleActiveCallSignal: raw call.callType='${call.callType}', parsed callType=$callType, status=$status, isMeCaller=$isMeCaller, isMeReceiver=$isMeReceiver")

        when (status) {
            "CALLING" -> {
                if (isMeReceiver && !isMeCaller) {
                    val currentState = _callState.value.state
                    if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING || currentState == CallState.ACTIVE) {
                        Log.d(TAG, "User is busy in existing call ($currentState), updating status to BUSY")
                        scope.launch(Dispatchers.IO) {
                            SupabaseCallService.updateActiveCallStatus(call.id, "BUSY")
                        }
                        return
                    }

                    val callCreatedMillis = SupabaseMessage.parseIsoTimestampToMillis(call.createdAt)
                    val callAgeMs = if (callCreatedMillis > 0) System.currentTimeMillis() - callCreatedMillis else 0L
                    if (callAgeMs > 45_000L) {
                        Log.d(TAG, "Ignoring stale incoming call (age: ${callAgeMs}ms)")
                        scope.launch(Dispatchers.IO) {
                            SupabaseCallService.updateActiveCallStatus(call.id, "MISSED")
                        }
                        return
                    }

                    // Acknowledge ringing back to caller via Supabase
                    scope.launch(Dispatchers.IO) {
                        SupabaseCallService.updateActiveCallStatus(call.id, "RINGING")
                    }

                    if (currentState == CallState.IDLE || currentState == CallState.ENDED) {
                        callSoundManager.startIncomingRingtone()
                        val incomingCaller = FamilyMember(
                            id = if (callerSuffix.isNotBlank()) callerSuffix else callerUid,
                            name = call.callerName.ifBlank { "Talkly User" },
                            phone = call.callerPhone,
                            relation = "Family Member",
                            status = "Incoming call...",
                            avatarUrl = if (call.callerAvatarUrl.isNotBlank()) call.callerAvatarUrl else null,
                            isOnline = true,
                            firebaseUid = callerUid,
                            isRegisteredOnTalkly = true
                        )
                        _callState.value = CurrentCallInfo(
                            state = CallState.INCOMING_RINGING,
                            callType = callType,
                            targetMember = incomingCaller,
                            roomID = roomID,
                            durationSeconds = 0
                        )
                        Log.e(TAG, "[DIAGNOSTIC] _callState emitted (INCOMING_RINGING): callType=${_callState.value.callType}, caller=${incomingCaller.name}, roomID=$roomID")

                        com.family.talkly.service.CallForegroundService.startIncomingCallService(
                            context = context,
                            callerName = call.callerName.ifBlank { "Talkly User" },
                            callerUid = callerUid,
                            callerPhone = call.callerPhone,
                            callerAvatar = call.callerAvatarUrl,
                            roomId = roomID,
                            callType = callType.name
                        )
                    }
                }
            }
            "RINGING", "PEER_RINGING" -> {
                if (isMeCaller) {
                    val currentState = _callState.value.state
                    if (currentState == CallState.OUTGOING_CALLING) {
                        Log.d(TAG, "Recipient device ringing. Transitioning state to OUTGOING_RINGING")
                        _callState.value = _callState.value.copy(state = CallState.OUTGOING_RINGING)
                    }
                }
            }
            "ACCEPTED", "PEER_ANSWERED" -> {
                if (isMeCaller) {
                    val currentState = _callState.value.state
                    if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING) {
                        ringingTimeoutJob?.cancel()
                        try {
                            com.family.talkly.service.CallForegroundService.stopCallService(context)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error stopping foreground service: ${e.localizedMessage}")
                        }
                        callSoundManager.stopAllSounds()
                        val isVideo = (_callState.value.callType == CallType.VIDEO)
                        callSoundManager.configureAudioForActiveCall(isSpeakerOn = isVideo, isMuted = _callState.value.isMuted)
                        _callState.value = _callState.value.copy(state = CallState.ACTIVE, isSpeakerOn = isVideo)
                        com.family.talkly.service.CallForegroundService.startActiveCallService(
                            context = context,
                            callerName = _callState.value.targetMember?.name ?: "Talkly User",
                            callType = _callState.value.callType.name,
                            roomId = roomID
                        )
                        joinCallRoom(roomID, isVideo)
                        startCallTimer()
                    }
                }
            }
            "REJECTED", "DECLINED" -> {
                val currentState = _callState.value.state
                if (currentState != CallState.IDLE && currentState != CallState.ENDED) {
                    if (isMeCaller) {
                        android.widget.Toast.makeText(context, "Call declined", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    endCallInternal("Call Declined")
                }
            }
            "BUSY" -> {
                val currentState = _callState.value.state
                if (currentState != CallState.IDLE && currentState != CallState.ENDED) {
                    if (isMeCaller) {
                        android.widget.Toast.makeText(context, "User is busy", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    endCallInternal("User Busy")
                }
            }
            "ENDED", "TIMED_OUT", "TIMEOUT", "MISSED", "CANCELLED" -> {
                val currentState = _callState.value.state
                if (currentState != CallState.IDLE && currentState != CallState.ENDED) {
                    endCallInternal("Call Ended")
                }
            }
        }
    }

    fun startOutgoingCall(member: FamilyMember, callType: CallType, isBlocked: Boolean = false) {
        val caller = currentUserProfile ?: getLocalUserProfile()
        startOutgoingCall(caller, member, callType, isBlocked)
    }

    fun startOutgoingCall(
        callerProfile: UserProfile,
        member: FamilyMember,
        callType: CallType,
        isBlocked: Boolean = false
    ) {
        Log.e("Talkly_AgoraEngine", "[CALLER_DIAGNOSTIC] startOutgoingCall() received callType=$callType")
        if (isBlocked) {
            Log.w(TAG, "Cannot start call: ${member.name} is blocked")
            android.widget.Toast.makeText(context, "Call failed: User is blocked", android.widget.Toast.LENGTH_SHORT).show()
            endCallInternal("User Blocked")
            return
        }

        val authUid = SupabaseClientProvider.auth.currentUserOrNull()?.id
        val effectiveCallerUid = if (!authUid.isNullOrBlank()) authUid else callerProfile.uid

        val targetUid = member.firebaseUid ?: if (!member.id.startsWith("contact_") && !member.id.contains(" ")) member.id else ""
        val targetPhone = member.phone
        val targetSuffix = PhoneUtils.extractPhoneSuffix(targetPhone)

        val combinedUserIds = listOf(effectiveCallerUid, targetUid.ifBlank { targetSuffix }).filter { it.isNotBlank() }.sorted().joinToString("_")
        val roomID = "call_room_${combinedUserIds}"

        val isVideo = (callType == CallType.VIDEO)
        _callState.value = CurrentCallInfo(
            state = CallState.OUTGOING_CALLING,
            callType = callType,
            targetMember = member,
            roomID = roomID,
            durationSeconds = 0,
            isMuted = false,
            isCameraOff = false,
            isFrontCamera = true,
            isSpeakerOn = isVideo,
            isOutgoing = true
        )
        callSoundManager.startOutgoingRingbackTone()
        com.family.talkly.service.CallForegroundService.startActiveCallService(
            context = context,
            callerName = member.name,
            callType = callType.name,
            roomId = roomID
        )
        Log.d(TAG, "Starting outgoing ${callType.name} call to ${member.name} in room $roomID")

        // Join Agora RTC channel early for outgoing call
        joinCallRoom(roomID, isVideo)

        // Async resolve receiver UUID if needed and insert active_call row in Supabase
        scope.launch(Dispatchers.IO) {
            var resolvedTargetId = targetUid
            val isTargetUuid = try {
                if (resolvedTargetId.isNotBlank()) {
                    java.util.UUID.fromString(resolvedTargetId)
                    true
                } else false
            } catch (e: Exception) {
                false
            }

            if (!isTargetUuid) {
                val found = SupabaseMessagingService.resolveUserUuid(targetPhone)
                    ?: SupabaseMessagingService.resolveUserUuid(targetSuffix)
                    ?: SupabaseMessagingService.resolveUserUuid(member.id)
                if (!found.isNullOrBlank()) {
                    resolvedTargetId = found
                }
            }

            val finalReceiverId = if (resolvedTargetId.isNotBlank()) {
                try {
                    java.util.UUID.fromString(resolvedTargetId)
                    resolvedTargetId
                } catch (e: Exception) {
                    null
                }
            } else null

            val nowIso = SupabaseMessage.millisToIsoTimestamp(System.currentTimeMillis())
            val activeCall = SupabaseActiveCall(
                id = roomID,
                roomId = roomID,
                callerId = effectiveCallerUid,
                callerName = callerProfile.name,
                callerPhone = callerProfile.phoneNumber,
                callerSuffix = callerProfile.phoneSuffix,
                callerAvatarUrl = callerProfile.profilePicUrl ?: "",
                receiverId = finalReceiverId,
                receiverPhone = targetPhone,
                receiverSuffix = targetSuffix,
                callType = callType.name,
                status = "CALLING",
                createdAt = nowIso,
                updatedAt = nowIso
            )

            Log.e("Talkly_AgoraEngine", "[CALLER_DIAGNOSTIC] Inserting into Supabase: callerId=$effectiveCallerUid, receiverId=$finalReceiverId, roomId=$roomID, callType=${callType.name}")
            val createResult = SupabaseCallService.createActiveCall(activeCall)
            if (createResult.isFailure) {
                val errorMsg = createResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                Log.e(TAG, "Failed to create active call in Supabase: $errorMsg")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "কল সংযোগ স্থাপন করা যায়নি: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                    endCallInternal("Call Setup Failed")
                }
                return@launch
            }

            // Send high priority FCM push for incoming calls (for killed/background recipient)
            val fcmPayload = mapOf(
                "type" to "INCOMING_CALL",
                "callerName" to callerProfile.name,
                "callerUid" to effectiveCallerUid,
                "caller_id" to effectiveCallerUid,
                "callerPhone" to callerProfile.phoneNumber,
                "callerAvatarUrl" to (callerProfile.profilePicUrl ?: ""),
                "roomID" to roomID,
                "callType" to callType.name,
                "status" to "RINGING"
            )
            com.family.talkly.util.FcmTokenManager.sendHighPriorityPush(
                targetUid = (finalReceiverId ?: resolvedTargetId).ifBlank { member.id },
                targetPhoneSuffix = targetSuffix,
                dataPayload = fcmPayload
            )
        }

        ringingTimeoutJob?.cancel()

        ringingTimeoutJob = scope.launch {
            delay(30000)
            val currentState = _callState.value.state
            if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING) {
                Log.d(TAG, "Call timed out after 30s: ${member.name} is unavailable or unreachable")
                android.widget.Toast.makeText(context, "${member.name} is unavailable / unreachable", android.widget.Toast.LENGTH_SHORT).show()

                scope.launch(Dispatchers.IO) {
                    SupabaseCallService.updateActiveCallStatus(roomID, "TIMEOUT")
                }

                val callLog = CallLog(
                    id = java.util.UUID.randomUUID().toString(),
                    memberId = member.id,
                    memberName = member.name,
                    direction = CallDirection.OUTGOING,
                    callType = callType,
                    timestamp = System.currentTimeMillis(),
                    durationSeconds = 0
                )
                addCallLog(callLog)

                endCallInternal("No Answer")
            }
        }
    }

    fun setIncomingCallFromKilledState(member: FamilyMember, roomID: String, callType: CallType) {
        try {
            com.family.talkly.service.CallForegroundService.stopCallService(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground service in setIncomingCallFromKilledState: ${e.localizedMessage}")
        }
        callSoundManager.stopAllSounds()
        _callState.value = CurrentCallInfo(
            state = CallState.INCOMING_RINGING,
            callType = callType,
            targetMember = member,
            roomID = roomID,
            durationSeconds = 0
        )
        Log.e(TAG, "[DIAGNOSTIC] _callState emitted (setIncomingCallFromKilledState): callType=${_callState.value.callType}, caller=${member.name}, roomID=$roomID")
    }

    fun triggerIncomingCall(member: FamilyMember, callType: CallType) {
        val myProfile = currentUserProfile ?: getLocalUserProfile()
        val myUid = myProfile.uid
        val myPhone = myProfile.phoneNumber
        val mySuffix = myProfile.phoneSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(myPhone) }

        val memberUid = member.firebaseUid ?: ""
        val memberPhone = member.phone
        val memberSuffix = PhoneUtils.extractPhoneSuffix(memberPhone)

        val isSelf = (myUid.isNotBlank() && myUid != "self" && (memberUid == myUid || member.id == myUid)) ||
                (myPhone.isNotBlank() && memberPhone.isNotBlank() && memberPhone == myPhone) ||
                (mySuffix.isNotBlank() && memberSuffix.isNotBlank() && memberSuffix == mySuffix)

        if (isSelf) {
            Log.d(TAG, "CLIENT-SIDE GUARD: Refusing to trigger incoming call from self-member")
            return
        }

        val roomID = "incoming_room_${member.id}"
        callSoundManager.startIncomingRingtone()
        _callState.value = CurrentCallInfo(
            state = CallState.INCOMING_RINGING,
            callType = callType,
            targetMember = member,
            roomID = roomID,
            durationSeconds = 0
        )
    }

    fun acceptCall() {
        Log.e(TAG, "[DIAGNOSTIC] acceptCall() CALLED: _callState.value.callType=${_callState.value.callType}, roomID=${_callState.value.roomID}, state=${_callState.value.state}")
        ringingTimeoutJob?.cancel()
        try {
            com.family.talkly.service.CallForegroundService.stopRingtoneImmediately()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringtone in acceptCall: ${e.localizedMessage}")
        }
        callSoundManager.stopAllSounds()
        val current = _callState.value
        val member = current.targetMember

        val isVideo = (current.callType == CallType.VIDEO)
        callSoundManager.configureAudioForActiveCall(isSpeakerOn = isVideo, isMuted = current.isMuted)

        scope.launch(Dispatchers.IO) {
            SupabaseCallService.updateActiveCallStatus(current.roomID, "ACCEPTED")
        }

        _callState.value = current.copy(state = CallState.ACTIVE, isSpeakerOn = isVideo, isOutgoing = false)
        Log.e(TAG, "[DIAGNOSTIC] _callState emitted (acceptCall): state=${_callState.value.state}, callType=${_callState.value.callType}, isCameraOff=${_callState.value.isCameraOff}")
        com.family.talkly.service.CallForegroundService.startActiveCallService(
            context = context,
            callerName = member?.name ?: "Talkly User",
            callType = current.callType.name,
            roomId = current.roomID
        )
        joinCallRoom(current.roomID, isVideo)
        startCallTimer()
    }

    fun declineCall() {
        ringingTimeoutJob?.cancel()
        try {
            com.family.talkly.service.CallForegroundService.stopCallService(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground service in declineCall: ${e.localizedMessage}")
        }
        callSoundManager.stopAllSounds()
        val current = _callState.value
        val member = current.targetMember

        scope.launch(Dispatchers.IO) {
            SupabaseCallService.updateActiveCallStatus(current.roomID, "REJECTED")
        }

        if (member != null) {
            val callLog = CallLog(
                id = java.util.UUID.randomUUID().toString(),
                memberId = member.id,
                memberName = member.name,
                direction = CallDirection.MISSED,
                callType = current.callType,
                timestamp = System.currentTimeMillis(),
                durationSeconds = 0
            )
            addCallLog(callLog)
        }
        endCallInternal("Call Declined")
    }

    fun endCall() {
        ringingTimeoutJob?.cancel()
        try {
            com.family.talkly.service.CallForegroundService.stopCallService(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground service in endCall: ${e.localizedMessage}")
        }
        callSoundManager.stopAllSounds()
        val current = _callState.value
        val member = current.targetMember

        scope.launch(Dispatchers.IO) {
            SupabaseCallService.updateActiveCallStatus(current.roomID, "ENDED")
        }

        if (member != null) {
            val isOutgoing = current.isOutgoing || current.state == CallState.OUTGOING_RINGING || current.state == CallState.OUTGOING_CALLING
            val direction = if (isOutgoing) CallDirection.OUTGOING else CallDirection.INCOMING
            val callLog = CallLog(
                id = java.util.UUID.randomUUID().toString(),
                memberId = member.id,
                memberName = member.name,
                direction = direction,
                callType = current.callType,
                timestamp = System.currentTimeMillis(),
                durationSeconds = current.durationSeconds
            )
            addCallLog(callLog)
        }
        endCallInternal("Call Ended")
    }

    private fun endCallInternal(reason: String) {
        val currentRoom = _callState.value.roomID
        ringingTimeoutJob?.cancel()
        timerJob?.cancel()
        try {
            com.family.talkly.service.CallForegroundService.stopCallService(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground call service: ${e.localizedMessage}")
        }
        callSoundManager.stopAllSounds()
        callSoundManager.resetAudioMode()
        leaveCallRoom()
        _callState.value = _callState.value.copy(state = CallState.ENDED)

        // Delete active call row from Supabase
        scope.launch(Dispatchers.IO) {
            try {
                if (currentRoom.isNotBlank()) {
                    SupabaseCallService.deleteActiveCall(currentRoom)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting active call from Supabase: ${e.localizedMessage}")
            }
        }

        scope.launch {
            delay(1000)
            _callState.value = CurrentCallInfo(state = CallState.IDLE)
        }
    }

    fun toggleMute() {
        val current = _callState.value
        val newMuted = !current.isMuted
        _callState.value = current.copy(isMuted = newMuted)
        rtcEngine?.muteLocalAudioStream(newMuted)
        callSoundManager.setMicrophoneMute(newMuted)
    }

    fun toggleCamera() {
        val current = _callState.value
        val newCameraOff = !current.isCameraOff
        _callState.value = current.copy(isCameraOff = newCameraOff)
        Log.e(TAG, "[DIAGNOSTIC] _callState emitted (toggleCamera): state=${_callState.value.state}, callType=${_callState.value.callType}, isCameraOff=${_callState.value.isCameraOff}")
        rtcEngine?.enableLocalVideo(!newCameraOff)
        rtcEngine?.muteLocalVideoStream(newCameraOff)
    }

    fun flipCamera() {
        val current = _callState.value
        val newFront = !current.isFrontCamera
        _callState.value = current.copy(isFrontCamera = newFront)
        rtcEngine?.switchCamera()
    }

    fun toggleSpeaker() {
        val current = _callState.value
        val newSpeaker = !current.isSpeakerOn
        _callState.value = current.copy(isSpeakerOn = newSpeaker)
        rtcEngine?.setEnableSpeakerphone(newSpeaker)
        callSoundManager.setSpeakerphoneOn(newSpeaker)
    }

    private fun startCallTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_callState.value.state == CallState.ACTIVE) {
                delay(1000)
                _callState.value = _callState.value.copy(
                    durationSeconds = _callState.value.durationSeconds + 1
                )
            }
        }
    }

    private fun addCallLog(log: CallLog) {
        val list = _callLogs.value.toMutableList()
        list.add(0, log)
        _callLogs.value = list
        onCallLogAdded?.invoke(log)

        // Persist call log in Supabase
        val profile = currentUserProfile ?: getLocalUserProfile()
        if (profile.uid.isNotBlank() && profile.uid != "self") {
            scope.launch(Dispatchers.IO) {
                try {
                    var peerUid = log.memberId
                    val isDirectUuid = try {
                        java.util.UUID.fromString(peerUid)
                        true
                    } catch (e: Exception) {
                        false
                    }

                    if (!isDirectUuid) {
                        val member = _callState.value.targetMember
                        if (member != null && !member.firebaseUid.isNullOrBlank()) {
                            peerUid = member.firebaseUid!!
                        } else {
                            val resolved = SupabaseMessagingService.resolveUserUuid(peerUid)
                            if (resolved != null) peerUid = resolved
                        }
                    }

                    val finalPeerUuid = try {
                        java.util.UUID.fromString(peerUid)
                        peerUid
                    } catch (e: Exception) {
                        null
                    }

                    val supabaseLog = SupabaseCallLog(
                        id = log.id,
                        userId = profile.uid,
                        peerId = finalPeerUuid,
                        peerName = log.memberName,
                        direction = log.direction.name,
                        callType = log.callType.name,
                        durationSeconds = log.durationSeconds,
                        createdAt = SupabaseMessage.millisToIsoTimestamp(log.timestamp)
                    )
                    SupabaseCallService.insertCallLog(supabaseLog)
                } catch (e: Exception) {
                    Log.w(TAG, "Error persisting call log in Supabase: ${e.localizedMessage}")
                }
            }
        }
    }
}
