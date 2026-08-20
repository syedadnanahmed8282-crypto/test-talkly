package com.family.talkly.data.zego

import android.app.Application
import android.content.Context
import android.util.Log
import android.view.View
import com.family.talkly.data.firebase.FirebaseChatRepository
import com.family.talkly.data.models.CallDirection
import com.family.talkly.data.models.CallLog
import com.family.talkly.data.models.CallType
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.UserProfile
import com.family.talkly.util.CallSoundManager
import com.family.talkly.util.PhoneUtils
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        const val FIREBASE_PROJECT_ID: String = "familycallapp-e6b21"

        @Volatile
        private var INSTANCE: ZegoCallEngineManager? = null

        fun getInstance(context: Context): ZegoCallEngineManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ZegoCallEngineManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val callSoundManager = CallSoundManager(context)

    private val firestore: FirebaseFirestore? by lazy {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    }

    private var expressEngine: ZegoExpressEngine? = null
    private var isJoinedRoom = false
    private var localViewRef: View? = null
    private var remoteViewRef: View? = null

    private var activeCallListener: ListenerRegistration? = null
    private var secondaryCallListener: ListenerRegistration? = null
    private var thirdCallListener: ListenerRegistration? = null
    private var currentSyncedUserId: String? = null

    var currentUserProfile: UserProfile? = null
    var chatRepository: FirebaseChatRepository? = null

    private val _callState = MutableStateFlow(CurrentCallInfo())
    val callState: StateFlow<CurrentCallInfo> = _callState.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLog>>(emptyList())
    val callLogs: StateFlow<List<CallLog>> = _callLogs.asStateFlow()

    private var timerJob: Job? = null
    private var ringingTimeoutJob: Job? = null
    private var lastMissedCallSessionId: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    var onCallLogAdded: ((CallLog) -> Unit)? = null

    private fun sendMissedCallMessageOnce(targetMember: FamilyMember?, callType: CallType, roomId: String) {
        if (targetMember == null || roomId.isBlank()) return
        if (lastMissedCallSessionId == roomId) {
            Log.d(TAG, "Missed call message already sent for session $roomId, ignoring duplicate attempt.")
            return
        }
        lastMissedCallSessionId = roomId
        val msgText = if (callType == CallType.VIDEO) "Missed video call 📹" else "Missed audio call 📞"
        Log.d(TAG, "Sending single missed call message for session $roomId to ${targetMember.name}")
        chatRepository?.sendMessage(
            memberId = targetMember.id,
            textContent = msgText,
            type = com.family.talkly.data.models.MessageType.TEXT
        )
    }

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

            expressEngine = ZegoExpressEngine.createEngine(profile, object : IZegoEventHandler() {
                override fun onRoomUserUpdate(
                    roomID: String?,
                    updateType: ZegoUpdateType?,
                    userList: ArrayList<ZegoUser>?
                ) {
                    Log.d(TAG, "onRoomUserUpdate: roomID=$roomID, updateType=$updateType, users=${userList?.size}")
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
                    Log.d(TAG, "onRoomStreamUpdate: roomID=$roomID, updateType=$updateType, streams=${streamList.size}")

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
                            Log.d(TAG, "Remote stream ADDED: streamID=$streamID by user=${stream.user?.userID}")
                            _callState.value = _callState.value.copy(
                                remoteStreamId = streamID,
                                isRemoteStreamPlaying = true
                            )
                            bindRemoteStream(streamID)
                        }
                    } else if (updateType == ZegoUpdateType.DELETE) {
                        for (stream in streamList) {
                            val streamID = stream.streamID
                            Log.d(TAG, "Remote stream DELETED: streamID=$streamID")
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
                    Log.d(TAG, "onRoomStateChanged: roomID=$roomID, reason=$reason, errorCode=$errorCode")
                }

                override fun onPublisherStateUpdate(
                    streamID: String?,
                    state: ZegoPublisherState?,
                    errorCode: Int,
                    extendedData: JSONObject?
                ) {
                    Log.d(TAG, "onPublisherStateUpdate: streamID=$streamID, state=$state, errorCode=$errorCode")
                }

                override fun onPlayerStateUpdate(
                    streamID: String?,
                    state: ZegoPlayerState?,
                    errorCode: Int,
                    extendedData: JSONObject?
                ) {
                    Log.d(TAG, "onPlayerStateUpdate: streamID=$streamID, state=$state, errorCode=$errorCode")
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
        expressEngine?.loginRoom(roomID, user, roomConfig)

        // Publish local audio/video stream
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
                expressEngine?.logoutRoom(roomID)
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
            enableBeautyFilter(true)
            val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
            expressEngine?.startPreview(canvas)
            Log.d(TAG, "Attached local video preview view with beauty filter")
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
        remoteViewRef?.let { view ->
            val canvas = ZegoCanvas(view).apply { viewMode = ZegoViewMode.ASPECT_FILL }
            expressEngine?.startPlayingStream(streamID, canvas)
            Log.d(TAG, "Playing remote stream $streamID on remote view")
        } ?: run {
            expressEngine?.startPlayingStream(streamID, ZegoCanvas(null))
            Log.d(TAG, "Playing remote stream $streamID as audio-only / background canvas")
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
            activeCallListener?.remove()
            activeCallListener = null
            secondaryCallListener?.remove()
            secondaryCallListener = null
            thirdCallListener?.remove()
            thirdCallListener = null
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
        if (currentSyncedUserId == uid && activeCallListener != null) return

        activeCallListener?.remove()
        secondaryCallListener?.remove()
        currentSyncedUserId = uid

        val handleCallSnapshot: (DocumentSnapshot?, Exception?) -> Unit = { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listen failed for active_calls: ${error.localizedMessage}")
            } else {
                handleCallDocSnapshot(snapshot)
            }
        }

        try {
            activeCallListener = firestore?.collection("active_calls")
                ?.document("user_$uid")
                ?.addSnapshotListener { snapshot, error -> handleCallSnapshot(snapshot, error) }

            val suffix = userProfile.phoneSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(userProfile.phoneNumber) }
            val cleanPhone = PhoneUtils.cleanPhoneNumber(userProfile.phoneNumber)

            if (suffix.isNotBlank() && suffix != uid) {
                secondaryCallListener = firestore?.collection("active_calls")
                    ?.document("user_$suffix")
                    ?.addSnapshotListener { snapshot, error -> handleCallSnapshot(snapshot, error) }
            }
            if (cleanPhone.isNotBlank() && cleanPhone != uid && cleanPhone != suffix) {
                thirdCallListener = firestore?.collection("active_calls")
                    ?.document("user_$cleanPhone")
                    ?.addSnapshotListener { snapshot, error -> handleCallSnapshot(snapshot, error) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error starting realtime call sync: ${e.localizedMessage}")
        }
    }

    private fun handleCallDocSnapshot(doc: DocumentSnapshot?) {
        if (doc == null || !doc.exists()) {
            val currentState = _callState.value.state
            if (currentState == CallState.INCOMING_RINGING) {
                endCallInternal("Call Cancelled")
            }
            return
        }

        val id = doc.getString("id") ?: ""
        val callerUid = doc.getString("callerUid") ?: ""
        val callerName = doc.getString("callerName") ?: "Talkly User"
        val callerPhone = doc.getString("callerPhone") ?: ""
        val callerSuffix = doc.getString("callerSuffix") ?: PhoneUtils.extractPhoneSuffix(callerPhone)
        val callerAvatarUrl = doc.getString("callerAvatarUrl") ?: ""

        val receiverUid = doc.getString("receiverUid") ?: ""
        val receiverPhone = doc.getString("receiverPhone") ?: ""
        val receiverSuffix = doc.getString("receiverSuffix") ?: PhoneUtils.extractPhoneSuffix(receiverPhone)

        val callTypeStr = doc.getString("callType") ?: "VIDEO"
        val callType = try { CallType.valueOf(callTypeStr) } catch (e: Exception) { CallType.VIDEO }
        val status = doc.getString("status") ?: ""
        val roomID = doc.getString("roomID") ?: id

        val myProfile = currentUserProfile ?: getLocalUserProfile()
        val myUid = myProfile.uid
        val myPhone = myProfile.phoneNumber
        val mySuffix = myProfile.phoneSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(myPhone) }

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

        when (status) {
            "CALLING", "RINGING" -> {
                if (isMeReceiver && !isMeCaller) {
                    val currentState = _callState.value.state
                    if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING || currentState == CallState.ACTIVE) {
                        Log.d(TAG, "Ignoring incoming call doc: already in active/outgoing call ($currentState)")
                        return
                    }

                    val callTimestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val callAgeMs = System.currentTimeMillis() - callTimestamp
                    if (callAgeMs > 45_000L) {
                        Log.d(TAG, "Ignoring stale incoming call (age: ${callAgeMs}ms)")
                        try {
                            firestore?.collection("active_calls")?.document("user_$myUid")?.update("status", "MISSED")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update stale call status: ${e.localizedMessage}")
                        }
                        return
                    }

                    // Fire PEER_RINGING ACK back to caller instantly
                    try {
                        val ringingAck = mapOf("status" to "PEER_RINGING", "updatedAt" to System.currentTimeMillis())
                        if (id.isNotBlank()) {
                            firestore?.collection("active_calls")?.document(id)?.set(ringingAck, com.google.firebase.firestore.SetOptions.merge())
                        }
                        if (callerUid.isNotBlank()) {
                            firestore?.collection("active_calls")?.document("user_$callerUid")?.set(ringingAck, com.google.firebase.firestore.SetOptions.merge())
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error sending PEER_RINGING ACK: ${e.localizedMessage}")
                    }

                    if (currentState == CallState.IDLE || currentState == CallState.ENDED) {
                        callSoundManager.startIncomingRingtone()
                        val incomingCaller = FamilyMember(
                            id = if (callerSuffix.isNotBlank()) callerSuffix else callerUid,
                            name = if (callerName.isNotBlank()) callerName else "Talkly User",
                            phone = callerPhone,
                            relation = "Family Member",
                            status = "Incoming call...",
                            avatarUrl = if (callerAvatarUrl.isNotBlank()) callerAvatarUrl else null,
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
                    }
                }
            }
            "PEER_RINGING" -> {
                if (isMeCaller) {
                    val currentState = _callState.value.state
                    if (currentState == CallState.OUTGOING_CALLING) {
                        Log.d(TAG, "Recipient device received signal. Transitioning state to OUTGOING_RINGING")
                        _callState.value = _callState.value.copy(state = CallState.OUTGOING_RINGING)
                    }
                }
            }
            "ACCEPTED", "PEER_ANSWERED" -> {
                if (isMeCaller) {
                    val currentState = _callState.value.state
                    if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING) {
                        lastMissedCallSessionId = roomID
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
            "DECLINED" -> {
                val currentState = _callState.value.state
                if (currentState != CallState.IDLE && currentState != CallState.ENDED) {
                    if (isMeCaller) {
                        android.widget.Toast.makeText(context, "Call declined", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    endCallInternal("Call Declined")
                }
            }
            "ENDED", "TIMED_OUT", "CANCELLED" -> {
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
        val cleanReceiverPhone = PhoneUtils.cleanPhoneNumber(targetPhone)

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

        val callData = mapOf(
            "id" to roomID,
            "callerUid" to callerProfile.uid,
            "callerName" to callerProfile.name,
            "callerPhone" to callerProfile.phoneNumber,
            "callerSuffix" to callerProfile.phoneSuffix,
            "callerAvatarUrl" to callerProfile.profilePicUrl,
            "receiverUid" to targetUid,
            "receiverPhone" to targetPhone,
            "receiverSuffix" to targetSuffix,
            "callType" to callType.name,
            "status" to "CALLING",
            "roomID" to roomID,
            "timestamp" to System.currentTimeMillis()
        )

        publishCallSignalToTargets(callerProfile, targetUid, targetSuffix, callData)

        // Async resolution if targetUid was blank to ensure recipient receives call without delay
        if (targetUid.isBlank() && targetSuffix.isNotBlank()) {
            scope.launch {
                try {
                    firestore?.collection("users_phone_index")?.document(targetSuffix)?.get()
                        ?.addOnSuccessListener { doc ->
                            val foundUid = doc?.getString("uid") ?: doc?.getString("firebaseUid")
                            if (!foundUid.isNullOrBlank()) {
                                val updatedData = callData.toMutableMap().apply { put("receiverUid", foundUid) }
                                publishCallSignalToTargets(callerProfile, foundUid, targetSuffix, updatedData)
                            }
                        }
                    firestore?.collection("users")?.whereEqualTo("phoneSuffix", targetSuffix)?.get()
                        ?.addOnSuccessListener { snap ->
                            val foundUid = snap?.documents?.firstOrNull()?.id
                            if (!foundUid.isNullOrBlank()) {
                                val updatedData = callData.toMutableMap().apply { put("receiverUid", foundUid) }
                                publishCallSignalToTargets(callerProfile, foundUid, targetSuffix, updatedData)
                            }
                        }
                } catch (e: Exception) {
                    Log.w(TAG, "Async target lookup for call exception: ${e.localizedMessage}")
                }
            }
        }

        ringingTimeoutJob?.cancel()

        ringingTimeoutJob = scope.launch {
            delay(30000)
            val currentState = _callState.value.state
            if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING) {
                Log.d(TAG, "Call timed out after 30s: ${member.name} is unavailable or unreachable")
                android.widget.Toast.makeText(context, "${member.name} is unavailable / unreachable", android.widget.Toast.LENGTH_SHORT).show()

                val timedOutData = callData.toMutableMap()
                timedOutData["status"] = "UNAVAILABLE"
                publishCallSignalToTargets(callerProfile, targetUid, targetSuffix, timedOutData)

                sendMissedCallMessageOnce(member, callType, roomID)

                addCallLog(
                    CallLog(
                        id = "call_${System.currentTimeMillis()}",
                        memberId = member.id,
                        memberName = member.name,
                        direction = CallDirection.OUTGOING,
                        callType = callType,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0
                    )
                )

                endCallInternal("No Answer")
            }
        }
    }

    private fun publishCallSignalToTargets(
        callerProfile: UserProfile,
        targetUid: String,
        targetSuffix: String,
        data: Map<String, Any>
    ) {
        try {
            val db = firestore ?: return
            val receiverPhone = data["receiverPhone"] as? String ?: ""
            val cleanReceiverPhone = PhoneUtils.cleanPhoneNumber(receiverPhone)
            val roomID = data["roomID"] as? String ?: (data["id"] as? String ?: "")

            if (roomID.isNotBlank()) {
                db.collection("active_calls").document(roomID).set(data)
            }
            if (targetUid.isNotBlank() && targetUid != "self") {
                db.collection("active_calls").document("user_$targetUid").set(data)
            }
            if (targetSuffix.isNotBlank() && targetSuffix != targetUid) {
                db.collection("active_calls").document("user_$targetSuffix").set(data)
            }
            if (cleanReceiverPhone.isNotBlank() && cleanReceiverPhone != targetUid && cleanReceiverPhone != targetSuffix) {
                db.collection("active_calls").document("user_$cleanReceiverPhone").set(data)
            }

            // Send high priority FCM push for incoming calls
            val status = data["status"] as? String ?: ""
            if (status.equals("RINGING", ignoreCase = true) || status.equals("CALLING", ignoreCase = true)) {
                val fcmPayload = mapOf(
                    "type" to "INCOMING_CALL",
                    "callerName" to callerProfile.name,
                    "callerUid" to callerProfile.uid,
                    "caller_id" to callerProfile.uid,
                    "callerPhone" to callerProfile.phoneNumber,
                    "callerAvatarUrl" to callerProfile.profilePicUrl,
                    "roomID" to (data["roomID"] as? String ?: ""),
                    "callType" to (data["callType"] as? String ?: "VIDEO"),
                    "status" to "RINGING"
                )
                com.family.talkly.util.FcmTokenManager.sendHighPriorityPush(
                    targetUid = targetUid,
                    targetPhoneSuffix = targetSuffix,
                    dataPayload = fcmPayload
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error publishing call signal to Firestore: ${e.localizedMessage}")
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

    private fun publishCallUpdateToTargets(
        callerProfile: UserProfile,
        targetUid: String,
        targetSuffix: String,
        newStatus: String
    ) {
        try {
            val db = firestore ?: return
            val updateMap = mapOf<String, Any>(
                "status" to newStatus,
                "timestamp" to System.currentTimeMillis()
            )
            if (targetUid.isNotBlank() && targetUid != "self") {
                db.collection("active_calls").document("user_$targetUid").set(updateMap, SetOptions.merge())
            }
            if (targetSuffix.isNotBlank() && targetSuffix != targetUid) {
                db.collection("active_calls").document("user_$targetSuffix").set(updateMap, SetOptions.merge())
            }
            if (callerProfile.uid.isNotBlank() && callerProfile.uid != "self") {
                db.collection("active_calls").document("user_${callerProfile.uid}").set(updateMap, SetOptions.merge())
            }
            if (callerProfile.phoneSuffix.isNotBlank() && callerProfile.phoneSuffix != callerProfile.uid) {
                db.collection("active_calls").document("user_${callerProfile.phoneSuffix}").set(updateMap, SetOptions.merge())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error updating call status in Firestore: ${e.localizedMessage}")
        }
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
        val myProfile = currentUserProfile ?: getLocalUserProfile()

        val targetUid = member?.firebaseUid ?: if (!member?.id.orEmpty().startsWith("contact_")) member?.id.orEmpty() else ""
        val targetSuffix = PhoneUtils.extractPhoneSuffix(member?.phone ?: "")

        val isVideo = (current.callType == CallType.VIDEO)
        callSoundManager.configureAudioForActiveCall(isSpeakerOn = isVideo, isMuted = current.isMuted)

        publishCallUpdateToTargets(myProfile, targetUid, targetSuffix, "ACCEPTED")

        lastMissedCallSessionId = current.roomID
        _callState.value = current.copy(state = CallState.ACTIVE, isSpeakerOn = isVideo, isOutgoing = false)
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
        val myProfile = currentUserProfile ?: getLocalUserProfile()

        val targetUid = member?.firebaseUid ?: if (!member?.id.orEmpty().startsWith("contact_")) member?.id.orEmpty() else ""
        val targetSuffix = PhoneUtils.extractPhoneSuffix(member?.phone ?: "")

        publishCallUpdateToTargets(myProfile, targetUid, targetSuffix, "DECLINED")

        if (member != null) {
            sendMissedCallMessageOnce(member, current.callType, current.roomID)
            addCallLog(
                CallLog(
                    id = "call_${System.currentTimeMillis()}",
                    memberId = member.id,
                    memberName = member.name,
                    direction = CallDirection.MISSED,
                    callType = current.callType,
                    timestamp = System.currentTimeMillis(),
                    durationSeconds = 0
                )
            )
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
        val myProfile = currentUserProfile ?: getLocalUserProfile()

        val targetUid = member?.firebaseUid ?: if (!member?.id.orEmpty().startsWith("contact_")) member?.id.orEmpty() else ""
        val targetSuffix = PhoneUtils.extractPhoneSuffix(member?.phone ?: "")

        publishCallUpdateToTargets(myProfile, targetUid, targetSuffix, "ENDED")
        lastMissedCallSessionId = current.roomID

        if (member != null) {
            val isOutgoing = current.isOutgoing || current.state == CallState.OUTGOING_RINGING || current.state == CallState.OUTGOING_CALLING
            val direction = if (isOutgoing) CallDirection.OUTGOING else CallDirection.INCOMING
            addCallLog(
                CallLog(
                    id = "call_${System.currentTimeMillis()}",
                    memberId = member.id,
                    memberName = member.name,
                    direction = direction,
                    callType = current.callType,
                    timestamp = System.currentTimeMillis(),
                    durationSeconds = current.durationSeconds
                )
            )
        }
        endCallInternal("Call Ended")
    }

    private fun endCallInternal(reason: String) {
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

        val profile = currentUserProfile ?: getLocalUserProfile()
        try {
            if (profile.uid.isNotBlank() && profile.uid != "self") {
                firestore?.collection("active_calls")?.document("user_${profile.uid}")?.delete()
            }
            if (profile.phoneSuffix.isNotBlank()) {
                firestore?.collection("active_calls")?.document("user_${profile.phoneSuffix}")?.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing active call document: ${e.localizedMessage}")
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
    }
}
