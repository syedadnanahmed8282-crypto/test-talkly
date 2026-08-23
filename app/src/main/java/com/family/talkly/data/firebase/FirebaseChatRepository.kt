package com.family.talkly.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.ReactionUtils
import com.family.talkly.data.models.ReactionEntry
import com.family.talkly.data.models.DEFAULT_FAMILY_MEMBERS
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.MessageType
import com.family.talkly.util.MediaCompressorAndUploader
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle

import com.family.talkly.data.local.TalklyDatabase
import com.family.talkly.data.local.entity.ChatMessageEntity
import com.family.talkly.data.models.MessageRequest
import com.family.talkly.data.models.StatusItem
import com.family.talkly.data.models.UserStatusGroup
import com.family.talkly.data.models.StatusViewer
import com.family.talkly.data.models.StatusLiker
import com.family.talkly.util.PhoneUtils
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import com.family.talkly.data.supabase.SupabaseMessagingService
import com.family.talkly.data.supabase.SupabaseMessage
import com.family.talkly.data.supabase.SupabaseMessageRequest
import com.family.talkly.data.supabase.SupabaseProfile
import com.family.talkly.data.supabase.SupabaseClientProvider
import com.family.talkly.data.supabase.toSupabaseMessage
import com.family.talkly.data.supabase.SupabaseSocialService
import com.family.talkly.data.supabase.SupabaseContact
import com.family.talkly.data.supabase.SupabaseStatus
import com.family.talkly.data.supabase.toSupabaseContact

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

class FirebaseChatRepository(private val context: Context) {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val TAG = "Talkly_FirebaseChat"
        const val FIREBASE_PROJECT_ID = "familycallapp-e6b21"
        private const val CONTACTS_PREFS = "talkly_saved_contacts_prefs"
        private const val KEY_SAVED_CONTACTS_JSON = "saved_contacts_json"
        private const val KEY_DEMO_CLEARED = "demo_contacts_cleared"
        private const val KEY_STATUSES_JSON = "talkly_statuses_json"
        private const val KEY_BLOCKED_USERS = "talkly_blocked_user_ids"
        private const val KEY_DELETED_CONTACT_IDS = "talkly_deleted_contact_ids"

        @Volatile
        private var INSTANCE: FirebaseChatRepository? = null

        fun getInstance(context: Context): FirebaseChatRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseChatRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var supabaseRealtimeChannel: RealtimeChannel? = null
    private var currentSyncedUserId: String? = null
    private var messageSyncJob: Job? = null
    private val contactPrefs = context.getSharedPreferences(CONTACTS_PREFS, Context.MODE_PRIVATE)
    private val database: TalklyDatabase by lazy { TalklyDatabase.getInstance(context) }
    private val socialService: SupabaseSocialService by lazy { SupabaseSocialService.getInstance(context) }
    private var presenceJob: Job? = null

    // Connectivity state management
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val _isNetworkConnected = MutableStateFlow(isNetworkCurrentlyAvailable())
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private val _lastServerSyncTime = MutableStateFlow(0L)
    val lastServerSyncTime: StateFlow<Long> = _lastServerSyncTime.asStateFlow()

    private fun isNetworkCurrentlyAvailable(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setupNetworkMonitoring() {
        try {
            val cm = connectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "NetworkCallback: onAvailable -> network connected")
                    _isNetworkConnected.value = true
                    forceReconnectListeners("network_online")
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "NetworkCallback: onLost -> network disconnected")
                    // সত্যিই আর কোনো active network না থাকলে তবেই offline ধরা হবে
                    _isNetworkConnected.value = isNetworkCurrentlyAvailable()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    // শুধু positive সিগন্যাল বিশ্বাস করা হচ্ছে; negative/offline সিদ্ধান্ত
                    // শুধু onLost() থেকেই আসবে, যাতে ক্ষণিকের capability change এ ভুলভাবে offline না হয়
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    if (hasInternet) {
                        _isNetworkConnected.value = true
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Error registering network callback: ${e.localizedMessage}")
        }
    }

    // Real-time family members presence and status
    private val _familyMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers.asStateFlow()

    // Message Requests StateFlow
    private val _messageRequests = MutableStateFlow<List<MessageRequest>>(emptyList())
    val messageRequests: StateFlow<List<MessageRequest>> = _messageRequests.asStateFlow()

    // Set of UIDs or phone suffixes of users who saved current user in their contacts
    private val _contactsWhoSavedMe = MutableStateFlow<Set<String>>(emptySet())
    val contactsWhoSavedMe: StateFlow<Set<String>> = _contactsWhoSavedMe.asStateFlow()

    // Blocked Users state
    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    // Permanently Deleted Contact IDs / Suffixes state
    private val _deletedContactIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedContactIds: StateFlow<Set<String>> = _deletedContactIds.asStateFlow()

    // Time offset for live testing 48-hour expiration logic
    private val _simulatedTimeOffsetMs = MutableStateFlow(0L)
    val simulatedTimeOffsetMs: StateFlow<Long> = _simulatedTimeOffsetMs.asStateFlow()

    // Message maps by family member id
    private val _messagesMap = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messagesMap: StateFlow<Map<String, List<ChatMessage>>> = _messagesMap.asStateFlow()

    // Statuses flow (24-hour disappearing updates)
    private val _statuses = MutableStateFlow<List<StatusItem>>(emptyList())
    val statuses: StateFlow<List<StatusItem>> = _statuses.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val demoIdsSet = setOf(
        "safwan", "israfel", "jolil", "samim", "akhter", "osman", "mohammad_raiu",
        "dr_rashed", "monju", "sk_farid", "mom", "dad", "grandma", "brother", "sister"
    )

    init {
        setupNetworkMonitoring()
        loadDeletedContactIds()
        loadInitialFamilyMembers()
        seedInitialFamilyChats()
        loadStatuses()
        loadBlockedUsers()
    }


    private fun loadDeletedContactIds() {
        val set = contactPrefs.getStringSet(KEY_DELETED_CONTACT_IDS, emptySet()) ?: emptySet()
        _deletedContactIds.value = set
    }

    private fun markContactAsDeleted(ids: List<String>) {
        val cleanIds = ids.filter { it.isNotBlank() && it != "self" }
        if (cleanIds.isEmpty()) return
        val updated = _deletedContactIds.value.toMutableSet()
        updated.addAll(cleanIds)
        _deletedContactIds.value = updated
        contactPrefs.edit().putStringSet(KEY_DELETED_CONTACT_IDS, updated).apply()
    }

    private fun unmarkContactAsDeleted(ids: List<String>) {
        val cleanIds = ids.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return
        val updated = _deletedContactIds.value.toMutableSet()
        updated.removeAll(cleanIds.toSet())
        _deletedContactIds.value = updated
        contactPrefs.edit().putStringSet(KEY_DELETED_CONTACT_IDS, updated).apply()
    }

    fun loadBlockedUsers() {
        val set = contactPrefs.getStringSet(KEY_BLOCKED_USERS, emptySet()) ?: emptySet()
        _blockedUserIds.value = set
    }

    fun blockUser(userId: String) {
        val updated = _blockedUserIds.value.toMutableSet()
        updated.add(userId)
        _blockedUserIds.value = updated
        contactPrefs.edit().putStringSet(KEY_BLOCKED_USERS, updated).apply()
    }

    fun unblockUser(userId: String) {
        val updated = _blockedUserIds.value.toMutableSet()
        updated.remove(userId)
        _blockedUserIds.value = updated
        contactPrefs.edit().putStringSet(KEY_BLOCKED_USERS, updated).apply()
    }

    fun isUserBlocked(userId: String): Boolean {
        return _blockedUserIds.value.contains(userId)
    }

