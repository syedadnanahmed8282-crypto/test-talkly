package com.family.talkly.data.zego

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val stunServers: List<String> = ZegoCallEngineManager.PUBLIC_STUN_SERVERS,
    val turnUsername: String = "openrelayproject",
    val turnPassword: String = "openrelayproject",
    val offerToReceiveAudio: Boolean = true,
    val offerToReceiveVideo: Boolean = true,
    val isRemoteAudioTrackAttached: Boolean = true,
    val isRemoteVideoTrackAttached: Boolean = true,
    val isMicrophoneMuted: Boolean = false,
    val isPublishAudioMuted: Boolean = false
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
        val ZEGO_APP_ID: Long = 2119647829L
        val ZEGO_APP_SIGN: String = "f7b21c961d9ae91fc3ca9ee453c6ff4027c451e93e59ceaeeecfcafd29bdc872"
        const val FIREBASE_PROJECT_ID: String = "familycallapp-e6b21"
    }

    private val callSoundManager = CallSoundManager(context)

    private val firestore: FirebaseFirestore? by lazy {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
    }

    private var activeCallListener: ListenerRegistration? = null
    private var secondaryCallListener: ListenerRegistration? = null
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
    var onCallLogAdded: ((CallLog) -> Unit)? = null

    init {
        Log.i(TAG, "ZEGOCloud Express Engine configured with AppID: $ZEGO_APP_ID for Firebase Project $FIREBASE_PROJECT_ID")
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
            callSoundManager.stopAllSounds()
            activeCallListener?.remove()
            activeCallListener = null
            secondaryCallListener?.remove()
            secondaryCallListener = null
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

            val suffix = userProfile.phoneSuffix
            if (suffix.isNotBlank() && suffix != uid) {
                secondaryCallListener = firestore?.collection("active_calls")
                    ?.document("user_$suffix")
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
        val mySuffix = myProfile.phoneSuffix

        val isMeCaller = (myUid.isNotBlank() && callerUid == myUid) || (mySuffix.isNotBlank() && callerSuffix == mySuffix)
        val isMeReceiver = (myUid.isNotBlank() && receiverUid == myUid) || (mySuffix.isNotBlank() && receiverSuffix == mySuffix) || (!isMeCaller)

        when (status) {
            "RINGING" -> {
                if (isMeReceiver && !isMeCaller) {
                    val currentState = _callState.value.state
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
            "ACCEPTED" -> {
                if (isMeCaller) {
                    val currentState = _callState.value.state
                    if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING) {
                        ringingTimeoutJob?.cancel()
                        callSoundManager.stopAllSounds()
                        val isVideo = (_callState.value.callType == CallType.VIDEO)
                        callSoundManager.configureAudioForActiveCall(isSpeakerOn = isVideo, isMuted = _callState.value.isMuted)
                        _callState.value = _callState.value.copy(state = CallState.ACTIVE, isSpeakerOn = isVideo)
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
            isSpeakerOn = isVideo
        )
        callSoundManager.startOutgoingRingbackTone()
        Log.d(TAG, "Starting outgoing ${callType.name} call to ${member.name} (targetUid: $targetUid, suffix: $targetSuffix)")

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
            "status" to "RINGING",
            "roomID" to roomID,
            "timestamp" to System.currentTimeMillis()
        )

        publishCallSignalToTargets(callerProfile, targetUid, targetSuffix, callData)

        ringingTimeoutJob?.cancel()

        scope.launch {
            delay(1500)
            if (_callState.value.state == CallState.OUTGOING_CALLING) {
                _callState.value = _callState.value.copy(state = CallState.OUTGOING_RINGING)
            }
        }

        ringingTimeoutJob = scope.launch {
            delay(30000)
            val currentState = _callState.value.state
            if (currentState == CallState.OUTGOING_CALLING || currentState == CallState.OUTGOING_RINGING) {
                Log.d(TAG, "Call timed out after 30s: No answer from ${member.name}")
                android.widget.Toast.makeText(context, "No answer from ${member.name}", android.widget.Toast.LENGTH_SHORT).show()

                val timedOutData = callData.toMutableMap()
                timedOutData["status"] = "TIMED_OUT"
                publishCallSignalToTargets(callerProfile, targetUid, targetSuffix, timedOutData)

                val msgText = if (callType == CallType.VIDEO) "Missed video call 📹" else "Missed audio call 📞"
                chatRepository?.sendMessage(
                    memberId = member.id,
                    textContent = msgText,
                    type = com.family.talkly.data.models.MessageType.TEXT
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
            if (targetUid.isNotBlank() && targetUid != "self") {
                db.collection("active_calls").document("user_$targetUid").set(data)
            }
            if (targetSuffix.isNotBlank() && targetSuffix != targetUid) {
                db.collection("active_calls").document("user_$targetSuffix").set(data)
            }
            if (callerProfile.uid.isNotBlank() && callerProfile.uid != "self") {
                db.collection("active_calls").document("user_${callerProfile.uid}").set(data)
            }
            if (callerProfile.phoneSuffix.isNotBlank() && callerProfile.phoneSuffix != callerProfile.uid) {
                db.collection("active_calls").document("user_${callerProfile.phoneSuffix}").set(data)
            }

            // Send high priority FCM push for incoming calls
            val status = data["status"] as? String ?: ""
            if (status.equals("RINGING", ignoreCase = true)) {
                val fcmPayload = mapOf(
                    "type" to "INCOMING_CALL",
                    "callerName" to callerProfile.name,
                    "callerUid" to callerProfile.uid,
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
        callSoundManager.stopAllSounds()
        val current = _callState.value
        val member = current.targetMember
        val myProfile = currentUserProfile ?: getLocalUserProfile()

        val targetUid = member?.firebaseUid ?: if (!member?.id.orEmpty().startsWith("contact_")) member?.id.orEmpty() else ""
        val targetSuffix = PhoneUtils.extractPhoneSuffix(member?.phone ?: "")

        val isVideo = (current.callType == CallType.VIDEO)
        callSoundManager.configureAudioForActiveCall(isSpeakerOn = isVideo, isMuted = current.isMuted)

        publishCallUpdateToTargets(myProfile, targetUid, targetSuffix, "ACCEPTED")

        _callState.value = current.copy(state = CallState.ACTIVE, isSpeakerOn = isVideo)
        startCallTimer()
    }

    fun declineCall() {
        ringingTimeoutJob?.cancel()
        callSoundManager.stopAllSounds()
        val current = _callState.value
        val member = current.targetMember
        val myProfile = currentUserProfile ?: getLocalUserProfile()

        val targetUid = member?.firebaseUid ?: if (!member?.id.orEmpty().startsWith("contact_")) member?.id.orEmpty() else ""
        val targetSuffix = PhoneUtils.extractPhoneSuffix(member?.phone ?: "")

        publishCallUpdateToTargets(myProfile, targetUid, targetSuffix, "DECLINED")

        if (member != null) {
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

            val msgText = if (current.callType == CallType.VIDEO) "Missed video call 📹" else "Missed audio call 📞"
            chatRepository?.sendMessage(
                memberId = member.id,
                textContent = msgText,
                type = com.family.talkly.data.models.MessageType.TEXT
            )
        }
        endCallInternal("Call Declined")
    }

    fun endCall() {
        ringingTimeoutJob?.cancel()
        callSoundManager.stopAllSounds()
        val current = _callState.value
        val member = current.targetMember
        val myProfile = currentUserProfile ?: getLocalUserProfile()

        val targetUid = member?.firebaseUid ?: if (!member?.id.orEmpty().startsWith("contact_")) member?.id.orEmpty() else ""
        val targetSuffix = PhoneUtils.extractPhoneSuffix(member?.phone ?: "")

        publishCallUpdateToTargets(myProfile, targetUid, targetSuffix, "ENDED")

        if (member != null) {
            val direction = if (current.state == CallState.OUTGOING_RINGING || current.state == CallState.OUTGOING_CALLING) CallDirection.OUTGOING else CallDirection.INCOMING
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
        callSoundManager.stopAllSounds()
        callSoundManager.resetAudioMode()
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
        callSoundManager.setMicrophoneMute(newMuted)
    }

    fun toggleCamera() {
        val current = _callState.value
        _callState.value = current.copy(isCameraOff = !current.isCameraOff)
    }

    fun flipCamera() {
        val current = _callState.value
        _callState.value = current.copy(isFrontCamera = !current.isFrontCamera)
    }

    fun toggleSpeaker() {
        val current = _callState.value
        val newSpeaker = !current.isSpeakerOn
        _callState.value = current.copy(isSpeakerOn = newSpeaker)
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

