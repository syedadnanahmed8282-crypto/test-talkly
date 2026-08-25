package com.family.talkly.data.zego

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import android.view.View
import com.family.talkly.data.firebase.FirebaseChatRepository
import com.family.talkly.data.models.CallDirection
import com.family.talkly.data.models.CallLog
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.UserProfile
import com.family.talkly.data.supabase.SupabaseActiveCall
import com.family.talkly.data.supabase.SupabaseCallLog
import com.family.talkly.data.supabase.SupabaseCallService
import com.family.talkly.data.supabase.SupabaseMessage
import com.family.talkly.data.supabase.SupabaseMessagingService
import com.family.talkly.util.CallSoundManager
import com.family.talkly.util.PhoneUtils
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoBeautifyFeature
import im.zego.zegoexpress.constants.ZegoPublishChannel
import im.zego.zegoexpress.constants.ZegoPublisherState
import im.zego.zegoexpress.constants.ZegoPlayerState
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.constants.ZegoViewMode
import im.zego.zegoexpress.entity.ZegoBeautifyOption
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoEngineProfile
import im.zego.zegoexpress.entity.ZegoRoomConfig
import im.zego.zegoexpress.entity.ZegoStream
import im.zego.zegoexpress.entity.ZegoUser
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
    val zegoAppId: Long = ZegoCallEngineManager.ZEGO_APP_ID,
    val zegoAppSign: String = ZegoCallEngineManager.ZEGO_APP_SIGN,
    val isZegoInitialized: Boolean = true,
    val usePrebuiltCallUi: Boolean = false,
    val zegoUIKitAppId: Long = ZegoCallEngineManager.ZEGO_APP_ID,
    val zegoUIKitAppSign: String = ZegoCallEngineManager.ZEGO_APP_SIGN,
    val stunServers: List<String> = ZegoCallEngineManager.PUBLIC_STUN_SERVERS,
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
        const val TAG = "Talkly_ZegoEngine"
        val PUBLIC_STUN_SERVERS: List<String> = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "turn:openrelay.metered.ca:80",
            "turn:openrelay.metered.ca:443",
            "turn:openrelay.metered.ca:443?transport=tcp"
        )
        val ZEGO_APP_ID: Long = 196267710L
        val ZEGO_APP_SIGN: String = "620de7961f58b3a6f8390c8a484233ff60aafd59a6f4bc8a538b25c502fd4403"

        @Volatile
        private var INSTANCE: ZegoCallEngineManager? = null

        fun getInstance(context: Context): ZegoCallEngineManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ZegoCallEngineManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val callSoundManager = CallSoundManager(context)

    private var expressEngine: ZegoExpressEngine? = null
    private var isJoinedRoom = false
    private var localViewRef: View? = null
    private var remoteViewRef: View? = null

    private var callsRealtimeChannel: RealtimeChannel? = null
    private var callSyncJob: Job? = null
    private var currentSyncedUserId: String? = null

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
        Log.i(TAG, "ZEGOCloud Express Engine initialized with AppID: $ZEGO_APP_ID")
        initZegoExpressEngine(context)

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
    private fun initZegoExpressEngine(ctx: Context) {
        if (expressEngine != null) return
        try {
            val app = ctx.applicationContext as? Application ?: return
            val profile = ZegoEngineProfile().apply {
                appID = ZEGO_APP_ID
                appSign = ZEGO_APP_SIGN
                scenario = ZegoScenario.COMMUNICATION
                application = app
            }

            Log.e(TAG, "[DIAGNOSTIC] Calling ZegoExpressEngine.createEngine with AppID=$ZEGO_APP_ID")
            expressEngine = ZegoExpressEngine.createEngine(profile, object : IZegoEventHandler() {
                override fun onRoomUserUpdate(
                    roomID: String?,
                    updateType: ZegoUpdateType?,
                    userList: ArrayList<ZegoUser>?
                ) {
                    Log.e(TAG, "[DIAGNOSTIC] onRoomUserUpdate: roomID=$roomID, updateType=$updateType, users=${userList?.size}")
                    if (updateType == ZegoUpdateType.ADD && !userList.isNullOrEmpty()) {
                        val currentState = _callState.value.state
                        if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING) {
                            Log.d(TAG, "Remote peer joined Zego room: stopping outgoing ringtone immediately")
                            ringingTimeoutJob?.cancel()
                            callSoundManager.stopAllSounds()
                            val isVideo = (_callState.value.callType == CallType.VIDEO)
                            callSoundManager.configureAudioForActiveCall(isSpeakerOn = isVideo, isMuted = _callState.value.isMuted)
                            _callState.value = _callState.value.copy(state = CallState.ACTIVE, isSpeakerOn = isVideo)
                            startCallTimer()
                        }
                    }
                }

                override fun onRoomStreamUpdate(
                    roomID: String?,
                    updateType: ZegoUpdateType?,
                    streamList: ArrayList<ZegoStream>?,
                    extendedData: JSONObject?
                ) {
                    if (streamList.isNullOrEmpty() || updateType == null) return
                    Log.e(TAG, "[DIAGNOSTIC] onRoomStreamUpdate: roomID=$roomID, updateType=$updateType, streams=${streamList.size}")

                    if (updateType == ZegoUpdateType.ADD) {
                        Log.d(TAG, "Remote stream ADDED: stopping outgoing ringtone and configuring active audio")
                        ringingTimeoutJob?.cancel()
                        callSoundManager.stopAllSounds()
                        val isVideo = (_callState.value.callType == CallType.VIDEO)
                        callSoundManager.configureAudioForActiveCall(isSpeakerOn = isVideo, isMuted = _callState.value.isMuted)
                        _callState.value = _callState.value.copy(
                            state = CallState.ACTIVE,
                            isSpeakerOn = isVideo
                        )

                        for (stream in streamList) {
                            val streamID = stream.streamID
                            Log.e(TAG, "[DIAGNOSTIC] Remote stream ADDED: streamID=$streamID by user=${stream.user?.userID}")
                            _callState.value = _callState.value.copy(
                                remoteStreamId = streamID,
                                isRemoteStreamPlaying = true
                            )
                            bindRemoteStream(streamID)
                        }
                    } else if (updateType == ZegoUpdateType.DELETE) {
                        for (stream in streamList) {
                            val streamID = stream.streamID
                            Log.e(TAG, "[DIAGNOSTIC] Remote stream DELETED: streamID=$streamID")
                            expressEngine?.stopPlayingStream(streamID)
                            if (_callState.value.remoteStreamId == streamID) {
                                _callState.value = _callState.value.copy(
                                    remoteStreamId = "",
                                    isRemoteStreamPlaying = false
                                )
                            }
                        }
                    }
                }

                override fun onRoomStateChanged(
                    roomID: String?,
                    reason: ZegoRoomStateChangedReason?,
                    errorCode: Int,
                    extendedData: JSONObject?
                ) {
                    Log.e(TAG, "[DIAGNOSTIC] onRoomStateChanged: roomID=$roomID, reason=$reason, errorCode=$errorCode (${getZegoErrorMessage(errorCode)})")
                }

                override fun onPublisherStateUpdate(
                    streamID: String?,
                    state: ZegoPublisherState?,
                    errorCode: Int,
                    extendedData: JSONObject?
                ) {
                    Log.e(TAG, "[DIAGNOSTIC] onPublisherStateUpdate: streamID=$streamID, state=$state, errorCode=$errorCode (${getZegoErrorMessage(errorCode)})")
                }

                override fun onPlayerStateUpdate(
                    streamID: String?,
                    state: ZegoPlayerState?,
                    errorCode: Int,
                    extendedData: JSONObject?
                ) {
                    Log.e(TAG, "[DIAGNOSTIC] onPlayerStateUpdate: streamID=$streamID, state=$state, errorCode=$errorCode (${getZegoErrorMessage(errorCode)})")
                    if (state == ZegoPlayerState.PLAYING) {
                        Log.d(TAG, "Remote player state PLAYING: stopping ringtone immediately")
                        ringingTimeoutJob?.cancel()
                        callSoundManager.stopAllSounds()
                        val isVideo = (_callState.value.callType == CallType.VIDEO)
                        callSoundManager.configureAudioForActiveCall(isSpeakerOn = isVideo, isMuted = _callState.value.isMuted)
                        _callState.value = _callState.value.copy(
                            state = CallState.ACTIVE,
                            isRemoteStreamPlaying = true
                        )
                    }
                }
            })
            Log.i(TAG, "ZegoExpressEngine created successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create ZegoExpressEngine: ${e.message}", e)
        }
    }

    fun joinCallRoom(roomID: String, isVideoCall: Boolean) {
        initZegoExpressEngine(context)
        if (isJoinedRoom && _callState.value.roomID == roomID) {
            Log.d(TAG, "Already joined Zego room: $roomID")
            return
        }

        val myProfile = currentUserProfile ?: getLocalUserProfile()
        val myUserId = if (myProfile.uid.isNotBlank() && myProfile.uid != "self") myProfile.uid else "user_${System.currentTimeMillis()}"
        val myUserName = myProfile.name.ifBlank { "Talkly User" }
        val localStreamId = "stream_$myUserId"

        isJoinedRoom = true
        _callState.value = _callState.value.copy(
            localStreamId = localStreamId,
            roomID = roomID
        )

        // Hardware permissions & explicit device controls
        expressEngine?.enableCamera(isVideoCall)
        expressEngine?.muteMicrophone(false)
        expressEngine?.muteSpeaker(false)
        expressEngine?.setAudioRouteToSpeaker(isVideoCall)
        expressEngine?.useFrontCamera(true)

        if (isVideoCall) {
            enableBeautyFilter(true)
        }

        // Login to room
        val user = ZegoUser(myUserId, myUserName)
        val roomConfig = ZegoRoomConfig()
        Log.e(TAG, "[DIAGNOSTIC] Attempting loginRoom: roomID=$roomID, userID=$myUserId")
        expressEngine?.loginRoom(roomID, user, roomConfig) { errorCode, extendedData ->
            Log.e(TAG, "[DIAGNOSTIC] loginRoom callback: roomID=$roomID, errorCode=$errorCode (${getZegoErrorMessage(errorCode)})")
        }

        // Publish local audio/video stream
        Log.e(TAG, "[DIAGNOSTIC] Attempting startPublishingStream: $localStreamId")
        expressEngine?.startPublishingStream(localStreamId)
        Log.i(TAG, "Joined room $roomID as $myUserId ($myUserName), publishing stream $localStreamId")

        // Attach local preview if view is bound
        localViewRef?.let { view ->
            if (isVideoCall) {
                val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
                expressEngine?.startPreview(canvas)
            }
        }
    }

    fun leaveCallRoom() {
        if (!isJoinedRoom) return
        isJoinedRoom = false

        val current = _callState.value
        val roomID = current.roomID
        val remoteStreamId = current.remoteStreamId

        try {
            expressEngine?.stopPreview()
            expressEngine?.stopPublishingStream()
            if (remoteStreamId.isNotBlank()) {
                expressEngine?.stopPlayingStream(remoteStreamId)
            }
            if (roomID.isNotBlank()) {
                Log.e(TAG, "[DIAGNOSTIC] Attempting logoutRoom: $roomID")
                expressEngine?.logoutRoom(roomID) { errorCode, extendedData ->
                    Log.e(TAG, "[DIAGNOSTIC] logoutRoom callback: roomID=$roomID, errorCode=$errorCode (${getZegoErrorMessage(errorCode)})")
                }
            }
            Log.i(TAG, "Left Zego room: $roomID")
        } catch (e: Exception) {
            Log.w(TAG, "Error leaving Zego room: ${e.message}")
        }

        localViewRef = null
        remoteViewRef = null
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
            expressEngine?.enableCamera(true)
            enableBeautyFilter(true)
            if (view is TextureView) {
                if (view.isAvailable) {
                    val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
                    expressEngine?.startPreview(canvas)
                    Log.d(TAG, "Attached local video preview (TextureView available)")
                } else {
                    view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            enableBeautyFilter(true)
                            val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
                            expressEngine?.startPreview(canvas)
                            Log.d(TAG, "Attached local video preview in onSurfaceTextureAvailable")
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            expressEngine?.stopPreview()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            } else {
                val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
                expressEngine?.startPreview(canvas)
                Log.d(TAG, "Attached local video preview view with beauty filter")
            }
        } else if (view == null) {
            expressEngine?.stopPreview()
        }
    }

    fun enableBeautyFilter(enable: Boolean = true) {
        if (expressEngine == null) return
        try {
            if (enable) {
                val beautifyOption = ZegoBeautifyOption().apply {
                    whitenFactor = 0.85 // Skin whitening & bright lighting (0.0 to 1.0)
                    sharpenFactor = 0.6 // Facial detail sharpening (0.0 to 1.0)
                }
                expressEngine?.setBeautifyOption(beautifyOption, ZegoPublishChannel.MAIN)
                Log.i(TAG, "Zego express real-time Beauty & Brightness filter ENABLED (Whiten=0.8, Sharpen=0.5)")
            } else {
                val defaultOption = ZegoBeautifyOption().apply {
                    whitenFactor = 0.0
                    sharpenFactor = 0.0
                }
                expressEngine?.setBeautifyOption(defaultOption, ZegoPublishChannel.MAIN)
                Log.i(TAG, "Zego express Beauty filter DISABLED")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting Zego beautify options: ${e.localizedMessage}")
        }
    }

    fun setRemoteVideoView(view: View?) {
        remoteViewRef = view
        val remoteStreamId = _callState.value.remoteStreamId
        if (remoteStreamId.isNotBlank()) {
            bindRemoteStream(remoteStreamId)
        }
    }

    private fun bindRemoteStream(streamID: String) {
        if (streamID.isBlank()) return
        val view = remoteViewRef
        if (view != null) {
            if (view is TextureView) {
                if (view.isAvailable) {
                    val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
                    expressEngine?.startPlayingStream(streamID, canvas)
                    _callState.value = _callState.value.copy(isRemoteStreamPlaying = true)
                    Log.d(TAG, "Playing remote stream $streamID on available remote TextureView")
                } else {
                    view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
                            expressEngine?.startPlayingStream(streamID, canvas)
                            _callState.value = _callState.value.copy(isRemoteStreamPlaying = true)
                            Log.d(TAG, "Playing remote stream $streamID in onSurfaceTextureAvailable")
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            } else {
                val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
                expressEngine?.startPlayingStream(streamID, canvas)
                _callState.value = _callState.value.copy(isRemoteStreamPlaying = true)
                Log.d(TAG, "Playing remote stream $streamID on remote view")
            }
        } else {
            expressEngine?.startPlayingStream(streamID, ZegoCanvas(null))
            Log.d(TAG, "Playing remote stream $streamID as audio-only / background canvas (awaiting remote view)")
        }
    }

    fun getLocalUserProfile(): UserProfile {
        val prefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)

        val uid = prefs.getString("user_uid", null) ?: fallbackPrefs.getString("user_uid", "self") ?: "self"
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
            Log.w(TAG, "Error clearing Zego session: ${e.localizedMessage}")
        }
    }

    fun startRealtimeCallSync(userProfile: UserProfile, repository: FirebaseChatRepository) {
        this.currentUserProfile = userProfile
        this.chatRepository = repository

        val uid = userProfile.uid
        if (uid.isBlank() || uid == "self") return
        val isChannelActive = callsRealtimeChannel != null && callsRealtimeChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
        if (currentSyncedUserId == uid && isChannelActive) {
            Log.d(TAG, "startRealtimeCallSync: Calls channel already active for $uid")
            return
        }

        callSyncJob?.cancel()
        currentSyncedUserId = uid

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
                Log.w(TAG, "Error loading initial call logs from Supabase: ${e.localizedMessage}")
            }
        }

        // 2. Subscribe to Supabase Realtime channel for active_calls
        callSyncJob = callScope.launch {
            try {
                callsRealtimeChannel?.let { SupabaseCallService.unsubscribeChannel(it) }
                callsRealtimeChannel = SupabaseCallService.createCallsRealtimeChannel(
                    currentUserId = uid,
                    coroutineScope = callScope,
                    onCallAction = { action ->
                        handleRealtimeCallAction(action)
                    },
                    onStatusChange = { status ->
                        if (status == RealtimeChannel.Status.UNSUBSCRIBED) {
                            Log.w(TAG, "Calls channel disconnected (status=$status). Reconnecting in 3s...")
                            callScope.launch {
                                delay(3000)
                                if (currentSyncedUserId == uid &&
                                    callsRealtimeChannel?.status?.value != RealtimeChannel.Status.SUBSCRIBED) {
                                    currentUserProfile?.let { prof ->
                                        chatRepository?.let { repo ->
                                            startRealtimeCallSync(prof, repo)
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error starting Supabase Realtime calls sync: ${e.localizedMessage}")
            }
        }
    }

    fun reconnectCallSync() {
        val profile = currentUserProfile ?: getLocalUserProfile()
        val repo = chatRepository ?: FirebaseChatRepository.getInstance(context)
        if (profile.uid.isNotBlank() && profile.uid != "self") {
            currentSyncedUserId = null
            startRealtimeCallSync(profile, repo)
        }
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

        Log.d(TAG, "handleActiveCallSignal: id=${call.id}, status=$status, isMeCaller=$isMeCaller, isMeReceiver=$isMeReceiver")

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
        if (isBlocked) {
            Log.w(TAG, "Cannot start call: ${member.name} is blocked")
            android.widget.Toast.makeText(context, "Call failed: User is blocked", android.widget.Toast.LENGTH_SHORT).show()
            endCallInternal("User Blocked")
            return
        }

        val targetUid = member.firebaseUid ?: if (!member.id.startsWith("contact_") && !member.id.contains(" ")) member.id else ""
        val targetPhone = member.phone
        val targetSuffix = PhoneUtils.extractPhoneSuffix(targetPhone)

        val combinedUserIds = listOf(callerProfile.uid, targetUid.ifBlank { targetSuffix }).filter { it.isNotBlank() }.sorted().joinToString("_")
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

        // Connect and publish stream early for outgoing call
        joinCallRoom(roomID, isVideo)

        // Async resolve receiver UUID if needed and insert active_call row in Supabase
        scope.launch(Dispatchers.IO) {
            var resolvedTargetId = targetUid
            if (resolvedTargetId.isBlank() || resolvedTargetId.startsWith("contact_")) {
                val found = SupabaseMessagingService.resolveUserUuid(targetPhone) ?: SupabaseMessagingService.resolveUserUuid(targetSuffix)
                if (!found.isNullOrBlank()) {
                    resolvedTargetId = found
                }
            }

            val activeCall = SupabaseActiveCall(
                id = roomID,
                roomId = roomID,
                callerId = callerProfile.uid,
                callerName = callerProfile.name,
                callerPhone = callerProfile.phoneNumber,
                callerSuffix = callerProfile.phoneSuffix,
                callerAvatarUrl = callerProfile.profilePicUrl,
                receiverId = resolvedTargetId.ifBlank { null },
                receiverPhone = targetPhone,
                receiverSuffix = targetSuffix,
                callType = callType.name,
                status = "CALLING"
            )

            SupabaseCallService.createActiveCall(activeCall)

            // Send high priority FCM push for incoming calls (for killed/background recipient)
            val fcmPayload = mapOf(
                "type" to "INCOMING_CALL",
                "callerName" to callerProfile.name,
                "callerUid" to callerProfile.uid,
                "caller_id" to callerProfile.uid,
                "callerPhone" to callerProfile.phoneNumber,
                "callerAvatarUrl" to callerProfile.profilePicUrl,
                "roomID" to roomID,
                "callType" to callType.name,
                "status" to "RINGING"
            )
            com.family.talkly.util.FcmTokenManager.sendHighPriorityPush(
                targetUid = resolvedTargetId,
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
                    id = "call_${System.currentTimeMillis()}",
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
                id = "call_${System.currentTimeMillis()}",
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
                id = "call_${System.currentTimeMillis()}",
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
        expressEngine?.muteMicrophone(newMuted)
        callSoundManager.setMicrophoneMute(newMuted)
    }

    fun toggleCamera() {
        val current = _callState.value
        val newCameraOff = !current.isCameraOff
        _callState.value = current.copy(isCameraOff = newCameraOff)
        Log.e(TAG, "[DIAGNOSTIC] _callState emitted (toggleCamera): state=${_callState.value.state}, callType=${_callState.value.callType}, isCameraOff=${_callState.value.isCameraOff}")
        expressEngine?.enableCamera(!newCameraOff)
    }

    fun flipCamera() {
        val current = _callState.value
        val newFront = !current.isFrontCamera
        _callState.value = current.copy(isFrontCamera = newFront)
        expressEngine?.useFrontCamera(newFront)
    }

    fun toggleSpeaker() {
        val current = _callState.value
        val newSpeaker = !current.isSpeakerOn
        _callState.value = current.copy(isSpeakerOn = newSpeaker)
        expressEngine?.setAudioRouteToSpeaker(newSpeaker)
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
                    if (peerUid.startsWith("contact_") || peerUid.contains(" ")) {
                        val member = _callState.value.targetMember
                        if (member != null && !member.firebaseUid.isNullOrBlank()) {
                            peerUid = member.firebaseUid!!
                        } else {
                            val resolved = SupabaseMessagingService.resolveUserUuid(peerUid)
                            if (resolved != null) peerUid = resolved
                        }
                    }

                    val supabaseLog = SupabaseCallLog(
                        id = log.id,
                        userId = profile.uid,
                        peerId = if (peerUid.isNotBlank() && !peerUid.startsWith("contact_")) peerUid else null,
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

    private fun getZegoErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            0 -> "SUCCESS (0)"
            1000001 -> "PARAM_INVALID (1000001)"
            1002001 -> "AUTH_FAILED (1002001) - Check AppID/AppSign"
            1002036 -> "ROOM_ALREADY_IN_ROOM (1002036)"
            1002050 -> "ROOM_COUNT_LIMIT_EXCEEDED (1002050)"
            1003001 -> "PUBLISH_STREAM_FAILED (1003001)"
            1003005 -> "PUBLISH_STREAM_ID_CONFLICT (1003005)"
            1004001 -> "PLAY_STREAM_FAILED (1004001)"
            1004002 -> "PLAY_STREAM_NOT_EXIST (1004002)"
            1009001 -> "TRIAL_LIMIT_EXCEEDED (1009001)"
            else -> "CODE_$errorCode"
        }
    }
}