    private fun setFamilyMembersWithDeduplication(newList: List<FamilyMember>) {
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)

        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
        val currentPhone = sessionPrefs.getString("user_phone", null)
            ?: fallbackPrefs.getString("user_phone", null) ?: ""
        val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)

        val deletedSet = _deletedContactIds.value

        val filteredAndDeduplicated = newList
            .filter { member -> member.id !in demoIdsSet && !member.id.startsWith("demo_") }
            .filter { member ->
                val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
                val cleanMemberPhone = member.phone.filter { it.isDigit() }
                val cleanCurrentPhone = currentPhone.filter { it.isDigit() }

                val isSelfByUid = (!currentUid.isNullOrBlank() && (member.id == currentUid || member.firebaseUid == currentUid))
                val isSelfByPhone = (cleanCurrentPhone.isNotBlank() && cleanMemberPhone.isNotBlank() && cleanMemberPhone == cleanCurrentPhone)
                val isSelfBySuffix = (currentSuffix.isNotBlank() && memberSuffix.isNotBlank() && memberSuffix == currentSuffix)

                val isDeleted = member.id in deletedSet ||
                        (member.firebaseUid != null && member.firebaseUid in deletedSet) ||
                        (memberSuffix.isNotBlank() && memberSuffix in deletedSet) ||
                        (cleanMemberPhone.isNotBlank() && cleanMemberPhone in deletedSet)

                !(isSelfByUid || isSelfByPhone || isSelfBySuffix || isDeleted)
            }
            .sortedWith(
                compareByDescending<FamilyMember> { it.isRegisteredOnTalkly }
                    .thenByDescending { !it.firebaseUid.isNullOrBlank() }
                    .thenByDescending { !it.avatarUrl.isNullOrBlank() }
                    .thenByDescending { it.lastActiveTimestamp }
            )
            .distinctBy { member ->
                val digits = member.phone.filter { it.isDigit() }
                val suffix = if (digits.length >= 10) digits.takeLast(10) else digits
                if (suffix.isNotBlank()) {
                    "suffix_$suffix"
                } else if (!member.firebaseUid.isNullOrBlank()) {
                    "uid_${member.firebaseUid}"
                } else {
                    "id_${member.id}"
                }
            }

        _familyMembers.value = filteredAndDeduplicated
    }

    private fun loadInitialFamilyMembers() {
        val savedJson = contactPrefs.getString(KEY_SAVED_CONTACTS_JSON, null)
        val list = mutableListOf<FamilyMember>()

        if (!savedJson.isNullOrBlank()) {
            try {
                val jsonArray = org.json.JSONArray(savedJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val memberId = obj.getString("id")
                    if (memberId in demoIdsSet || memberId.startsWith("demo_")) continue

                    val member = FamilyMember(
                        id = memberId,
                        name = obj.getString("name"),
                        relation = obj.optString("relation", "Contact"),
                        avatarUrl = if (obj.has("avatarUrl") && !obj.isNull("avatarUrl")) obj.getString("avatarUrl") else null,
                        status = obj.optString("status", "Available for call 💬"),
                        phone = obj.getString("phone"),
                        isOnline = obj.optBoolean("isOnline", true),
                        isTyping = false,
                        lastSeen = obj.optString("lastSeen", "Recently"),
                        lastActiveTimestamp = obj.optLong("lastActiveTimestamp", System.currentTimeMillis()),
                        unreadCount = obj.optInt("unreadCount", 0),
                        isPinned = obj.optBoolean("isPinned", false),
                        isRegisteredOnTalkly = obj.optBoolean("isRegisteredOnTalkly", false),
                        firebaseUid = if (obj.has("firebaseUid") && !obj.isNull("firebaseUid")) obj.getString("firebaseUid") else null
                    )
                    if (list.none { it.id == member.id }) {
                        list.add(member)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse saved contacts JSON: ${e.message}")
            }
        }

        setFamilyMembersWithDeduplication(list)
        contactPrefs.edit().putBoolean(KEY_DEMO_CLEARED, true).apply()
        saveContactsToPrefs()
    }

    private fun saveContactsToPrefs() {
        try {
            val jsonArray = org.json.JSONArray()
            _familyMembers.value.forEach { member ->
                val obj = org.json.JSONObject().apply {
                    put("id", member.id)
                    put("name", member.name)
                    put("relation", member.relation)
                    put("avatarUrl", member.avatarUrl)
                    put("status", member.status)
                    put("phone", member.phone)
                    put("isOnline", member.isOnline)
                    put("lastSeen", member.lastSeen)
                    put("lastActiveTimestamp", member.lastActiveTimestamp)
                    put("unreadCount", member.unreadCount)
                    put("isPinned", member.isPinned)
                    put("isRegisteredOnTalkly", member.isRegisteredOnTalkly)
                    put("firebaseUid", member.firebaseUid)
                }
                jsonArray.put(obj)
            }
            contactPrefs.edit()
                .putString(KEY_SAVED_CONTACTS_JSON, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving contacts to prefs: ${e.message}")
        }
    }

    fun searchTalklyUserByPhone(phone: String, onResult: (com.family.talkly.data.models.UserProfile?) -> Unit) {
        val targetSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(phone)
        val cleanPhone = com.family.talkly.util.PhoneUtils.cleanPhoneNumber(phone)
        if (targetSuffix.isBlank()) {
            onResult(null)
            return
        }

        repositoryScope.launch(Dispatchers.IO) {
            try {
                val profiles = SupabaseClientProvider.client.postgrest["profiles"]
                    .select {
                        filter {
                            or {
                                eq("phone", cleanPhone)
                                eq("phone_suffix", targetSuffix)
                                eq("phone", phone)
                            }
                        }
                        limit(1)
                    }
                    .decodeList<SupabaseProfile>()

                val found = profiles.firstOrNull()
                withContext(Dispatchers.Main) {
                    if (found != null) {
                        val profile = com.family.talkly.data.models.UserProfile(
                            uid = found.id,
                            name = found.name.ifBlank { "Talkly User" },
                            phoneNumber = if (found.phone.isNotBlank()) found.phone else phone,
                            phoneSuffix = if (found.phoneSuffix.isNotBlank()) found.phoneSuffix else targetSuffix,
                            profilePicUrl = found.avatarUrl,
                            bio = found.bio
                        )
                        onResult(profile)
                    } else {
                        onResult(null)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search user exception in Supabase: ${e.localizedMessage}")
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun addNewContact(
        name: String,
        phone: String,
        relation: String = "Family Member",
        bio: String = "Available for call 💬",
        avatarUrl: String? = null,
        onComplete: ((FamilyMember) -> Unit)? = null
    ) {
        val cleanPhone = phone.trim()
        val phoneSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(cleanPhone)

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null) ?: "self"
        val myPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val mySuffix = PhoneUtils.extractPhoneSuffix(myPhone)

        // Exclude current logged-in user from self-contacts
        if ((currentUid.isNotBlank() && currentUid != "self" && cleanPhone == currentUid) ||
            (myPhone.isNotBlank() && cleanPhone == myPhone) ||
            (mySuffix.isNotBlank() && phoneSuffix.isNotBlank() && phoneSuffix == mySuffix)) {
            Log.w(TAG, "Prevented adding logged-in user as self-contact")
            return
        }

        repositoryScope.launch(Dispatchers.IO) {
            val searchRes = socialService.searchUserByPhone(cleanPhone)
            val matchedProfile = searchRes.getOrNull()

            val targetUid = matchedProfile?.id
            val realName = matchedProfile?.name?.takeIf { it.isNotBlank() } ?: name.trim()
            val realAvatar = matchedProfile?.avatarUrl ?: avatarUrl
            val realBio = matchedProfile?.bio?.takeIf { it.isNotBlank() } ?: bio

            val contactId = if (!targetUid.isNullOrBlank() && !targetUid.startsWith("contact_")) targetUid else "contact_${phoneSuffix.ifBlank { cleanPhone.replace("+", "").replace(" ", "") }}"

            unmarkContactAsDeleted(listOf(contactId, cleanPhone, phoneSuffix, targetUid ?: ""))

            val newMember = FamilyMember(
                id = contactId,
                name = realName,
                relation = relation.ifBlank { "Family Member" },
                avatarUrl = realAvatar,
                status = realBio ?: "Available on Talkly 💬",
                phone = cleanPhone,
                isOnline = true,
                isTyping = false,
                lastSeen = "Online",
                unreadCount = 0,
                isPinned = false,
                isRegisteredOnTalkly = !targetUid.isNullOrBlank(),
                firebaseUid = if (!targetUid.isNullOrBlank() && !targetUid.startsWith("contact_")) targetUid else null
            )

            withContext(Dispatchers.Main) {
                val currentList = _familyMembers.value.toMutableList()
                currentList.removeAll { 
                    it.id == contactId || 
                    it.phone == cleanPhone ||
                    (!targetUid.isNullOrBlank() && (it.id == targetUid || it.firebaseUid == targetUid)) ||
                    (phoneSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(it.phone) == phoneSuffix)
                }
                currentList.add(0, newMember)
                setFamilyMembersWithDeduplication(currentList)
                saveContactsToPrefs()
                onComplete?.invoke(newMember)
            }

            // Persist contact in Supabase `contacts` table
            if (currentUid.isNotBlank() && currentUid != "self") {
                val supabaseContact = SupabaseContact(
                    userId = currentUid,
                    contactUserId = targetUid,
                    contactName = realName,
                    contactPhone = cleanPhone,
                    contactPhoneSuffix = phoneSuffix,
                    relation = relation.ifBlank { "Family Member" },
                    isPinned = false,
                    isMutual = false,
                    status = "ACCEPTED"
                )
                socialService.saveContact(supabaseContact)
            }
        }
    }

    fun deleteContact(memberId: String) {
        val canonicalId = getCanonicalMemberId(memberId)
        val targetMember = _familyMembers.value.firstOrNull {
            it.id == memberId || it.id == canonicalId || it.firebaseUid == memberId || it.firebaseUid == canonicalId
        }
        val targetPhone = targetMember?.phone ?: if (memberId.startsWith("+") || memberId.all { it.isDigit() }) memberId else ""
        val targetSuffix = PhoneUtils.extractPhoneSuffix(targetPhone)
        val targetFirebaseUid = targetMember?.firebaseUid ?: ""

        // Mark as permanently deleted locally
        markContactAsDeleted(listOf(memberId, canonicalId, targetFirebaseUid, targetSuffix, targetPhone))

        // 1. Purge chat history and media for this contact permanently
        deleteChatHistory(memberId)

        // 2. Remove contact from _familyMembers and local contact cache
        val updatedList = _familyMembers.value.filter { member ->
            member.id != memberId &&
            member.id != canonicalId &&
            (targetFirebaseUid.isBlank() || member.firebaseUid != targetFirebaseUid) &&
            (targetSuffix.isBlank() || PhoneUtils.extractPhoneSuffix(member.phone) != targetSuffix)
        }
        setFamilyMembersWithDeduplication(updatedList)
        saveContactsToPrefs()

        // 3. Delete contact record from Supabase `contacts` table
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null) ?: "self"

        if (currentUid.isNotBlank() && currentUid != "self" && targetSuffix.isNotBlank()) {
            repositoryScope.launch(Dispatchers.IO) {
                socialService.deleteContact(currentUid, targetSuffix)
            }
        }

        // 4. Purge message requests for this contact
        val remainingRequests = _messageRequests.value.filterNot { req ->
            (req.senderId == memberId || req.senderId == canonicalId || (targetFirebaseUid.isNotBlank() && req.senderId == targetFirebaseUid) || (targetSuffix.isNotBlank() && req.senderPhoneSuffix == targetSuffix)) ||
            (req.receiverId == memberId || req.receiverId == canonicalId || (targetFirebaseUid.isNotBlank() && req.receiverId == targetFirebaseUid) || (targetSuffix.isNotBlank() && req.receiverPhoneSuffix == targetSuffix))
        }
        _messageRequests.value = remainingRequests

        // 5. Purge from contactsWhoSavedMe
        val currentSavedMe = _contactsWhoSavedMe.value.toMutableSet()
        currentSavedMe.remove(memberId)
        currentSavedMe.remove(canonicalId)
        if (targetSuffix.isNotBlank()) currentSavedMe.remove(targetSuffix)
        if (targetFirebaseUid.isNotBlank()) currentSavedMe.remove(targetFirebaseUid)
        _contactsWhoSavedMe.value = currentSavedMe
    }

    fun togglePinMember(memberId: String) {
        val currentList = _familyMembers.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == memberId || it.firebaseUid == memberId }
        if (index != -1) {
            val item = currentList[index]
            val newPinned = !item.isPinned
            currentList[index] = item.copy(isPinned = newPinned)
            setFamilyMembersWithDeduplication(currentList)
            saveContactsToPrefs()

            val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
            val currentUid = currentSyncedUserId ?: sessionPrefs.getString("user_uid", null)
            val suffix = PhoneUtils.extractPhoneSuffix(item.phone)
            if (!currentUid.isNullOrBlank() && currentUid != "self" && suffix.isNotBlank()) {
                repositoryScope.launch(Dispatchers.IO) {
                    socialService.togglePinContact(currentUid, suffix, newPinned)
                }
            }
        }
    }

    fun clearDemoContacts() {
        contactPrefs.edit().putBoolean(KEY_DEMO_CLEARED, true).apply()
        val filteredList = _familyMembers.value.filter { it.id !in demoIdsSet && !it.id.startsWith("demo_") }
        setFamilyMembersWithDeduplication(filteredList)
        saveContactsToPrefs()
    }

    fun startRealtimePresenceSync(userId: String, userName: String = "Talkly User", avatarUrl: String? = null) {
        if (userId.isBlank() || userId == "self") return
        presenceJob?.cancel()
        presenceJob = repositoryScope.launch(Dispatchers.IO) {
            try {
                socialService.connectPresence(userId, userName, avatarUrl).collect { onlineUserIds ->
                    withContext(Dispatchers.Main) {
                        val updatedMembers = _familyMembers.value.map { member ->
                            val isOnline = member.firebaseUid in onlineUserIds ||
                                    member.id in onlineUserIds ||
                                    (member.id == userId || member.firebaseUid == userId)
                            member.copy(
                                isOnline = isOnline,
                                lastSeen = if (isOnline) "Online" else member.lastSeen
                            )
                        }
                        setFamilyMembersWithDeduplication(updatedMembers)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Presence sync error: ${e.localizedMessage}")
            }
        }
    }

    fun stopRealtimePresenceSync(userId: String) {
        presenceJob?.cancel()
        presenceJob = null
        repositoryScope.launch(Dispatchers.IO) {
            socialService.disconnectPresence(userId)
        }
    }

    fun syncContactsFromSupabase(currentUserId: String) {
        if (currentUserId.isBlank() || currentUserId == "self") return
        repositoryScope.launch(Dispatchers.IO) {
            try {
                val contactsResult = socialService.loadContacts(currentUserId)
                val contacts = contactsResult.getOrDefault(emptyList())

                if (contacts.isNotEmpty()) {
                    val currentMap = _familyMembers.value.associateBy { it.phone }.toMutableMap()
                    val deletedSet = _deletedContactIds.value

                    val updatedList = contacts.mapNotNull { c ->
                        val suffix = c.contactPhoneSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(c.contactPhone) }
                        if (c.contactPhone in deletedSet || suffix in deletedSet) return@mapNotNull null

                        val existing = currentMap[c.contactPhone]
                        FamilyMember(
                            id = if (!c.contactUserId.isNullOrBlank()) c.contactUserId else "contact_${suffix}",
                            name = c.contactName,
                            relation = c.relation,
                            avatarUrl = existing?.avatarUrl,
                            status = existing?.status ?: "Available on Talkly 💬",
                            phone = c.contactPhone,
                            isOnline = existing?.isOnline ?: false,
                            isTyping = false,
                            lastSeen = existing?.lastSeen ?: "Recently",
                            lastActiveTimestamp = existing?.lastActiveTimestamp ?: 0L,
                            unreadCount = existing?.unreadCount ?: 0,
                            isPinned = c.isPinned,
                            isRegisteredOnTalkly = !c.contactUserId.isNullOrBlank(),
                            firebaseUid = c.contactUserId
                        )
                    }

                    withContext(Dispatchers.Main) {
                        setFamilyMembersWithDeduplication(updatedList)
                        saveContactsToPrefs()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error syncing contacts from Supabase: ${e.localizedMessage}")
            }
        }
    }

    fun setTypingStatus(targetMemberId: String, isTyping: Boolean) {
        val currentList = _familyMembers.value.map { member ->
            if (member.id == targetMemberId || member.firebaseUid == targetMemberId) {
                member.copy(isTyping = isTyping)
            } else {
                member
            }
        }
        _familyMembers.value = currentList
    }

    fun setMemberTyping(memberId: String, isTyping: Boolean) {
        setTypingStatus(memberId, isTyping)
    }

    fun setMemberPresence(
        memberId: String,
        isOnline: Boolean,
        lastSeen: String = if (isOnline) "Online" else com.family.talkly.util.PhoneUtils.formatLastSeenTime(System.currentTimeMillis()),
        lastActiveTimestamp: Long = System.currentTimeMillis()
    ) {
        val effectiveLastSeen = if (!isOnline && lastSeen.equals("Online", ignoreCase = true)) {
            com.family.talkly.util.PhoneUtils.formatLastSeenTime(lastActiveTimestamp)
        } else {
            lastSeen
        }

        val currentList = _familyMembers.value.map { member ->
            if (member.id == memberId || member.firebaseUid == memberId) {
                member.copy(
                    isOnline = isOnline,
                    lastSeen = effectiveLastSeen,
                    lastActiveTimestamp = lastActiveTimestamp,
                    isTyping = if (!isOnline) false else member.isTyping
                )
            } else {
                member
            }
        }
        setFamilyMembersWithDeduplication(currentList)

        if (!isOnline && memberId.isNotBlank() && memberId != "self") {
            repositoryScope.launch(Dispatchers.IO) {
                socialService.updateLastSeenTimestamp(memberId)
            }
        }
    }

    fun toggleMemberPresence(memberId: String) {
        val member = _familyMembers.value.firstOrNull { it.id == memberId } ?: return
        val newOnline = !member.isOnline
        setMemberPresence(memberId, newOnline, if (newOnline) "Online" else "Recently")
    }

    private fun String?.isNull_or_empty_str(s: String?): Boolean = s == null || s.isEmpty()

    fun deleteChatHistory(memberId: String) {
        val canonicalId = getCanonicalMemberId(memberId)
        val targetMember = _familyMembers.value.firstOrNull {
            it.id == memberId || it.id == canonicalId || it.firebaseUid == memberId || it.firebaseUid == canonicalId
        }
        val targetPhone = targetMember?.phone ?: if (memberId.startsWith("+") || memberId.all { it.isDigit() }) memberId else ""
        val targetSuffix = PhoneUtils.extractPhoneSuffix(targetPhone)
        val targetFirebaseUid = targetMember?.firebaseUid ?: ""

        // 1. Collect all messages for media deletion before removing from map
        val messagesToDelete = mutableListOf<ChatMessage>()
        _messagesMap.value[memberId]?.let { messagesToDelete.addAll(it) }
        _messagesMap.value[canonicalId]?.let { messagesToDelete.addAll(it) }
        if (targetFirebaseUid.isNotBlank()) {
            _messagesMap.value[targetFirebaseUid]?.let { messagesToDelete.addAll(it) }
        }
        if (targetSuffix.isNotBlank()) {
            _messagesMap.value[targetSuffix]?.let { messagesToDelete.addAll(it) }
        }

        // 2. Wipe from local messagesMap memory cache and Room Database
        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap.remove(memberId)
        updatedMap.remove(canonicalId)
        if (targetFirebaseUid.isNotBlank()) updatedMap.remove(targetFirebaseUid)
        if (targetSuffix.isNotBlank()) updatedMap.remove(targetSuffix)
        _messagesMap.value = updatedMap

        repositoryScope.launch(Dispatchers.IO) {
            listOfNotNull(memberId.ifBlank { null }, canonicalId.ifBlank { null }, targetFirebaseUid.ifBlank { null }, targetSuffix.ifBlank { null })
                .distinct()
                .forEach { key ->
                    try {
                        database.chatMessageDao().deleteMessagesForChat(key)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error deleting messages from Room for key $key: ${e.localizedMessage}")
                    }
                }
        }

        saveMessagesToDisk()

        // 3. Unpin target member if pinned and update familyMembers
        val updatedMembers = _familyMembers.value.map { m ->
            if (m.id == memberId || m.id == canonicalId ||
                (targetFirebaseUid.isNotBlank() && m.firebaseUid == targetFirebaseUid) ||
                (targetSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == targetSuffix)) {
                m.copy(isPinned = false)
            } else m
        }
        setFamilyMembersWithDeduplication(updatedMembers)
        saveContactsToPrefs()

        // 4. Delete from Supabase messages table
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null) ?: "self"

        repositoryScope.launch(Dispatchers.IO) {
            val resolvedUid = if (targetFirebaseUid.isNotBlank()) targetFirebaseUid else (SupabaseMessagingService.resolveUserUuid(canonicalId) ?: canonicalId)
            val convId = SupabaseMessagingService.getOrCreateConversationId(currentUid, resolvedUid)
            SupabaseMessagingService.deleteChatHistory(currentUid, resolvedUid, convId)
        }
    }

    fun triggerSimulatedTypingReply(memberId: String) {
        // Disabled per requirements: No automated mock replies, bot responses, or local fallback test logic
    }

    private val diskExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun startRoomDatabaseObserver() {
        repositoryScope.launch(Dispatchers.IO) {
            try {
                database.chatMessageDao().getAllMessages().collect { entities ->
                    if (entities.isNotEmpty()) {
                        val loadedMap = mutableMapOf<String, MutableList<ChatMessage>>()
                        for (entity in entities) {
                            val list = loadedMap.getOrPut(entity.chatKey) { mutableListOf() }
                            list.add(entity.toChatMessage())
                        }
                        val resultMap = loadedMap.mapValues { entry -> entry.value.sortedBy { it.timestamp } }
                        if (resultMap.isNotEmpty()) {
                            _messagesMap.value = resultMap
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in Room Database observer: ${e.localizedMessage}")
            }
        }
    }

    private fun saveMessagesToDisk() {
        repositoryScope.launch(Dispatchers.IO) {
            try {
                val currentMap = _messagesMap.value
                val entities = mutableListOf<ChatMessageEntity>()
                val rootObj = org.json.JSONObject()

                currentMap.forEach { (chatKey, msgList) ->
                    val arr = org.json.JSONArray()
                    msgList.forEach { msg ->
                        entities.add(ChatMessageEntity.fromChatMessage(chatKey, msg))
                        val obj = org.json.JSONObject().apply {
                            put("id", msg.id)
                            put("senderId", msg.senderId)
                            put("senderName", msg.senderName)
                            put("receiverId", msg.receiverId)
                            put("messageType", msg.messageType.name)
                            put("textContent", msg.textContent)
                            put("mediaUrl", msg.mediaUrl ?: org.json.JSONObject.NULL)
                            put("timestamp", msg.timestamp)
                            put("callType", msg.callType ?: org.json.JSONObject.NULL)
                            put("callDurationSec", msg.callDurationSec)
                            put("isDelivered", msg.isDelivered)
                            put("isRead", msg.isRead)
                            put("readAtTimestamp", msg.readAtTimestamp ?: org.json.JSONObject.NULL)
                            put("reaction", msg.reaction ?: org.json.JSONObject.NULL)
                            put("isStarred", msg.isStarred)
                            put("isPinned", msg.isPinned)
                            put("replyToMessageId", msg.replyToMessageId ?: org.json.JSONObject.NULL)
                            put("replyToSenderName", msg.replyToSenderName ?: org.json.JSONObject.NULL)
                            put("replyToText", msg.replyToText ?: org.json.JSONObject.NULL)
                            put("isEdited", msg.isEdited)
                            put("isDeletedForEveryone", msg.isDeletedForEveryone)
                            if (msg.deletedForUsers.isNotEmpty()) {
                                val delArr = org.json.JSONArray()
                                msg.deletedForUsers.forEach { delArr.put(it) }
                                put("deletedForUsers", delArr)
                            }
                        }
                        arr.put(obj)
                    }
                    rootObj.put(chatKey, arr)
                }

                // 1. Save to Room Database on IO thread
                database.chatMessageDao().clearAllMessages()
                if (entities.isNotEmpty()) {
                    database.chatMessageDao().insertMessages(entities)
                }

                // 2. Save JSON backup
                val file = java.io.File(context.filesDir, "cached_talkly_messages_v2.json")
                file.writeText(rootObj.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error saving messages to Room/disk: ${e.message}")
            }
        }
    }

    private fun loadMessagesFromDisk() {
        repositoryScope.launch(Dispatchers.IO) {
            try {
                // 1. Try loading from Room Database
                val loadedEntities = database.chatMessageDao().getAllMessagesSync()
                if (loadedEntities.isNotEmpty()) {
                    val loadedMap = mutableMapOf<String, MutableList<ChatMessage>>()
                    for (entity in loadedEntities) {
                        val list = loadedMap.getOrPut(entity.chatKey) { mutableListOf() }
                        list.add(entity.toChatMessage())
                    }
                    val resultMap = loadedMap.mapValues { entry -> entry.value.sortedBy { it.timestamp } }
                    if (resultMap.isNotEmpty()) {
                        _messagesMap.value = resultMap
                        Log.i(TAG, "Loaded ${loadedEntities.size} messages from Room Database for offline support")
                        return@launch
                    }
                }

                // 2. Fallback: Load from JSON file if Room is empty
                val file = java.io.File(context.filesDir, "cached_talkly_messages_v2.json")
                if (!file.exists()) return@launch
                val jsonStr = file.readText()
                if (jsonStr.isBlank()) return@launch
                val rootObj = org.json.JSONObject(jsonStr)
                val loadedMap = mutableMapOf<String, List<ChatMessage>>()
                val keys = rootObj.keys()
                while (keys.hasNext()) {
                    val chatKey = keys.next()
                    val arr = rootObj.getJSONArray(chatKey)
                    val msgList = mutableListOf<ChatMessage>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id", "")
                        if (id.isBlank()) continue
                        val senderId = obj.optString("senderId", "self")
                        val senderName = obj.optString("senderName", "You")
                        val receiverId = obj.optString("receiverId", "")
                        val typeStr = obj.optString("messageType", "TEXT")
                        val type = try { MessageType.valueOf(typeStr) } catch (e: Exception) { MessageType.TEXT }
                        val textContent = obj.optString("textContent", "")
                        val mediaUrl = if (obj.has("mediaUrl") && !obj.isNull("mediaUrl")) obj.getString("mediaUrl") else null
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        val callType = if (obj.has("callType") && !obj.isNull("callType")) obj.getString("callType") else null
                        val callDurationSec = obj.optInt("callDurationSec", 0)
                        val isDelivered = obj.optBoolean("isDelivered", false)
                        val isRead = obj.optBoolean("isRead", false)
                        val readAtTimestamp = if (obj.has("readAtTimestamp") && !obj.isNull("readAtTimestamp")) obj.getLong("readAtTimestamp") else null
                        val reaction = if (obj.has("reaction") && !obj.isNull("reaction")) obj.getString("reaction") else null
                        val isStarred = obj.optBoolean("isStarred", false)
                        val isPinned = obj.optBoolean("isPinned", false)
                        val replyToMessageId = if (obj.has("replyToMessageId") && !obj.isNull("replyToMessageId")) obj.getString("replyToMessageId") else null
                        val replyToSenderName = if (obj.has("replyToSenderName") && !obj.isNull("replyToSenderName")) obj.getString("replyToSenderName") else null
                        val replyToText = if (obj.has("replyToText") && !obj.isNull("replyToText")) obj.getString("replyToText") else null
                        val isEdited = obj.optBoolean("isEdited", false)
                        val isDeletedForEveryone = obj.optBoolean("isDeletedForEveryone", false)
                        val deletedForUsers = mutableListOf<String>()
                        if (obj.has("deletedForUsers")) {
                            val delArr = obj.getJSONArray("deletedForUsers")
                            for (d in 0 until delArr.length()) {
                                deletedForUsers.add(delArr.getString(d))
                            }
                        }

                        msgList.add(
                            ChatMessage(
                                id = id,
                                senderId = senderId,
                                senderName = senderName,
                                receiverId = receiverId,
                                messageType = type,
                                textContent = textContent,
                                mediaUrl = mediaUrl,
                                timestamp = timestamp,
                                callType = callType,
                                callDurationSec = callDurationSec,
                                isDelivered = isDelivered,
                                isRead = isRead,
                                readAtTimestamp = readAtTimestamp,
                                reaction = reaction,
                                isStarred = isStarred,
                                isPinned = isPinned,
                                replyToMessageId = replyToMessageId,
                                replyToSenderName = replyToSenderName,
                                replyToText = replyToText,
                                isEdited = isEdited,
                                isDeletedForEveryone = isDeletedForEveryone,
                                deletedForUsers = deletedForUsers
                            )
                        )
                    }
                    loadedMap[chatKey] = msgList.sortedBy { it.timestamp }
                }
                if (loadedMap.isNotEmpty()) {
                    _messagesMap.value = loadedMap
                    saveMessagesToDisk()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages from disk: ${e.message}")
            }
        }
    }

    private fun autoStartRealtimeSyncIfLoggedIn() {
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = sessionPrefs.getString("user_uid", null) ?: fallbackPrefs.getString("user_uid", null)
        if (!currentUid.isNullOrBlank()) {
            startRealtimeMessageSync(currentUid)
        }
    }

    private fun seedInitialFamilyChats() {
        startRoomDatabaseObserver()
        loadMessagesFromDisk()
        autoStartRealtimeSyncIfLoggedIn()
    }

    fun getChatRoomId(id1: String, id2: String): String {
        val clean1 = id1.trim()
        val clean2 = id2.trim()
        if (clean1.isBlank()) return clean2
        if (clean2.isBlank()) return clean1
        val sorted = listOf(clean1, clean2).sorted()
        return "chat_${sorted[0]}_${sorted[1]}"
    }

    fun getCanonicalMemberId(memberOrUidOrPhone: String): String {
        if (memberOrUidOrPhone.isBlank()) return memberOrUidOrPhone
        val suffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(memberOrUidOrPhone)
        val existing = _familyMembers.value.firstOrNull { member ->
            member.id == memberOrUidOrPhone ||
            member.firebaseUid == memberOrUidOrPhone ||
            member.phone == memberOrUidOrPhone ||
            (suffix.isNotBlank() && com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone) == suffix)
        }
        return existing?.id ?: memberOrUidOrPhone
    }

    fun getMessagesForMember(memberId: String): List<ChatMessage> {
        val canonicalId = getCanonicalMemberId(memberId)
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"
        val currentPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)

        val rawList = _messagesMap.value[canonicalId] ?: _messagesMap.value[memberId] ?: emptyList()
        return rawList.filter { msg ->
            !msg.deletedForUsers.contains("self") &&
            !msg.deletedForUsers.contains(currentUid) &&
            (currentSuffix.isBlank() || !msg.deletedForUsers.contains(currentSuffix))
        }
    }

    fun markMessagesAsRead(memberId: String) {
        val canonicalId = getCanonicalMemberId(memberId)

        // Cancel pending notifications for this conversation
        com.family.talkly.util.TalklyNotificationHelper.cancelNotificationsForChat(context, memberId)
        if (canonicalId != memberId) {
            com.family.talkly.util.TalklyNotificationHelper.cancelNotificationsForChat(context, canonicalId)
        }

        val currentMessages = getMessagesForMember(canonicalId)
        if (currentMessages.isEmpty()) return
        var updatedAny = false

        val currentUid = currentSyncedUserId ?: "self"
        val unreadMsgIds = mutableListOf<String>()

        val updatedMessages = currentMessages.map { msg ->
            if (msg.senderId != "self" && msg.senderId != currentUid && !msg.isRead) {
                updatedAny = true
                val now = System.currentTimeMillis()
                unreadMsgIds.add(msg.id)
                msg.copy(isRead = true, readAtTimestamp = now, isDelivered = true)
            } else {
                msg
            }
        }

        if (updatedAny) {
            val updatedMap = _messagesMap.value.toMutableMap()
            updatedMap[canonicalId] = updatedMessages
            if (canonicalId != memberId) {
                updatedMap[memberId] = updatedMessages
            }
            _messagesMap.value = updatedMap
            saveMessagesToDisk()

            // Update in Supabase and Room
            repositoryScope.launch(Dispatchers.IO) {
                unreadMsgIds.forEach { msgId ->
                    SupabaseMessagingService.markMessageAsRead(msgId)
                    try {
                        database.chatMessageDao().updateMessageReadStatus(msgId, isRead = true, readAt = System.currentTimeMillis())
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating room read status for $msgId: ${e.localizedMessage}")
                    }
                }
            }
        }

        // Reset unread count for member in list
        val member = _familyMembers.value.firstOrNull { it.id == canonicalId || it.id == memberId }
        if (member != null && member.unreadCount > 0) {
            val updatedMembers = _familyMembers.value.map { m ->
                if (m.id == member.id) m.copy(unreadCount = 0) else m
            }
            _familyMembers.value = updatedMembers
        }
    }

    fun toggleMessageReaction(
        memberId: String,
        messageId: String,
        reactionEmoji: String,
        currentUserId: String = "self",
        currentUserName: String = "You",
        currentUserAvatar: String? = null
    ) {
        val canonicalId = getCanonicalMemberId(memberId)
        val rawList = _messagesMap.value[canonicalId] ?: _messagesMap.value[memberId] ?: emptyList()
        if (rawList.isEmpty()) return

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val senderUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"

        val effectiveUid = if (currentUserId != "self" && currentUserId.isNotBlank()) currentUserId else senderUid

        var newReactionValue: String? = null

        val updatedMessages = rawList.map { msg ->
            if (msg.id == messageId) {
                val currentEntries = ReactionUtils.parseReactions(msg.reaction, msg.senderId, msg.senderName, msg.timestamp)
                val userEntry = currentEntries.firstOrNull { 
                    it.userId == effectiveUid || it.userId == "self" || (effectiveUid == "self" && it.userId == senderUid)
                }

                val newEntries = currentEntries.toMutableList()
                if (userEntry != null) {
                    if (userEntry.emoji == reactionEmoji) {
                        // Same emoji -> REMOVE user's reaction (toggle off)
                        newEntries.removeAll { 
                            it.userId == effectiveUid || it.userId == "self" || (effectiveUid == "self" && it.userId == senderUid)
                        }
                    } else {
                        // Different emoji -> UPDATE user's reaction
                        val idx = newEntries.indexOf(userEntry)
                        if (idx >= 0) {
                            newEntries[idx] = userEntry.copy(
                                emoji = reactionEmoji,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                } else {
                    // Add new reaction entry for this user
                    newEntries.add(
                        ReactionEntry(
                            userId = effectiveUid,
                            userName = currentUserName,
                            emoji = reactionEmoji,
                            timestamp = System.currentTimeMillis(),
                            avatarUrl = currentUserAvatar
                        )
                    )
                }

                newReactionValue = ReactionUtils.serializeReactions(newEntries)
                msg.copy(reaction = newReactionValue)
            } else {
                msg
            }
        }

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = updatedMessages
        if (canonicalId != memberId) {
            updatedMap[memberId] = updatedMessages
        }
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        // Sync reaction to Supabase and Room
        repositoryScope.launch(Dispatchers.IO) {
            SupabaseMessagingService.updateMessageReaction(messageId, newReactionValue)
            try {
                database.chatMessageDao().updateMessageReaction(messageId, newReactionValue)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating reaction in Room for $messageId: ${e.localizedMessage}")
            }
        }
    }

    fun deleteMessageForYou(memberId: String, messageId: String) {
        val canonicalId = getCanonicalMemberId(memberId)
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"
        val currentPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)

        val rawList = _messagesMap.value[canonicalId] ?: _messagesMap.value[memberId] ?: emptyList()
        var updatedMsg: ChatMessage? = null

        val updatedList = rawList.filterNot { msg ->
            if (msg.id == messageId) {
                val currentDeleted = msg.deletedForUsers.toMutableList()
                if (!currentDeleted.contains(currentUid)) currentDeleted.add(currentUid)
                if (!currentDeleted.contains("self")) currentDeleted.add("self")
                if (currentSuffix.isNotBlank() && !currentDeleted.contains(currentSuffix)) currentDeleted.add(currentSuffix)
                val newMsg = msg.copy(deletedForUsers = currentDeleted)
                updatedMsg = newMsg
                true
            } else {
                false
            }
        }

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = updatedList
        if (canonicalId != memberId) updatedMap[memberId] = updatedList
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        val finalDeleted = updatedMsg?.deletedForUsers ?: listOf(currentUid)
        repositoryScope.launch(Dispatchers.IO) {
            try {
                database.chatMessageDao().deleteMessageById(messageId)
            } catch (e: Exception) {
                Log.w(TAG, "Error purging message $messageId from local Room DB: ${e.localizedMessage}")
            }
            SupabaseMessagingService.deleteMessageForYou(messageId, finalDeleted)
        }
    }

    fun deleteMessageForEveryone(memberId: String, messageId: String): Boolean {
        val canonicalId = getCanonicalMemberId(memberId)
        val rawList = _messagesMap.value[canonicalId] ?: _messagesMap.value[memberId] ?: emptyList()
        val msg = rawList.firstOrNull { it.id == messageId } ?: return false

        val isWithin10Mins = (System.currentTimeMillis() - msg.timestamp) <= (10 * 60 * 1000L)
        if (!isWithin10Mins) {
            return false
        }

        val updatedList = rawList.map { m ->
            if (m.id == messageId) {
                m.copy(
                    isDeletedForEveryone = true,
                    textContent = "This message was deleted",
                    mediaUrl = null
                )
            } else {
                m
            }
        }

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = updatedList
        if (canonicalId != memberId) updatedMap[memberId] = updatedList
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        // Sync to Supabase and Room
        repositoryScope.launch(Dispatchers.IO) {
            SupabaseMessagingService.deleteMessageForEveryone(messageId)
            try {
                database.chatMessageDao().updateMessageDeletion(messageId, isDeletedForEveryone = true, textContent = "This message was deleted")
            } catch (e: Exception) {
                Log.w(TAG, "Error updating delete for everyone in Room: ${e.localizedMessage}")
            }
        }

        return true
    }

    fun editMessage(memberId: String, messageId: String, newText: String): Boolean {
        val canonicalId = getCanonicalMemberId(memberId)
        val rawList = _messagesMap.value[canonicalId] ?: _messagesMap.value[memberId] ?: emptyList()
        val msg = rawList.firstOrNull { it.id == messageId } ?: return false

        val isWithin10Mins = (System.currentTimeMillis() - msg.timestamp) <= (10 * 60 * 1000L)
        if (!isWithin10Mins) {
            return false
        }

        val updatedList = rawList.map { m ->
            if (m.id == messageId) {
                m.copy(
                    textContent = newText,
                    isEdited = true
                )
            } else {
                m
            }
        }

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = updatedList
        if (canonicalId != memberId) updatedMap[memberId] = updatedList
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        // Sync to Supabase and Room
        repositoryScope.launch(Dispatchers.IO) {
            SupabaseMessagingService.editMessage(messageId, newText)
            try {
                database.chatMessageDao().updateMessageContent(messageId, newText)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating edited message in Room: ${e.localizedMessage}")
            }
        }

        return true
    }

    fun toggleStarMessage(memberId: String, messageId: String) {
        val canonicalId = getCanonicalMemberId(memberId)
        val currentMessages = getMessagesForMember(canonicalId)
        if (currentMessages.isEmpty()) return
        var newStarredValue = false
        val updatedMessages = currentMessages.map { msg ->
            if (msg.id == messageId) {
                val newStarred = !msg.isStarred
                newStarredValue = newStarred
                msg.copy(isStarred = newStarred)
            } else {
                msg
            }
        }
        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = updatedMessages
        if (canonicalId != memberId) {
            updatedMap[memberId] = updatedMessages
        }
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        // Sync to Supabase & Room
        repositoryScope.launch(Dispatchers.IO) {
            SupabaseMessagingService.toggleStarMessage(messageId, newStarredValue)
            try {
                database.chatMessageDao().updateMessageStarred(messageId, newStarredValue)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating starred in Room: ${e.localizedMessage}")
            }
        }
    }

    fun togglePinMessage(memberId: String, messageId: String): Boolean {
        val canonicalId = getCanonicalMemberId(memberId)
        val currentMessages = getMessagesForMember(canonicalId)
        if (currentMessages.isEmpty()) return false

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"

        val targetMsg = currentMessages.firstOrNull { it.id == messageId } ?: return false

        // Permission check for unpinning
        if (targetMsg.isPinned) {
            val canUnpin = targetMsg.pinnedBy.isNullOrBlank() ||
                    targetMsg.pinnedBy == "self" ||
                    targetMsg.pinnedBy == currentUid ||
                    targetMsg.senderId == "self" ||
                    targetMsg.senderId == currentUid
            if (!canUnpin) {
                Log.w(TAG, "User $currentUid does not have permission to unpin message $messageId pinned by ${targetMsg.pinnedBy}")
                return false
            }
        }

        val newPinned = !targetMsg.isPinned
        val newPinnedBy = if (newPinned) currentUid else null

        val updatedMessages = currentMessages.map { msg ->
            if (msg.id == messageId) {
                msg.copy(isPinned = newPinned, pinnedBy = newPinnedBy)
            } else {
                msg
            }
        }

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = updatedMessages
        if (canonicalId != memberId) {
            updatedMap[memberId] = updatedMessages
        }
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        // Sync to Supabase & Room
        repositoryScope.launch(Dispatchers.IO) {
            SupabaseMessagingService.togglePinMessage(messageId, newPinned, newPinnedBy)
            try {
                database.chatMessageDao().updateMessagePinned(messageId, newPinned, newPinnedBy)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating pin in Room: ${e.localizedMessage}")
            }
        }

        return true
    }

    private var isInitialMessageSyncDone = false

    fun forceReconnectListeners(reason: String = "manual") {
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val uid = currentSyncedUserId
            ?: com.family.talkly.data.supabase.SupabaseClientProvider.auth.currentUserOrNull()?.id
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)

        Log.d(TAG, "forceReconnectListeners called (reason: $reason, targetUid: $uid)")
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "forceReconnectListeners: No active user session, skipping reconnect.")
            return
        }

        try {
            currentSyncedUserId = null // Reset so startRealtimeMessageSync bypasses the guard

            startRealtimeMessageSync(uid)
            syncContactsFromSupabase(uid)
            syncStatusesFromSupabase(uid)
            Log.d(TAG, "forceReconnectListeners: Successfully attached message listeners for uid=$uid (trigger: $reason)")
        } catch (e: Exception) {
            Log.e(TAG, "forceReconnectListeners encountered error: ${e.localizedMessage}")
        }
    }

    fun startRealtimeMessageSync(currentUserId: String?) {
        if (currentUserId.isNullOrBlank()) return
        val isChannelActive = supabaseRealtimeChannel != null && supabaseRealtimeChannel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
        if (currentSyncedUserId == currentUserId && isChannelActive) {
            Log.d(TAG, "startRealtimeMessageSync: Channel already active for $currentUserId")
            return
        }

        currentSyncedUserId = currentUserId
        isInitialMessageSyncDone = false

        messageSyncJob?.cancel()
        messageSyncJob = repositoryScope.launch(Dispatchers.IO) {
            // 1. Cleanly unsubscribe and clear any previous channel sequentially
            try {
                if (supabaseRealtimeChannel != null) {
                    SupabaseMessagingService.unsubscribeChannel(supabaseRealtimeChannel)
                    supabaseRealtimeChannel = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning previous realtime channel: ${e.localizedMessage}")
            }

            // 2. Fetch recent messages from Supabase PostgREST to initialize local DB & state
            try {
                val recentSupabaseMessages = SupabaseMessagingService.fetchRecentMessagesForUser(currentUserId, limit = 200)
                if (recentSupabaseMessages.isNotEmpty()) {
                    val currentMap = _messagesMap.value.toMutableMap()
                    recentSupabaseMessages.forEach { sMsg ->
                        val chatMsg = sMsg.toChatMessage(currentUserId)
                        val rawOtherPartyId = if (chatMsg.senderId == "self" || chatMsg.senderId == currentUserId) chatMsg.receiverId else chatMsg.senderId
                        if (rawOtherPartyId.isNotBlank()) {
                            val canonicalOther = getCanonicalMemberId(rawOtherPartyId)
                            val list = (currentMap[canonicalOther] ?: emptyList()).toMutableList()
                            if (list.none { it.id == chatMsg.id }) {
                                list.add(chatMsg)
                                currentMap[canonicalOther] = list.sortedBy { it.timestamp }
                            }
                            try {
                                database.chatMessageDao().insertMessage(ChatMessageEntity.fromChatMessage(canonicalOther, chatMsg))
                            } catch (e: Exception) {}
                        }
                    }
                    _messagesMap.value = currentMap
                    saveMessagesToDisk()
                }
                _lastServerSyncTime.value = System.currentTimeMillis()
                isInitialMessageSyncDone = true
            } catch (e: Exception) {
                Log.w(TAG, "Error performing initial Supabase message fetch: ${e.localizedMessage}")
                isInitialMessageSyncDone = true
            }

            // 3. Connect Supabase Realtime Channel with auto-reconnect listener
            try {
                supabaseRealtimeChannel = SupabaseMessagingService.createMessagingRealtimeChannel(
                    currentUserId = currentUserId,
                    coroutineScope = repositoryScope,
                    onMessageAction = { action ->
                        handleIncomingSupabaseMessageAction(action, currentUserId)
                    },
                    onRequestAction = { action ->
                        handleIncomingSupabaseRequestAction(action, currentUserId)
                    },
                    onStatusChange = { status ->
                        if (status == RealtimeChannel.Status.UNSUBSCRIBED) {
                            Log.w(TAG, "Messaging channel disconnected (status=$status). Reconnecting in 3s...")
                            repositoryScope.launch(Dispatchers.IO) {
                                delay(3000)
                                if (currentSyncedUserId == currentUserId &&
                                    supabaseRealtimeChannel?.status?.value != RealtimeChannel.Status.SUBSCRIBED) {
                                    startRealtimeMessageSync(currentUserId)
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting Supabase Realtime messaging channel: ${e.localizedMessage}")
            }
        }

        setupFirestoreMessageRequestsListener(currentUserId)
    }

    private fun handleIncomingSupabaseMessageAction(action: io.github.jan.supabase.realtime.PostgresAction, currentUserId: String) {
        when (action) {
            is io.github.jan.supabase.realtime.PostgresAction.Insert -> {
                try {
                    val supabaseMsg = SupabaseMessagingService.json.decodeFromJsonElement<SupabaseMessage>(action.record)
                    val chatMsg = supabaseMsg.toChatMessage(currentUserId)
                    handleSingleIncomingChatMessage(chatMsg, currentUserId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding inserted Supabase message: ${e.localizedMessage}")
                }
            }
            is io.github.jan.supabase.realtime.PostgresAction.Update -> {
                try {
                    val supabaseMsg = SupabaseMessagingService.json.decodeFromJsonElement<SupabaseMessage>(action.record)
                    val chatMsg = supabaseMsg.toChatMessage(currentUserId)
                    handleSingleIncomingChatMessage(chatMsg, currentUserId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding updated Supabase message: ${e.localizedMessage}")
                }
            }
            is io.github.jan.supabase.realtime.PostgresAction.Delete -> {
                try {
                    val id = action.oldRecord["id"]?.toString()?.replace("\"", "") ?: return
                    val currentMap = _messagesMap.value.toMutableMap()
                    for ((key, list) in currentMap) {
                        if (list.any { it.id == id }) {
                            currentMap[key] = list.filterNot { it.id == id }
                        }
                    }
                    _messagesMap.value = currentMap
                    saveMessagesToDisk()
                    repositoryScope.launch(Dispatchers.IO) {
                        database.chatMessageDao().deleteMessageById(id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling deleted message: ${e.localizedMessage}")
                }
            }
            else -> {}
        }
    }

    private fun handleIncomingSupabaseRequestAction(action: io.github.jan.supabase.realtime.PostgresAction, currentUserId: String) {
        when (action) {
            is io.github.jan.supabase.realtime.PostgresAction.Insert,
            is io.github.jan.supabase.realtime.PostgresAction.Update -> {
                try {
                    val record = when (action) {
                        is io.github.jan.supabase.realtime.PostgresAction.Insert -> action.record
                        is io.github.jan.supabase.realtime.PostgresAction.Update -> action.record
                        else -> return
                    }
                    val req = SupabaseMessagingService.json.decodeFromJsonElement<SupabaseMessageRequest>(record)
                    val currentList = _messageRequests.value.toMutableList()
                    val idx = currentList.indexOfFirst { it.id == req.id }
                    val item = req.toMessageRequest()
                    if (idx >= 0) {
                        currentList[idx] = item
                    } else {
                        currentList.add(0, item)
                    }
                    _messageRequests.value = currentList
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling incoming request action: ${e.localizedMessage}")
                }
            }
            is io.github.jan.supabase.realtime.PostgresAction.Delete -> {
                try {
                    val id = action.oldRecord["id"]?.toString()?.replace("\"", "") ?: return
                    _messageRequests.value = _messageRequests.value.filterNot { it.id == id }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling deleted request: ${e.localizedMessage}")
                }
            }
            else -> {}
        }
    }

    private fun handleSingleIncomingChatMessage(message: ChatMessage, currentUserId: String) {
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val userPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val userSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(userPhone)

        val isSelf = message.senderId == "self" || message.senderId == currentUserId
        if (!isSelf && !message.isDelivered) {
            repositoryScope.launch(Dispatchers.IO) {
                SupabaseMessagingService.markMessageAsDelivered(message.id)
            }
        }

        val finalDelivered = if (!isSelf) true else message.isDelivered
        val finalMessage = message.copy(isDelivered = finalDelivered)

        val rawOtherPartyId = if (isSelf) message.receiverId else message.senderId
        if (rawOtherPartyId.isBlank()) return

        val canonicalOtherPartyId = getCanonicalMemberId(rawOtherPartyId)
        ensureContactInChatList(canonicalOtherPartyId, fallbackName = message.senderName)

        val currentMap = _messagesMap.value.toMutableMap()
        val existingMsgs = (currentMap[canonicalOtherPartyId] ?: currentMap[rawOtherPartyId] ?: emptyList()).toMutableList()
        val existingIndex = existingMsgs.indexOfFirst { it.id == message.id }
        if (existingIndex >= 0) {
            val existing = existingMsgs[existingIndex]
            val preservedDelivered = existing.isDelivered || finalMessage.isDelivered
            val preservedRead = existing.isRead || finalMessage.isRead
            val preservedReadAt = finalMessage.readAtTimestamp ?: existing.readAtTimestamp
            val preservedDeletedForUsers = (finalMessage.deletedForUsers + existing.deletedForUsers).distinct()
            existingMsgs[existingIndex] = finalMessage.copy(
                isDelivered = preservedDelivered,
                isRead = preservedRead,
                readAtTimestamp = preservedReadAt,
                deletedForUsers = preservedDeletedForUsers,
                isPending = false
            )
        } else {
            existingMsgs.add(finalMessage)
        }

        // Deduplication & notification tracking
        if (isSelf || finalMessage.isRead) {
            com.family.talkly.util.TalklyNotificationHelper.markMessageProcessed(context, message.id)
        } else if (canonicalOtherPartyId == com.family.talkly.util.TalklyNotificationHelper.activeChatMemberId) {
            com.family.talkly.util.TalklyNotificationHelper.markMessageProcessed(context, message.id)
        } else if (!com.family.talkly.util.TalklyNotificationHelper.isMessageProcessed(context, message.id)) {
            val displayContent = when (finalMessage.messageType) {
                MessageType.TEXT -> finalMessage.textContent
                MessageType.IMAGE -> "📷 Photo"
                MessageType.VIDEO -> "📹 Video"
                MessageType.VOICE_NOTE -> "🎵 Voice message"
                MessageType.CALL_LOG -> "📞 Call"
            }
            com.family.talkly.util.TalklyNotificationHelper.postIncomingMessageNotification(
                context = context,
                senderName = if (finalMessage.senderName.isNotBlank() && finalMessage.senderName != "Talkly User") finalMessage.senderName else "New Message",
                messageText = displayContent,
                chatMemberId = canonicalOtherPartyId,
                messageId = finalMessage.id
            )
        }

        val filteredMsgs = existingMsgs.filterNot { msg ->
            (currentUserId.isNotBlank() && msg.deletedForUsers.contains(currentUserId)) ||
            msg.deletedForUsers.contains("self") ||
            (userSuffix.isNotBlank() && msg.deletedForUsers.contains(userSuffix))
        }.sortedBy { it.timestamp }
        currentMap[canonicalOtherPartyId] = filteredMsgs
        if (canonicalOtherPartyId != rawOtherPartyId && currentMap.containsKey(rawOtherPartyId)) {
            currentMap.remove(rawOtherPartyId)
        }

        _messagesMap.value = currentMap
        saveMessagesToDisk()
        _lastServerSyncTime.value = System.currentTimeMillis()

        // Persist to Room
        repositoryScope.launch(Dispatchers.IO) {
            try {
                database.chatMessageDao().insertMessage(
                    ChatMessageEntity.fromChatMessage(canonicalOtherPartyId, finalMessage)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error inserting message to Room: ${e.localizedMessage}")
            }
        }
    }

    fun invalidateLocalCacheAndSyncPrimaryProfile(primaryUid: String) {
        if (primaryUid.isBlank()) return

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val userPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val userSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(userPhone)

        // Purge local contacts cache entries associated with self/duplicate UIDs
        val currentList = _familyMembers.value.toMutableList()
        val filteredList = currentList.filter { member ->
            val mSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
            val isSelfUid = (member.id == primaryUid || member.firebaseUid == primaryUid)
            val isSelfPhone = (userPhone.isNotBlank() && member.phone == userPhone)
            val isSelfSuffix = (userSuffix.isNotBlank() && mSuffix.isNotBlank() && mSuffix == userSuffix)
            !(isSelfUid || isSelfPhone || isSelfSuffix)
        }
        setFamilyMembersWithDeduplication(filteredList)
        saveContactsToPrefs()

        // Purge messages map entry for self key
        val currentMap = _messagesMap.value.toMutableMap()
        currentMap.remove(primaryUid)
        currentMap.remove("self")
        if (userSuffix.isNotBlank()) currentMap.remove(userSuffix)
        _messagesMap.value = currentMap
        saveMessagesToDisk()

        // Clear Coil memory and disk caches to purge stale avatars
        try {
            val imageLoader = coil.Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing Coil image cache in invalidateLocalCacheAndSyncPrimaryProfile: ${e.localizedMessage}")
        }

        // Merge & normalize local statuses for self profile
        val updatedStatuses = _statuses.value.filter { !it.isExpired(_simulatedTimeOffsetMs.value) }.map { item ->
            val isSelf = item.userId == "self" ||
                    item.userId == primaryUid ||
                    (userPhone.isNotBlank() && item.userId == userPhone) ||
                    (userSuffix.isNotBlank() && com.family.talkly.util.PhoneUtils.extractPhoneSuffix(item.userId) == userSuffix)
            if (isSelf) {
                item.copy(userId = primaryUid)
            } else {
                item
            }
        }
        _statuses.value = updatedStatuses
        saveStatusesToPrefs()

        // Restart realtime message listener, status sync, and contact sync
        currentSyncedUserId = null
        startRealtimeMessageSync(primaryUid)
        syncContactsFromSupabase(primaryUid)
        syncStatusesFromSupabase(primaryUid)
    }

    fun resetSessionOnLogout() {
        try {
            repositoryScope.launch(Dispatchers.IO) {
                SupabaseMessagingService.unsubscribeChannel(supabaseRealtimeChannel)
                supabaseRealtimeChannel = null
            }

            currentSyncedUserId = null
            _messagesMap.value = emptyMap()
            _familyMembers.value = emptyList()
            _statuses.value = emptyList()
            _messageRequests.value = emptyList()
            _contactsWhoSavedMe.value = emptySet()
            _blockedUserIds.value = emptySet()
            _deletedContactIds.value = emptySet()

            contactPrefs.edit().clear().apply()

            repositoryScope.launch(Dispatchers.IO) {
                try {
                    database.chatMessageDao().clearAllMessages()
                } catch (e: Exception) {
                    Log.w(TAG, "Error clearing Room DB on logout: ${e.localizedMessage}")
                }
            }

            try {
                val file = java.io.File(context.filesDir, "cached_talkly_messages_v2.json")
                if (file.exists()) file.delete()
            } catch (e: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting session on logout: ${e.localizedMessage}")
        }
    }

    fun ensureContactInChatList(memberOrUidOrPhone: String, fallbackName: String? = null) {
        if (memberOrUidOrPhone.isBlank() || memberOrUidOrPhone == "self") return
        val suffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(memberOrUidOrPhone)
        val canonicalId = getCanonicalMemberId(memberOrUidOrPhone)
        val deletedSet = _deletedContactIds.value

        // Do NOT re-add contact if explicitly deleted by user
        if (memberOrUidOrPhone in deletedSet || canonicalId in deletedSet || (suffix.isNotBlank() && suffix in deletedSet)) {
            return
        }

        val validFallback = if (!fallbackName.isNullOrBlank() && fallbackName != "Talkly User" && fallbackName != "You") fallbackName else null

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
        val currentPhone = sessionPrefs.getString("user_phone", null)
            ?: fallbackPrefs.getString("user_phone", null) ?: ""
        val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)

        // Exclude self explicitly
        if ((!currentUid.isNullOrBlank() && memberOrUidOrPhone == currentUid) ||
            (currentPhone.isNotBlank() && memberOrUidOrPhone == currentPhone) ||
            (currentSuffix.isNotBlank() && suffix.isNotBlank() && suffix == currentSuffix)) {
            return
        }

        val existingIndex = _familyMembers.value.indexOfFirst { member ->
            member.id == memberOrUidOrPhone ||
            member.firebaseUid == memberOrUidOrPhone ||
            member.phone == memberOrUidOrPhone ||
            (suffix.isNotBlank() && com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone) == suffix)
        }

        if (existingIndex >= 0) {
            val existing = _familyMembers.value[existingIndex]
            if (existing.name == "Talkly User" && validFallback != null) {
                val updatedList = _familyMembers.value.toMutableList()
                updatedList[existingIndex] = existing.copy(name = validFallback)
                setFamilyMembersWithDeduplication(updatedList)
                saveContactsToPrefs()
            }
            return
        }

        repositoryScope.launch(Dispatchers.IO) {
            try {
                var profile: SupabaseProfile? = null
                if (memberOrUidOrPhone.length >= 30) {
                    profile = try {
                        SupabaseClientProvider.postgrest.from("profiles")
                            .select { filter { eq("id", memberOrUidOrPhone) } }
                            .decodeSingleOrNull<SupabaseProfile>()
                    } catch (e: Exception) { null }
                }
                if (profile == null && suffix.isNotBlank()) {
                    val res = socialService.searchUserByPhone(suffix)
                    profile = res.getOrNull()
                }
                if (profile == null && (memberOrUidOrPhone.startsWith("+") || memberOrUidOrPhone.all { it.isDigit() })) {
                    val res = socialService.searchUserByPhone(memberOrUidOrPhone)
                    profile = res.getOrNull()
                }

                withContext(Dispatchers.Main) {
                    if (profile != null && profile.name.isNotBlank()) {
                        val uid = profile.id
                        val name = profile.name.ifBlank { validFallback ?: memberOrUidOrPhone }
                        val phone = profile.phone
                        val pic = profile.avatarUrl
                        val bio = profile.bio.ifBlank { "Available on Talkly 💬" }

                        val newMember = FamilyMember(
                            id = uid,
                            name = name,
                            relation = "Contact",
                            avatarUrl = pic,
                            status = bio,
                            phone = phone,
                            isOnline = true,
                            isRegisteredOnTalkly = true,
                            firebaseUid = uid
                        )

                        val currentList = _familyMembers.value.toMutableList()
                        if (currentList.none { it.id == uid || it.firebaseUid == uid }) {
                            currentList.add(0, newMember)
                            setFamilyMembersWithDeduplication(currentList)
                            saveContactsToPrefs()
                        }
                    } else {
                        createFallbackContact(memberOrUidOrPhone, validFallback)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ensureContactInChatList error: ${e.localizedMessage}")
                withContext(Dispatchers.Main) {
                    createFallbackContact(memberOrUidOrPhone, validFallback)
                }
            }
        }
    }

    private fun createFallbackContact(memberOrUidOrPhone: String, validFallbackName: String?) {
        val displayName = validFallbackName
            ?: if (memberOrUidOrPhone.startsWith("+") || memberOrUidOrPhone.all { it.isDigit() }) memberOrUidOrPhone
            else if (memberOrUidOrPhone.length > 6) "User " + memberOrUidOrPhone.takeLast(4)
            else memberOrUidOrPhone

        val fallbackMember = FamilyMember(
            id = memberOrUidOrPhone,
            name = displayName,
            relation = "Contact",
            phone = memberOrUidOrPhone,
            isOnline = true,
            isRegisteredOnTalkly = true,
            firebaseUid = if (!memberOrUidOrPhone.startsWith("contact_") && !memberOrUidOrPhone.contains(" ")) memberOrUidOrPhone else null
        )
        val currentList = _familyMembers.value.toMutableList()
        if (currentList.none { it.id == memberOrUidOrPhone }) {
            currentList.add(0, fallbackMember)
            setFamilyMembersWithDeduplication(currentList)
            saveContactsToPrefs()
        }
    }

    private fun updateMessagePendingState(messageId: String, isPending: Boolean) {
        val currentMap = _messagesMap.value.toMutableMap()
        var modified = false
        currentMap.forEach { (key, msgs) ->
            val idx = msgs.indexOfFirst { it.id == messageId }
            if (idx >= 0 && msgs[idx].isPending != isPending) {
                val list = msgs.toMutableList()
                list[idx] = list[idx].copy(isPending = isPending)
                currentMap[key] = list
                modified = true
            }
        }
        if (modified) {
            _messagesMap.value = currentMap
            saveMessagesToDisk()
        }
    }

    fun sendMessage(
        memberId: String,
        textContent: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        forcedTimestamp: Long = System.currentTimeMillis(),
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToText: String? = null,
        explicitSenderUid: String? = null,
        explicitMessageId: String? = null
    ) {
        val canonicalId = getCanonicalMemberId(memberId)

        val targetMember = _familyMembers.value.firstOrNull {
            it.id == canonicalId || it.id == memberId || it.firebaseUid == canonicalId || it.firebaseUid == memberId
        }
        val targetPhone = targetMember?.phone ?: if (memberId.startsWith("+") || memberId.all { it.isDigit() }) memberId else ""
        val targetSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(targetPhone)

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)

        val senderUid = explicitSenderUid?.takeIf { it.isNotBlank() }
            ?: currentSyncedUserId
            ?: com.family.talkly.data.supabase.SupabaseClientProvider.client.auth.currentUserOrNull()?.id
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"

        val senderName = sessionPrefs.getString("user_name", null)
            ?: fallbackPrefs.getString("user_name", null)
            ?: "Talkly User"

        ensureContactInChatList(canonicalId, fallbackName = targetMember?.name)

        val isOnline = _isNetworkConnected.value

        val newMessage = ChatMessage(
            id = explicitMessageId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
            senderId = senderUid,
            senderName = senderName,
            receiverId = canonicalId,
            messageType = type,
            textContent = textContent,
            mediaUrl = mediaUrl,
            timestamp = forcedTimestamp,
            isDelivered = false,
            isRead = false,
            isPending = !isOnline,
            replyToMessageId = replyToMessageId,
            replyToSenderName = replyToSenderName,
            replyToText = replyToText
        )

        val currentList = (getMessagesForMember(canonicalId)).toMutableList()
        currentList.add(newMessage)

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = currentList
        if (canonicalId != memberId && updatedMap.containsKey(memberId)) {
            updatedMap.remove(memberId)
        }
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        // Insert into local Room DB immediately
        repositoryScope.launch(Dispatchers.IO) {
            try {
                database.chatMessageDao().insertMessage(
                    ChatMessageEntity.fromChatMessage(canonicalId, newMessage)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error inserting local message to Room: ${e.localizedMessage}")
            }
        }

        // Send via Supabase Postgrest
        repositoryScope.launch(Dispatchers.IO) {
            try {
                val resolvedSenderUuid = SupabaseMessagingService.resolveUserUuid(senderUid)
                    ?: com.family.talkly.data.supabase.SupabaseClientProvider.client.auth.currentUserOrNull()?.id
                    ?: sessionPrefs.getString("user_uid", null)
                    ?: fallbackPrefs.getString("user_uid", null)
                    ?: ""

                val targetLookupKey = when {
                    !targetMember?.firebaseUid.isNullOrBlank() -> targetMember!!.firebaseUid!!
                    targetPhone.isNotBlank() -> targetPhone
                    else -> canonicalId
                }
                val resolvedReceiverUuid = SupabaseMessagingService.resolveUserUuid(targetLookupKey)
                    ?: SupabaseMessagingService.resolveUserUuid(memberId)
                    ?: SupabaseMessagingService.resolveUserUuid(canonicalId)
                    ?: ""

                if (resolvedSenderUuid.isBlank() || resolvedReceiverUuid.isBlank()) {
                    Log.w(TAG, "Cannot send message to Supabase: invalid UUID (sender='$resolvedSenderUuid', receiver='$resolvedReceiverUuid')")
                    return@launch
                }

                val conversationId = SupabaseMessagingService.getOrCreateConversationId(resolvedSenderUuid, resolvedReceiverUuid)

                val supabaseMessage = newMessage.toSupabaseMessage(
                    conversationId = conversationId,
                    resolvedSenderId = resolvedSenderUuid,
                    resolvedReceiverId = resolvedReceiverUuid
                )

                Log.e(TAG, "DEBUG_BEFORE_SENDMSG: resolvedSenderUuid='$resolvedSenderUuid', resolvedReceiverUuid='$resolvedReceiverUuid', conversationId='$conversationId', canonicalId='$canonicalId', targetLookupKey='$targetLookupKey', memberId='$memberId'")

                val sendSuccess = SupabaseMessagingService.sendMessage(supabaseMessage)
                if (sendSuccess) {
                    updateMessagePendingState(newMessage.id, false)
                    try {
                        database.chatMessageDao().updatePendingStatus(newMessage.id, false)
                    } catch (e: Exception) {}
                } else {
                    Log.e(TAG, "DEBUG_SENDMSG_FAILED sender=$resolvedSenderUuid receiver=$resolvedReceiverUuid convId=$conversationId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "DEBUG_SENDMSG_EXCEPTION message=${e.message} localizedMessage=${e.localizedMessage} class=${e.javaClass.simpleName}", e)
            }
        }

        // Send high priority FCM push notification to recipient
        val previewText = when (type) {
            MessageType.TEXT -> textContent
            MessageType.IMAGE -> "📷 Photo"
            MessageType.VIDEO -> "📹 Video"
            MessageType.VOICE_NOTE -> "🎵 Voice message"
            MessageType.CALL_LOG -> "📞 Call"
        }
        val fcmPayload = mapOf(
            "type" to "CHAT_MESSAGE",
            "senderName" to senderName,
            "messageText" to previewText,
            "senderUid" to (senderUid ?: ""),
            "chatMemberId" to canonicalId
        )
        val resolvedTargetUid = targetMember?.firebaseUid ?: if (!canonicalId.startsWith("contact_") && !canonicalId.contains(" ")) canonicalId else ""
        com.family.talkly.util.FcmTokenManager.sendHighPriorityPush(
            targetUid = resolvedTargetUid,
            targetPhoneSuffix = targetSuffix,
            dataPayload = fcmPayload
        )
    }

    fun toggle48HourFastForward() {
        if (_simulatedTimeOffsetMs.value == 0L) {
            // Fast forward 50 hours into future
            _simulatedTimeOffsetMs.value = 50 * 60 * 60 * 1000L
        } else {
            // Reset to real time
            _simulatedTimeOffsetMs.value = 0L
        }
    }

    fun addExpiredMediaDemo(memberId: String) {
        val fiftyHoursAgo = System.currentTimeMillis() - (50 * 60 * 60 * 1000L)
        sendMessage(
            memberId = memberId,
            textContent = "Demo photo uploaded 50 hours ago",
            type = MessageType.IMAGE,
            mediaUrl = "https://images.unsplash.com/photo-1511895426328-dc8714191300?w=600&auto=format&fit=crop&q=80",
            forcedTimestamp = fiftyHoursAgo
        )
    }

    // --- 24-HOUR DISAPPEARING STATUS METHODS ---

    private fun loadStatuses() {
        val savedStatusesJson = contactPrefs.getString(KEY_STATUSES_JSON, null)
        val loadedList = mutableListOf<StatusItem>()

        if (!savedStatusesJson.isNullOrBlank()) {
            try {
                val jsonArray = org.json.JSONArray(savedStatusesJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    val viewers = mutableListOf<StatusViewer>()
                    if (obj.has("viewers") && !obj.isNull("viewers")) {
                        val vArray = obj.getJSONArray("viewers")
                        for (j in 0 until vArray.length()) {
                            val vObj = vArray.getJSONObject(j)
                            viewers.add(
                                StatusViewer(
                                    userId = vObj.getString("userId"),
                                    userName = vObj.getString("userName"),
                                    userAvatarUrl = if (vObj.has("userAvatarUrl") && !vObj.isNull("userAvatarUrl")) vObj.getString("userAvatarUrl") else null,
                                    timeAgo = vObj.optString("timeAgo", "Recently")
                                )
                            )
                        }
                    }

                    val likes = mutableListOf<StatusLiker>()
                    if (obj.has("likes") && !obj.isNull("likes")) {
                        val lArray = obj.getJSONArray("likes")
                        for (j in 0 until lArray.length()) {
                            val lObj = lArray.getJSONObject(j)
                            likes.add(
                                StatusLiker(
                                    userId = lObj.getString("userId"),
                                    userName = lObj.getString("userName"),
                                    userAvatarUrl = if (lObj.has("userAvatarUrl") && !lObj.isNull("userAvatarUrl")) lObj.getString("userAvatarUrl") else null
                                )
                            )
                        }
                    }

                    val status = StatusItem(
                        id = obj.getString("id"),
                        userId = obj.getString("userId"),
                        userName = obj.getString("userName"),
                        userAvatarUrl = if (obj.has("userAvatarUrl") && !obj.isNull("userAvatarUrl")) obj.getString("userAvatarUrl") else null,
                        textContent = if (obj.has("textContent") && !obj.isNull("textContent")) obj.getString("textContent") else null,
                        photoUrl = if (obj.has("photoUrl") && !obj.isNull("photoUrl")) obj.getString("photoUrl") else null,
                        backgroundColorHex = obj.optString("backgroundColorHex", "#321C3B"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isSeen = obj.optBoolean("isSeen", false),
                        viewers = viewers,
                        likes = likes
                    )
                    if (!status.isExpired(_simulatedTimeOffsetMs.value) && status.userId !in demoIdsSet) {
                        loadedList.add(status)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved statuses: ${e.message}")
            }
        }

        _statuses.value = loadedList
        saveStatusesToPrefs()
    }

    private fun saveStatusesToPrefs() {
        try {
            val jsonArray = org.json.JSONArray()
            _statuses.value.forEach { status ->
                val viewersArray = org.json.JSONArray()
                status.viewers.forEach { v ->
                    val vObj = org.json.JSONObject().apply {
                        put("userId", v.userId)
                        put("userName", v.userName)
                        put("userAvatarUrl", v.userAvatarUrl)
                        put("timeAgo", v.timeAgo)
                    }
                    viewersArray.put(vObj)
                }

                val likesArray = org.json.JSONArray()
                status.likes.forEach { l ->
                    val lObj = org.json.JSONObject().apply {
                        put("userId", l.userId)
                        put("userName", l.userName)
                        put("userAvatarUrl", l.userAvatarUrl)
                    }
                    likesArray.put(lObj)
                }

                val obj = org.json.JSONObject().apply {
                    put("id", status.id)
                    put("userId", status.userId)
                    put("userName", status.userName)
                    put("userAvatarUrl", status.userAvatarUrl)
                    put("textContent", status.textContent)
                    put("photoUrl", status.photoUrl)
                    put("backgroundColorHex", status.backgroundColorHex)
                    put("timestamp", status.timestamp)
                    put("isSeen", status.isSeen)
                    put("viewers", viewersArray)
                    put("likes", likesArray)
                }
                jsonArray.put(obj)
            }
            contactPrefs.edit().putString(KEY_STATUSES_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving statuses to prefs: ${e.message}")
        }
    }

    fun postStatus(
        userId: String = "self",
        userName: String = "You",
        userAvatarUrl: String? = null,
        textContent: String? = null,
        photoUrl: String? = null,
        backgroundColorHex: String = "#321C3B"
    ) {
        val statusId = "status_${System.currentTimeMillis()}"
        var persistentPhotoUrl = photoUrl
        var localPhotoFile: File? = null

        if (!photoUrl.isNullOrBlank() && (photoUrl.startsWith("content://") || (photoUrl.startsWith("file://") && !photoUrl.contains("status_photos")))) {
            try {
                val statusDir = File(context.filesDir, "status_photos").apply { mkdirs() }
                val destFile = File(statusDir, "${statusId}.jpg")
                val uri = Uri.parse(photoUrl)

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri), null, options)

                    val maxDim = maxOf(options.outWidth, options.outHeight)
                    var sampleSize = 1
                    while (maxDim / sampleSize > 1080) { sampleSize *= 2 }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                    inputStream.close()

                    if (bitmap != null) {
                        val outStream = FileOutputStream(destFile)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
                        outStream.flush()
                        outStream.close()
                        bitmap.recycle()

                        localPhotoFile = destFile
                        persistentPhotoUrl = Uri.fromFile(destFile).toString()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error storing local status image: ${e.localizedMessage}")
            }
        }

        val resolvedUserId = if (userId != "self") userId else (currentSyncedUserId ?: "self")
        val now = System.currentTimeMillis()
        val expiresAt = now + (24 * 60 * 60 * 1000L)

        val newStatus = StatusItem(
            id = statusId,
            userId = resolvedUserId,
            userName = userName,
            userAvatarUrl = userAvatarUrl,
            textContent = textContent,
            photoUrl = persistentPhotoUrl,
            backgroundColorHex = backgroundColorHex,
            timestamp = now,
            isSeen = true,
            viewers = emptyList(),
            likes = emptyList()
        )

        val currentList = _statuses.value.toMutableList()
        currentList.add(0, newStatus)
        _statuses.value = currentList
        saveStatusesToPrefs()

        // Sync status to Supabase
        if (resolvedUserId.isNotBlank() && resolvedUserId != "self") {
            repositoryScope.launch(Dispatchers.IO) {
                var finalMediaUrl = persistentPhotoUrl
                val targetUploadFile = localPhotoFile ?: if (!persistentPhotoUrl.isNullOrBlank() && persistentPhotoUrl.startsWith("file://")) {
                    try { File(Uri.parse(persistentPhotoUrl).path ?: "") } catch (e: Exception) { null }
                } else null

                if (targetUploadFile != null && targetUploadFile.exists()) {
                    try {
                        val uploader = MediaCompressorAndUploader(context)
                        val remotePath = "status_photos/${newStatus.id}.jpg"
                        val downloadUrl = uploader.uploadMediaFile(targetUploadFile, remotePath) { _, _ -> }
                        if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                            finalMediaUrl = downloadUrl
                            withContext(Dispatchers.Main) {
                                val updatedList = _statuses.value.map { item ->
                                    if (item.id == newStatus.id) item.copy(photoUrl = downloadUrl) else item
                                }
                                _statuses.value = updatedList
                                saveStatusesToPrefs()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Status photo Cloudinary upload error: ${e.localizedMessage}")
                    }
                }

                val supabaseStatus = SupabaseStatus(
                    id = statusId,
                    userId = resolvedUserId,
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    textContent = textContent,
                    mediaUrl = finalMediaUrl,
                    isVideo = newStatus.isVideo,
                    backgroundColor = backgroundColorHex,
                    createdAt = SupabaseMessage.millisToIsoTimestamp(now),
                    expiresAt = SupabaseMessage.millisToIsoTimestamp(expiresAt)
                )
                socialService.postStatus(supabaseStatus)
            }
        }
    }

    fun syncStatusesFromSupabase(currentUserId: String) {
        if (currentUserId.isBlank() || currentUserId == "self") return
        repositoryScope.launch(Dispatchers.IO) {
            try {
                val result = socialService.loadActiveStatuses()
                val activeItems = result.getOrDefault(emptyList())

                if (activeItems.isNotEmpty()) {
                    val myViewedSet = activeItems.map { status ->
                        val isSelf = status.userId == currentUserId || status.userId == "self"
                        val seen = isSelf || status.viewers.any { it.userId == currentUserId }
                        status.copy(isSeen = seen)
                    }

                    withContext(Dispatchers.Main) {
                        _statuses.value = myViewedSet
                        saveStatusesToPrefs()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error syncing statuses from Supabase: ${e.localizedMessage}")
            }
        }
    }

    fun toggleStatusLike(
        statusId: String,
        currentUserId: String = "self",
        currentUserName: String = "You",
        currentUserAvatar: String? = null
    ) {
        val realCurrentUid = if (currentUserId != "self") currentUserId else (currentSyncedUserId ?: "self")
        val updated = _statuses.value.map { status ->
            if (status.id == statusId) {
                val existingLike = status.likes.firstOrNull { it.userId == realCurrentUid }
                val newLikes = if (existingLike != null) {
                    status.likes.filter { it.userId != realCurrentUid }
                } else {
                    status.likes + StatusLiker(realCurrentUid, currentUserName, currentUserAvatar)
                }

                if (realCurrentUid.isNotBlank() && realCurrentUid != "self") {
                    repositoryScope.launch(Dispatchers.IO) {
                        socialService.toggleStatusLike(statusId, realCurrentUid, currentUserName, currentUserAvatar)
                    }
                }

                status.copy(likes = newLikes)
            } else {
                status
            }
        }
        _statuses.value = updated
        saveStatusesToPrefs()
    }

    fun markStatusAsSeen(
        statusId: String,
        currentUserId: String = "self",
        currentUserName: String = "You",
        currentUserAvatar: String? = null
    ) {
        val realCurrentUid = if (currentUserId != "self") currentUserId else (currentSyncedUserId ?: "self")
        val updated = _statuses.value.map { status ->
            if (status.id == statusId) {
                var newViewers = status.viewers
                val isSelfStatus = status.userId == "self" || status.userId == realCurrentUid || (currentSyncedUserId != null && status.userId == currentSyncedUserId)
                if (!isSelfStatus && status.viewers.none { it.userId == realCurrentUid || it.userId == "self" }) {
                    val newViewer = StatusViewer(
                        userId = realCurrentUid,
                        userName = currentUserName,
                        userAvatarUrl = currentUserAvatar,
                        timeAgo = "Just now"
                    )
                    newViewers = status.viewers + newViewer

                    if (realCurrentUid.isNotBlank() && realCurrentUid != "self") {
                        repositoryScope.launch(Dispatchers.IO) {
                            socialService.markStatusViewed(statusId, realCurrentUid, currentUserName, currentUserAvatar)
                        }
                    }
                }
                status.copy(isSeen = true, viewers = newViewers)
            } else {
                status
            }
        }
        _statuses.value = updated
        saveStatusesToPrefs()
    }

    fun deleteStatus(statusId: String, currentUserId: String = "self") {
        val realCurrentUid = if (currentUserId != "self") currentUserId else (currentSyncedUserId ?: "self")
        val updated = _statuses.value.filterNot { it.id == statusId }
        _statuses.value = updated
        saveStatusesToPrefs()

        if (realCurrentUid.isNotBlank() && realCurrentUid != "self") {
            repositoryScope.launch(Dispatchers.IO) {
                socialService.deleteStatus(statusId, realCurrentUid)
            }
        }
    }

    // --- MESSAGE REQUEST & STRICT MUTUAL CONTACT PRIVACY METHODS ---

    fun setupFirestoreMessageRequestsListener(currentUid: String) {
        if (currentUid.isBlank()) return
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val userPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val userSuffix = PhoneUtils.extractPhoneSuffix(userPhone)

        repositoryScope.launch(Dispatchers.IO) {
            try {
                val requests = SupabaseMessagingService.fetchMessageRequests(currentUid)
                val list = mutableListOf<MessageRequest>()
                for (sReq in requests) {
                    val req = sReq.toMessageRequest()
                    val isForMe = req.receiverId == currentUid ||
                            (req.receiverPhoneSuffix.isNotBlank() && req.receiverPhoneSuffix == userSuffix) ||
                            (userPhone.isNotBlank() && req.receiverPhone == userPhone)

                    val isByMe = req.senderId == currentUid ||
                            (req.senderPhoneSuffix.isNotBlank() && req.senderPhoneSuffix == userSuffix) ||
                            (userPhone.isNotBlank() && req.senderPhone == userPhone)

                    if (isForMe || isByMe) {
                        list.add(req)
                        if (req.status == "ACCEPTED") {
                            val partnerUid = if (isByMe) req.receiverId else req.senderId
                            val partnerPhone = if (isByMe) req.receiverPhone else req.senderPhone
                            val partnerSuffix = if (isByMe) req.receiverPhoneSuffix else req.senderPhoneSuffix
                            val partnerName = if (isByMe) req.receiverName else req.senderName
                            val partnerAvatar = if (isByMe) "" else req.senderAvatar
                            val partnerContactId = if (partnerUid.isNotBlank() && partnerUid != "self" && !partnerUid.startsWith("contact_")) partnerUid else "contact_${partnerSuffix.ifBlank { partnerPhone.replace("+", "") }}"

                            val currentList = _familyMembers.value.toMutableList()
                            val existingIdx = currentList.indexOfFirst { m ->
                                m.id == partnerContactId ||
                                (partnerUid.isNotBlank() && (m.id == partnerUid || m.firebaseUid == partnerUid)) ||
                                (partnerSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == partnerSuffix)
                            }
                            if (existingIdx != -1) {
                                val existing = currentList[existingIdx]
                                currentList[existingIdx] = existing.copy(
                                    isRegisteredOnTalkly = true,
                                    phone = if (existing.phone.isBlank()) partnerPhone else existing.phone,
                                    avatarUrl = existing.avatarUrl ?: partnerAvatar.ifBlank { null },
                                    firebaseUid = existing.firebaseUid ?: (if (partnerUid.isNotBlank() && !partnerUid.startsWith("contact_")) partnerUid else null)
                                )
                            } else {
                                currentList.add(
                                    0,
                                    FamilyMember(
                                        id = partnerContactId,
                                        name = partnerName.ifBlank { "Talkly User" },
                                        relation = "Contact",
                                        avatarUrl = partnerAvatar.ifBlank { null },
                                        status = "Available on Talkly 💬",
                                        phone = partnerPhone,
                                        isOnline = true,
                                        isTyping = false,
                                        lastSeen = "Online",
                                        unreadCount = 0,
                                        isPinned = false,
                                        isRegisteredOnTalkly = true,
                                        firebaseUid = if (partnerUid.isNotBlank() && !partnerUid.startsWith("contact_")) partnerUid else null
                                    )
                                )
                            }
                            setFamilyMembersWithDeduplication(currentList)
                            saveContactsToPrefs()

                            val currentSavedMe = _contactsWhoSavedMe.value.toMutableSet()
                            if (partnerSuffix.isNotBlank()) currentSavedMe.add(partnerSuffix)
                            if (partnerUid.isNotBlank()) currentSavedMe.add(partnerUid)
                            currentSavedMe.add(partnerContactId)
                            _contactsWhoSavedMe.value = currentSavedMe
                        }
                    }
                }
                _messageRequests.value = list
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching Supabase message requests: ${e.localizedMessage}")
            }
        }

        if (userSuffix.isNotBlank()) {
            repositoryScope.launch(Dispatchers.IO) {
                try {
                    val result = socialService.loadContactsWhoSavedMe(userSuffix)
                    val ownerIds = result.getOrDefault(emptyList())
                    if (ownerIds.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val savedMeSet = _contactsWhoSavedMe.value.toMutableSet()
                            savedMeSet.addAll(ownerIds)
                            _contactsWhoSavedMe.value = savedMeSet
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load contacts who saved me from Supabase: ${e.localizedMessage}")
                }
            }
        }
    }

    fun isMutualContact(currentUserUid: String?, targetMember: FamilyMember?): Boolean {
        if (targetMember == null) return false
        val currentUid = currentUserUid ?: currentSyncedUserId ?: "self"

        // 1. Self is always mutual
        if (targetMember.id == "self" || targetMember.id == currentUid || targetMember.firebaseUid == currentUid) {
            return true
        }

        val targetPhone = targetMember.phone
        val targetPhoneSuffix = PhoneUtils.extractPhoneSuffix(targetPhone)
        val targetIdSuffix = PhoneUtils.extractPhoneSuffix(targetMember.id)
        val targetUidSuffix = targetMember.firebaseUid?.let { PhoneUtils.extractPhoneSuffix(it) } ?: ""
        val targetSuffix = when {
            targetPhoneSuffix.isNotBlank() -> targetPhoneSuffix
            targetIdSuffix.isNotBlank() -> targetIdSuffix
            targetUidSuffix.isNotBlank() -> targetUidSuffix
            else -> ""
        }

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val myPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val mySuffix = PhoneUtils.extractPhoneSuffix(myPhone)

        // 2. Demo contacts remain mutual for initial out-of-the-box app functionality
        if (targetMember.id.startsWith("demo_") || targetMember.id in demoIdsSet) {
            return true
        }

        // 3. Check if there is an ACCEPTED message request between current user and target member
        val hasAcceptedRequest = _messageRequests.value.any { req ->
            req.status == "ACCEPTED" && (
                (req.senderId == currentUid && (req.receiverId == targetMember.id || req.receiverId == targetMember.firebaseUid || (targetSuffix.isNotBlank() && req.receiverPhoneSuffix == targetSuffix))) ||
                (req.receiverId == currentUid && (req.senderId == targetMember.id || req.senderId == targetMember.firebaseUid || (targetSuffix.isNotBlank() && req.senderPhoneSuffix == targetSuffix))) ||
                (mySuffix.isNotBlank() && targetSuffix.isNotBlank() && 
                    ((req.senderPhoneSuffix == mySuffix && req.receiverPhoneSuffix == targetSuffix) ||
                     (req.senderPhoneSuffix == targetSuffix && req.receiverPhoneSuffix == mySuffix)))
            )
        }

        if (hasAcceptedRequest) {
            return true
        }

        // 4. Check if both have each other saved in contact book
        val iHaveTargetSaved = _familyMembers.value.any { m ->
            m.id == targetMember.id ||
            (!targetMember.firebaseUid.isNullOrBlank() && (m.id == targetMember.firebaseUid || m.firebaseUid == targetMember.firebaseUid)) ||
            (targetSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == targetSuffix)
        }

        val targetHasMeSaved = _contactsWhoSavedMe.value.contains(targetSuffix) ||
                _contactsWhoSavedMe.value.contains(targetMember.id) ||
                (targetMember.firebaseUid != null && _contactsWhoSavedMe.value.contains(targetMember.firebaseUid))

        return (iHaveTargetSaved && targetHasMeSaved) || hasAcceptedRequest
    }

    fun sendNextMessageRequest(
        targetMember: FamilyMember,
        initialText: String = "Hello, I would like to connect on Talkly!",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val senderUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null) ?: "self"
        val senderPhone = sessionPrefs.getString("user_phone", null)
            ?: fallbackPrefs.getString("user_phone", null) ?: ""
        val senderSuffix = PhoneUtils.extractPhoneSuffix(senderPhone)
        val senderName = sessionPrefs.getString("user_name", null)
            ?: fallbackPrefs.getString("user_name", null) ?: "Talkly User"
        val senderAvatar = sessionPrefs.getString("user_profile_pic", null) ?: ""

        val receiverPhone = targetMember.phone
        val receiverSuffix = PhoneUtils.extractPhoneSuffix(receiverPhone)
        val receiverUid = if (!targetMember.firebaseUid.isNullOrBlank()) targetMember.firebaseUid!! else targetMember.id

        val docId = java.util.UUID.randomUUID().toString()

        val supabaseReq = SupabaseMessageRequest(
            id = docId,
            senderId = senderUid,
            senderPhone = senderPhone,
            senderPhoneSuffix = senderSuffix,
            senderName = senderName,
            senderAvatar = senderAvatar,
            receiverId = receiverUid,
            receiverPhone = receiverPhone,
            receiverPhoneSuffix = receiverSuffix,
            receiverName = targetMember.name,
            status = "PENDING",
            initialMessage = initialText
        )

        repositoryScope.launch(Dispatchers.IO) {
            try {
                val success = SupabaseMessagingService.sendMessageRequest(supabaseReq)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error sending Supabase message request: ${e.localizedMessage}")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }

        sendMessage(
            memberId = targetMember.id,
            textContent = "📩 Message Request: $initialText",
            type = MessageType.TEXT
        )
    }

    fun acceptMessageRequest(request: MessageRequest, onComplete: (() -> Unit)? = null) {
        val reqId = request.id
        repositoryScope.launch(Dispatchers.IO) {
            SupabaseMessagingService.updateMessageRequestStatus(reqId, "ACCEPTED")
        }

        val currentRequests = _messageRequests.value.map {
            if (it.id == reqId) it.copy(status = "ACCEPTED") else it
        }
        _messageRequests.value = currentRequests

        val senderName = request.senderName.ifBlank { "Talkly User" }
        val senderPhone = request.senderPhone.ifBlank { request.senderPhoneSuffix }
        val senderUid = request.senderId
        val senderSuffix = request.senderPhoneSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(senderPhone) }

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null) ?: "self"
        val myPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val mySuffix = PhoneUtils.extractPhoneSuffix(myPhone)
        val myName = sessionPrefs.getString("user_name", null) ?: fallbackPrefs.getString("user_name", "Talkly User")
        val myAvatar = sessionPrefs.getString("user_profile_pic", null) ?: ""

        val applyContactRelationship = { realName: String, realAvatar: String?, realBio: String? ->
            val contactId = if (senderUid.isNotBlank() && senderUid != "self" && !senderUid.startsWith("contact_")) senderUid else "contact_${senderSuffix.ifBlank { senderPhone.replace("+", "") }}"
            
            val newContact = FamilyMember(
                id = contactId,
                name = realName,
                relation = "Contact",
                avatarUrl = realAvatar,
                status = realBio ?: "Available on Talkly 💬",
                phone = senderPhone,
                isOnline = true,
                isTyping = false,
                lastSeen = "Online",
                unreadCount = 0,
                isPinned = false,
                isRegisteredOnTalkly = true,
                firebaseUid = if (senderUid.isNotBlank() && !senderUid.startsWith("contact_")) senderUid else null
            )

            val currentList = _familyMembers.value.toMutableList()
            currentList.removeAll { 
                it.id == contactId || 
                (senderUid.isNotBlank() && (it.id == senderUid || it.firebaseUid == senderUid)) ||
                (senderSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(it.phone) == senderSuffix)
            }
            currentList.add(0, newContact)
            setFamilyMembersWithDeduplication(currentList)
            saveContactsToPrefs()

            val currentSavedMe = _contactsWhoSavedMe.value.toMutableSet()
            if (senderSuffix.isNotBlank()) currentSavedMe.add(senderSuffix)
            if (senderUid.isNotBlank()) currentSavedMe.add(senderUid)
            currentSavedMe.add(contactId)
            _contactsWhoSavedMe.value = currentSavedMe

            onComplete?.invoke()
        }

        applyContactRelationship(senderName, request.senderAvatar.ifBlank { null }, null)
    }

    fun declineMessageRequest(requestId: String, onComplete: (() -> Unit)? = null) {
        repositoryScope.launch(Dispatchers.IO) {
            SupabaseMessagingService.updateMessageRequestStatus(requestId, "DECLINED")
        }
        val currentRequests = _messageRequests.value.map {
            if (it.id == requestId) it.copy(status = "DECLINED") else it
        }
        _messageRequests.value = currentRequests
        onComplete?.invoke()
    }

    fun getGroupedActiveStatuses(currentUserId: String = "self"): List<UserStatusGroup> {
        val activeStatuses = _statuses.value.filter { !it.isExpired(_simulatedTimeOffsetMs.value) }

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val userPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val userSuffix = PhoneUtils.extractPhoneSuffix(userPhone)
        val realCurrentUid = if (currentUserId != "self") currentUserId else (currentSyncedUserId ?: "self")

        // 1. Filter out statuses from users who are NOT mutual contacts with current user (unless self)
        val privacyFilteredStatuses = activeStatuses.filter { statusItem ->
            val isSelf = statusItem.userId == "self" ||
                    statusItem.userId == realCurrentUid ||
                    (currentSyncedUserId != null && statusItem.userId == currentSyncedUserId) ||
                    (userPhone.isNotBlank() && statusItem.userId == userPhone) ||
                    (userSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(statusItem.userId) == userSuffix)
            if (isSelf) {
                true
            } else {
                val uploaderPhoneSuffix = PhoneUtils.extractPhoneSuffix(statusItem.userId)
                val matchingMember = _familyMembers.value.firstOrNull { m ->
                    m.id == statusItem.userId ||
                    m.firebaseUid == statusItem.userId ||
                    (uploaderPhoneSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == uploaderPhoneSuffix)
                } ?: FamilyMember(
                    id = statusItem.userId,
                    name = statusItem.userName,
                    phone = statusItem.userId,
                    status = "",
                    relation = "Contact"
                )

                isMutualContact(realCurrentUid, matchingMember)
            }
        }

        // 2. Normalize statuses so all stories belonging to self/duplicate accounts merge into realCurrentUid
        val normalizedStatuses = privacyFilteredStatuses.map { item ->
            val isSelf = item.userId == "self" ||
                    item.userId == realCurrentUid ||
                    (currentSyncedUserId != null && item.userId == currentSyncedUserId) ||
                    (userPhone.isNotBlank() && item.userId == userPhone) ||
                    (userSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(item.userId) == userSuffix)
            if (isSelf) {
                item.copy(userId = realCurrentUid)
            } else {
                item
            }
        }

        val groupedMap = normalizedStatuses.groupBy { it.userId }

        val groups = groupedMap.map { (uId, statusList) ->
            val firstItem = statusList.first()
            val isSelfGroup = uId == "self" || uId == realCurrentUid || (currentSyncedUserId != null && uId == currentSyncedUserId)

            val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
            val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)

            val matchingMember = if (!isSelfGroup) {
                val uPhoneSuffix = PhoneUtils.extractPhoneSuffix(uId)
                _familyMembers.value.firstOrNull { m ->
                    m.id == uId || m.firebaseUid == uId || (uPhoneSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(m.phone) == uPhoneSuffix)
                }
            } else null

            val currentUserName = if (isSelfGroup) {
                sessionPrefs.getString("user_name", null) ?: fallbackPrefs.getString("user_name", null) ?: "My Status"
            } else {
                matchingMember?.name ?: firstItem.userName
            }

            val currentUserAvatarUrl = if (isSelfGroup) {
                sessionPrefs.getString("user_profile_pic", null) ?: fallbackPrefs.getString("user_profile_pic", null) ?: firstItem.userAvatarUrl
            } else {
                matchingMember?.avatarUrl ?: firstItem.userAvatarUrl
            }

            val updatedStatuses = statusList.sortedBy { it.timestamp }.map { item ->
                item.copy(
                    userName = currentUserName,
                    userAvatarUrl = currentUserAvatarUrl
                )
            }

            UserStatusGroup(
                userId = uId,
                userName = currentUserName,
                userAvatarUrl = currentUserAvatarUrl,
                statuses = updatedStatuses
            )
        }.toMutableList()

        // Sort so "My Status" is always first, then users with unseen status, then recent
        groups.sortWith { g1, g2 ->
            val isG1Self = g1.userId == "self" || g1.userId == realCurrentUid || (currentSyncedUserId != null && g1.userId == currentSyncedUserId)
            val isG2Self = g2.userId == "self" || g2.userId == realCurrentUid || (currentSyncedUserId != null && g2.userId == currentSyncedUserId)
            when {
                isG1Self && !isG2Self -> -1
                !isG1Self && isG2Self -> 1
                g1.hasUnseen && !g2.hasUnseen -> -1
                !g1.hasUnseen && g2.hasUnseen -> 1
                else -> (g2.statuses.lastOrNull()?.timestamp ?: 0L).compareTo(g1.statuses.lastOrNull()?.timestamp ?: 0L)
            }
        }

        return groups
    }
}
