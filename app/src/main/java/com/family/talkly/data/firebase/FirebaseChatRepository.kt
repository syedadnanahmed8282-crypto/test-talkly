package com.family.talkly.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.DEFAULT_FAMILY_MEMBERS
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.MessageType
import com.family.talkly.util.MediaCompressorAndUploader
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.family.talkly.data.models.StatusItem
import com.family.talkly.data.models.UserStatusGroup
import com.family.talkly.data.models.StatusViewer
import com.family.talkly.data.models.StatusLiker

class FirebaseChatRepository(private val context: Context) {

    companion object {
        const val TAG = "Talkly_FirebaseChat"
        const val FIREBASE_PROJECT_ID = "familycallapp-e6b21"
        private const val CONTACTS_PREFS = "talkly_saved_contacts_prefs"
        private const val KEY_SAVED_CONTACTS_JSON = "saved_contacts_json"
        private const val KEY_DEMO_CLEARED = "demo_contacts_cleared"
        private const val KEY_STATUSES_JSON = "talkly_statuses_json"
        private const val KEY_BLOCKED_USERS = "talkly_blocked_user_ids"
    }

    private var firestore: FirebaseFirestore? = null
    private var membersListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var statusesListener: ListenerRegistration? = null
    private var currentSyncedUserId: String? = null
    private val contactPrefs = context.getSharedPreferences(CONTACTS_PREFS, Context.MODE_PRIVATE)

    // Real-time family members presence and status
    private val _familyMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers.asStateFlow()

    // Blocked Users state
    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

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

    init {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            firestore = FirebaseFirestore.getInstance()
            Log.i(TAG, "Initialized Firebase Firestore for project $FIREBASE_PROJECT_ID")
            setupFirestorePresenceListener()
            setupFirestoreUsersVerificationListener()
            setupFirestoreStatusesListener()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Firestore init fallback mode: ${e.localizedMessage}")
        }
        loadInitialFamilyMembers()
        seedInitialFamilyChats()
        loadStatuses()
        loadBlockedUsers()
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

    private val demoIdsSet = setOf(
        "safwan", "israfel", "jolil", "samim", "akhter", "osman", "mohammad_raiu",
        "dr_rashed", "monju", "sk_farid", "mom", "dad", "grandma", "brother", "sister"
    )

    private fun setFamilyMembersWithDeduplication(newList: List<FamilyMember>) {
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)

        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
        val currentPhone = sessionPrefs.getString("user_phone", null)
            ?: fallbackPrefs.getString("user_phone", null) ?: ""
        val currentSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(currentPhone)

        val filteredAndDeduplicated = newList
            .filter { member -> member.id !in demoIdsSet && !member.id.startsWith("demo_") }
            .filter { member ->
                val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
                val cleanMemberPhone = member.phone.filter { it.isDigit() }
                val cleanCurrentPhone = currentPhone.filter { it.isDigit() }

                val isSelfByUid = (!currentUid.isNullOrBlank() && (member.id == currentUid || member.firebaseUid == currentUid))
                val isSelfByPhone = (cleanCurrentPhone.isNotBlank() && cleanMemberPhone.isNotBlank() && cleanMemberPhone == cleanCurrentPhone)
                val isSelfBySuffix = (currentSuffix.isNotBlank() && memberSuffix.isNotBlank() && memberSuffix == currentSuffix)

                !(isSelfByUid || isSelfByPhone || isSelfBySuffix)
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

        try {
            firestore?.collection("users")
                ?.whereEqualTo("phoneSuffix", targetSuffix)
                ?.get()
                ?.addOnSuccessListener { snapshot ->
                    if (snapshot != null && !snapshot.isEmpty) {
                        val doc = snapshot.documents.maxByOrNull { it.getLong("updatedAt") ?: 0L } ?: snapshot.documents.first()
                        val docUid = doc.id
                        val name = doc.getString("name") ?: "Talkly User"
                        val rawPhone = doc.getString("phoneNumber") ?: phone
                        val docSuffix = doc.getString("phoneSuffix") ?: targetSuffix
                        val pic = doc.getString("profilePicUrl") ?: ""
                        val bio = doc.getString("bio") ?: "Available on Talkly 💬"
                        val profile = com.family.talkly.data.models.UserProfile(
                            uid = docUid,
                            name = name,
                            phoneNumber = rawPhone,
                            phoneSuffix = docSuffix,
                            profilePicUrl = pic,
                            bio = bio
                        )
                        onResult(profile)
                    } else {
                        // Fallback check across all users if phoneSuffix was not yet stored on older user documents
                        firestore?.collection("users")
                            ?.get()
                            ?.addOnSuccessListener { fullSnapshot ->
                                if (fullSnapshot != null && !fullSnapshot.isEmpty) {
                                    for (doc in fullSnapshot.documents) {
                                        val rawUserPhone = doc.getString("phoneNumber") ?: ""
                                        val docSuffix = doc.getString("phoneSuffix") ?: com.family.talkly.util.PhoneUtils.extractPhoneSuffix(rawUserPhone)
                                        val docCleanPhone = com.family.talkly.util.PhoneUtils.cleanPhoneNumber(rawUserPhone)
                                        val docUid = doc.id

                                        if ((targetSuffix.isNotBlank() && docSuffix == targetSuffix) ||
                                            (cleanPhone.isNotBlank() && (docCleanPhone.contains(cleanPhone) || cleanPhone.contains(docCleanPhone))) ||
                                            docUid == cleanPhone
                                        ) {
                                            val name = doc.getString("name") ?: "Talkly User"
                                            val pic = doc.getString("profilePicUrl") ?: ""
                                            val bio = doc.getString("bio") ?: "Available on Talkly 💬"
                                            val profile = com.family.talkly.data.models.UserProfile(
                                                uid = docUid,
                                                name = name,
                                                phoneNumber = if (rawUserPhone.isNotBlank()) rawUserPhone else phone,
                                                phoneSuffix = docSuffix,
                                                profilePicUrl = pic,
                                                bio = bio
                                            )
                                            onResult(profile)
                                            return@addOnSuccessListener
                                        }
                                    }
                                }
                                onResult(null)
                            }
                            ?.addOnFailureListener { onResult(null) }
                    }
                }
                ?.addOnFailureListener {
                    onResult(null)
                } ?: onResult(null)
        } catch (e: Exception) {
            Log.w(TAG, "Search user exception: ${e.localizedMessage}")
            onResult(null)
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
        val customId = "contact_${phoneSuffix.ifBlank { cleanPhone.replace("+", "").replace(" ", "") }}"

        val newMember = FamilyMember(
            id = customId,
            name = name.trim(),
            relation = relation.ifBlank { "Family Member" },
            avatarUrl = avatarUrl,
            status = bio.ifBlank { "Available on Talkly 💬" },
            phone = cleanPhone,
            isOnline = true,
            isTyping = false,
            lastSeen = "Online",
            unreadCount = 0,
            isPinned = false
        )

        val currentList = _familyMembers.value.toMutableList()
        // Remove existing if duplicate by ID or suffix
        currentList.removeAll { 
            it.id == customId || 
            it.phone == cleanPhone ||
            (phoneSuffix.isNotBlank() && com.family.talkly.util.PhoneUtils.extractPhoneSuffix(it.phone) == phoneSuffix)
        }
        currentList.add(0, newMember) // Put at top
        setFamilyMembersWithDeduplication(currentList)

        saveContactsToPrefs()

        // Sync to Firestore 'family_members'
        try {
            firestore?.collection("family_members")
                ?.document(customId)
                ?.set(
                    mapOf(
                        "id" to customId,
                        "name" to name,
                        "relation" to relation,
                        "phone" to cleanPhone,
                        "phoneSuffix" to phoneSuffix,
                        "status" to bio,
                        "avatarUrl" to avatarUrl,
                        "isOnline" to true
                    )
                )
        } catch (e: Exception) {
            Log.w(TAG, "Firestore sync contact failed: ${e.localizedMessage}")
        }

        onComplete?.invoke(newMember)
    }

    fun deleteContact(memberId: String) {
        val updatedList = _familyMembers.value.filter { it.id != memberId }
        setFamilyMembersWithDeduplication(updatedList)
        saveContactsToPrefs()

        try {
            firestore?.collection("family_members")?.document(memberId)?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting contact from Firestore: ${e.message}")
        }
    }

    fun clearDemoContacts() {
        contactPrefs.edit().putBoolean(KEY_DEMO_CLEARED, true).apply()
        val filteredList = _familyMembers.value.filter { it.id !in demoIdsSet && !it.id.startsWith("demo_") }
        setFamilyMembersWithDeduplication(filteredList)
        saveContactsToPrefs()
    }

    private fun setupFirestorePresenceListener() {
        try {
            membersListener = firestore?.collection("family_members")
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen failed for family_members: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        val updatedMembers = _familyMembers.value.map { member ->
                            val doc = snapshot.documents.firstOrNull { it.id == member.id }
                            if (doc != null) {
                                val online = doc.getBoolean("isOnline") ?: member.isOnline
                                val typing = doc.getBoolean("isTyping") ?: member.isTyping
                                val seen = doc.getString("lastSeen") ?: member.lastSeen
                                val activeTs = doc.getLong("lastActiveTimestamp") ?: member.lastActiveTimestamp
                                member.copy(
                                    isOnline = online,
                                    isTyping = typing,
                                    lastSeen = seen,
                                    lastActiveTimestamp = activeTs
                                )
                            } else {
                                member
                            }
                        }
                        setFamilyMembersWithDeduplication(updatedMembers)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set up Firestore snapshot listener: ${e.localizedMessage}")
        }
    }

    fun setMemberTyping(memberId: String, isTyping: Boolean) {
        val currentList = _familyMembers.value.map { member ->
            if (member.id == memberId) {
                member.copy(isTyping = isTyping)
            } else {
                member
            }
        }
        setFamilyMembersWithDeduplication(currentList)

        try {
            firestore?.collection("family_members")
                ?.document(memberId)
                ?.set(mapOf("isTyping" to isTyping, "isOnline" to true), com.google.firebase.firestore.SetOptions.merge())
        } catch (e: Exception) {
            Log.w(TAG, "Firestore setTyping error: ${e.localizedMessage}")
        }
    }

    fun setMemberPresence(
        memberId: String,
        isOnline: Boolean,
        lastSeen: String = if (isOnline) "Online" else com.family.talkly.util.PhoneUtils.formatLastSeenTime(System.currentTimeMillis()),
        lastActiveTimestamp: Long = System.currentTimeMillis()
    ) {
        val currentList = _familyMembers.value.map { member ->
            if (member.id == memberId || member.firebaseUid == memberId) {
                member.copy(
                    isOnline = isOnline,
                    lastSeen = lastSeen,
                    lastActiveTimestamp = lastActiveTimestamp,
                    isTyping = if (!isOnline) false else member.isTyping
                )
            } else {
                member
            }
        }
        setFamilyMembersWithDeduplication(currentList)

        try {
            val presenceMap = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to lastSeen,
                "lastActiveTimestamp" to lastActiveTimestamp,
                "isTyping" to if (!isOnline) false else false
            )
            firestore?.collection("family_members")
                ?.document(memberId)
                ?.set(presenceMap, com.google.firebase.firestore.SetOptions.merge())

            firestore?.collection("users")
                ?.document(memberId)
                ?.set(presenceMap, com.google.firebase.firestore.SetOptions.merge())
        } catch (e: Exception) {
            Log.w(TAG, "Firestore setPresence error: ${e.localizedMessage}")
        }
    }

    fun toggleMemberPresence(memberId: String) {
        val member = _familyMembers.value.firstOrNull { it.id == memberId } ?: return
        val newOnline = !member.isOnline
        setMemberPresence(memberId, newOnline, if (newOnline) "Online" else "Today at 10:15 AM")
    }

    private var usersListener: ListenerRegistration? = null

    private fun setupFirestoreUsersVerificationListener() {
        try {
            usersListener = firestore?.collection("users")
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen failed for users collection: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val registeredDocsBySuffix = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
                        val registeredDocsByFullPhone = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()

                        val sortedDocs = snapshot.documents.sortedByDescending { it.getLong("updatedAt") ?: 0L }

                        for (doc in sortedDocs) {
                            val rawPhone = doc.getString("phoneNumber") ?: ""
                            val suffix = doc.getString("phoneSuffix") ?: com.family.talkly.util.PhoneUtils.extractPhoneSuffix(rawPhone)
                            val cleanPhone = com.family.talkly.util.PhoneUtils.cleanPhoneNumber(rawPhone)

                            if (suffix.isNotBlank() && !registeredDocsBySuffix.containsKey(suffix)) {
                                registeredDocsBySuffix[suffix] = doc
                            }
                            if (cleanPhone.isNotBlank() && !registeredDocsByFullPhone.containsKey(cleanPhone)) {
                                registeredDocsByFullPhone[cleanPhone] = doc
                            }
                            if (!registeredDocsByFullPhone.containsKey(doc.id)) {
                                registeredDocsByFullPhone[doc.id] = doc
                            }
                        }

                        val updatedMembers = _familyMembers.value.map { member ->
                            val memberSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(member.phone)
                            val cleanMemberPhone = com.family.talkly.util.PhoneUtils.cleanPhoneNumber(member.phone)

                            var matchedDoc = registeredDocsBySuffix[memberSuffix]
                                ?: registeredDocsByFullPhone[cleanMemberPhone]
                                ?: registeredDocsByFullPhone[member.id]

                            if (matchedDoc == null && memberSuffix.length >= 6) {
                                for (doc in snapshot.documents) {
                                    val docPhone = doc.getString("phoneNumber") ?: ""
                                    val docDigits = docPhone.filter { it.isDigit() }
                                    val docSuffix = doc.getString("phoneSuffix") ?: if (docDigits.length >= 10) docDigits.takeLast(10) else docDigits
                                    val docLast10 = if (docDigits.length >= 10) docDigits.takeLast(10) else docDigits

                                    if ((docSuffix.isNotBlank() && docSuffix == memberSuffix) ||
                                        (docLast10.isNotBlank() && docLast10 == memberSuffix) ||
                                        (memberSuffix.isNotBlank() && (docLast10.endsWith(memberSuffix) || memberSuffix.endsWith(docLast10)))
                                    ) {
                                        matchedDoc = doc
                                        break
                                    }
                                }
                            }

                            val isMatch = (matchedDoc != null)
                            Log.d("ContactSync", "Checking contact: ${member.phone} | Suffix: $memberSuffix | Found Match: $isMatch")

                            if (matchedDoc != null) {
                                val uid = matchedDoc.id
                                val bio = matchedDoc.getString("bio") ?: member.status
                                val pic = matchedDoc.getString("profilePicUrl")
                                val realName = matchedDoc.getString("name") ?: member.name
                                val online = matchedDoc.getBoolean("isOnline") ?: member.isOnline
                                val seen = matchedDoc.getString("lastSeen") ?: member.lastSeen
                                val activeTs = matchedDoc.getLong("lastActiveTimestamp") ?: member.lastActiveTimestamp

                                val validAvatar = if (!pic.isNullOrBlank()) {
                                    if (pic.startsWith("http://") || pic.startsWith("https://") || pic.startsWith("data:")) {
                                        pic
                                    } else if (pic.startsWith("file://")) {
                                        try {
                                            val f = java.io.File(android.net.Uri.parse(pic).path ?: "")
                                            if (f.exists()) pic else member.avatarUrl
                                        } catch (e: Exception) {
                                            member.avatarUrl
                                        }
                                    } else {
                                        pic
                                    }
                                } else {
                                    member.avatarUrl
                                }

                                member.copy(
                                    name = if (!realName.isNullOrBlank()) realName else member.name,
                                    isRegisteredOnTalkly = true,
                                    firebaseUid = uid,
                                    avatarUrl = validAvatar,
                                    status = if (bio.isNullOrBlank()) "Available on Talkly 💬" else bio,
                                    isOnline = online,
                                    lastSeen = seen,
                                    lastActiveTimestamp = activeTs
                                )
                            } else {
                                member.copy(
                                    isRegisteredOnTalkly = false,
                                    firebaseUid = null,
                                    status = "User not registered on Talkly"
                                )
                            }
                        }
                        setFamilyMembersWithDeduplication(updatedMembers)
                        saveContactsToPrefs()
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set up users verification listener: ${e.localizedMessage}")
        }
    }

    private fun String?.isNull_or_empty_str(s: String?): Boolean = s == null || s.isEmpty()

    fun deleteChatHistory(memberId: String) {
        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap.remove(memberId)
        _messagesMap.value = updatedMap

        try {
            firestore?.collection("family_chats")
                ?.document(memberId)
                ?.collection("messages")
                ?.get()
                ?.addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing chat history in Firestore: ${e.localizedMessage}")
        }
    }

    fun triggerSimulatedTypingReply(memberId: String) {
        // Disabled per requirements: No automated mock replies, bot responses, or local fallback test logic
    }

    private fun seedInitialFamilyChats() {
        // Disabled per requirements: No fake mock chats seeded locally
        _messagesMap.value = emptyMap()
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
        return _messagesMap.value[canonicalId] ?: _messagesMap.value[memberId] ?: emptyList()
    }

    fun markMessagesAsRead(memberId: String) {
        val canonicalId = getCanonicalMemberId(memberId)
        val currentMessages = getMessagesForMember(canonicalId)
        if (currentMessages.isEmpty()) return
        var updatedAny = false

        val currentUid = currentSyncedUserId ?: "self"

        val updatedMessages = currentMessages.map { msg ->
            if (msg.senderId != "self" && msg.senderId != currentUid && !msg.isRead) {
                updatedAny = true
                val now = System.currentTimeMillis()
                val readMsg = msg.copy(isRead = true, readAtTimestamp = now, isDelivered = true)

                // Sync read status to Firestore
                try {
                    val updateData = mapOf(
                        "isRead" to true,
                        "readAtTimestamp" to now,
                        "isDelivered" to true
                    )
                    firestore?.collection("family_chats")
                        ?.document(msg.senderId)
                        ?.collection("messages")
                        ?.document(msg.id)
                        ?.update(updateData)

                    if (!currentUid.isBlank() && currentUid != "self") {
                        firestore?.collection("family_chats")
                            ?.document(currentUid)
                            ?.collection("messages")
                            ?.document(msg.id)
                            ?.update(updateData)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating read receipt in Firestore: ${e.localizedMessage}")
                }

                readMsg
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
        }

        // Reset unread count for member in list and Firestore
        val member = _familyMembers.value.firstOrNull { it.id == canonicalId || it.id == memberId }
        if (member != null && member.unreadCount > 0) {
            val updatedMembers = _familyMembers.value.map { m ->
                if (m.id == member.id) m.copy(unreadCount = 0) else m
            }
            _familyMembers.value = updatedMembers

            try {
                firestore?.collection("family_members")
                    ?.document(member.id)
                    ?.update("unreadCount", 0)
            } catch (e: Exception) {
                Log.w(TAG, "Error resetting unread count in Firestore: ${e.localizedMessage}")
            }
        }
    }

    fun toggleMessageReaction(memberId: String, messageId: String, reactionEmoji: String) {
        val canonicalId = getCanonicalMemberId(memberId)
        val currentMessages = getMessagesForMember(canonicalId)
        if (currentMessages.isEmpty()) return
        val updatedMessages = currentMessages.map { msg ->
            if (msg.id == messageId) {
                val newReaction = if (msg.reaction == reactionEmoji) null else reactionEmoji
                val updatedMsg = msg.copy(reaction = newReaction)
                
                try {
                    firestore?.collection("family_chats")
                        ?.document(canonicalId)
                        ?.collection("messages")
                        ?.document(messageId)
                        ?.update("reaction", newReaction)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating reaction in Firestore: ${e.localizedMessage}")
                }
                
                updatedMsg
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
    }

    fun toggleStarMessage(memberId: String, messageId: String) {
        val canonicalId = getCanonicalMemberId(memberId)
        val currentMessages = getMessagesForMember(canonicalId)
        if (currentMessages.isEmpty()) return
        val updatedMessages = currentMessages.map { msg ->
            if (msg.id == messageId) {
                val newStarred = !msg.isStarred
                val updatedMsg = msg.copy(isStarred = newStarred)
                try {
                    firestore?.collection("family_chats")
                        ?.document(canonicalId)
                        ?.collection("messages")
                        ?.document(messageId)
                        ?.update("isStarred", newStarred)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating star in Firestore: ${e.localizedMessage}")
                }
                updatedMsg
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
    }

    fun togglePinMessage(memberId: String, messageId: String) {
        val canonicalId = getCanonicalMemberId(memberId)
        val currentMessages = getMessagesForMember(canonicalId)
        if (currentMessages.isEmpty()) return
        val updatedMessages = currentMessages.map { msg ->
            if (msg.id == messageId) {
                val newPinned = !msg.isPinned
                val updatedMsg = msg.copy(isPinned = newPinned)
                try {
                    firestore?.collection("family_chats")
                        ?.document(canonicalId)
                        ?.collection("messages")
                        ?.document(messageId)
                        ?.update("isPinned", newPinned)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating pin message in Firestore: ${e.localizedMessage}")
                }
                updatedMsg
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
    }

    fun togglePinMember(memberId: String) {
        val updatedMembers = _familyMembers.value.map { member ->
            if (member.id == memberId) {
                val newPinned = !member.isPinned
                try {
                    firestore?.collection("family_members")
                        ?.document(memberId)
                        ?.update("isPinned", newPinned)
                } catch (e: Exception) {
                    Log.w(TAG, "Error pinning member in Firestore: ${e.localizedMessage}")
                }
                member.copy(isPinned = newPinned)
            } else {
                member
            }
        }
        _familyMembers.value = updatedMembers
    }

    private var secondaryMessagesListener: ListenerRegistration? = null

    fun startRealtimeMessageSync(currentUserId: String?) {
        if (currentUserId.isNullOrBlank()) return
        if (currentSyncedUserId == currentUserId && messagesListener != null) return

        messagesListener?.remove()
        secondaryMessagesListener?.remove()
        currentSyncedUserId = currentUserId

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val userPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val userSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(userPhone)

        val handleMessageSnapshot: (com.google.firebase.firestore.QuerySnapshot?, com.google.firebase.firestore.FirebaseFirestoreException?) -> Unit = { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listen failed for messages: ${error.localizedMessage}")
            } else if (snapshot != null && !snapshot.isEmpty) {
                val currentMap = _messagesMap.value.toMutableMap()

                for (doc in snapshot.documents) {
                    try {
                        val id = doc.getString("id") ?: doc.id
                        val senderId = doc.getString("senderId") ?: ""
                        val senderName = doc.getString("senderName")?.ifBlank { null } ?: "Talkly User"
                        val receiverId = doc.getString("receiverId") ?: ""
                        val textContent = doc.getString("textContent") ?: ""
                        val mediaUrl = doc.getString("mediaUrl")
                        val typeStr = doc.getString("messageType") ?: "TEXT"
                        val type = try { MessageType.valueOf(typeStr) } catch (e: Exception) { MessageType.TEXT }
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val isRead = doc.getBoolean("isRead") ?: false
                        val rawDelivered = doc.getBoolean("isDelivered")
                        val isDelivered = rawDelivered ?: isRead
                        val readAtTimestamp = doc.getLong("readAtTimestamp")
                        val isStarred = doc.getBoolean("isStarred") ?: false
                        val isPinned = doc.getBoolean("isPinned") ?: false

                        if (senderId != "self" && senderId != currentUserId && !isDelivered) {
                            try {
                                val updateData = mapOf("isDelivered" to true)
                                firestore?.collection("family_chats")
                                    ?.document(senderId)
                                    ?.collection("messages")
                                    ?.document(id)
                                    ?.update(updateData)
                                if (!currentUserId.isNullOrBlank()) {
                                    firestore?.collection("family_chats")
                                        ?.document(currentUserId)
                                        ?.collection("messages")
                                        ?.document(id)
                                        ?.update(updateData)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error updating delivery status in Firestore: ${e.localizedMessage}")
                            }
                        }

                        val finalDelivered = if (senderId != "self" && senderId != currentUserId) true else isDelivered

                        val message = ChatMessage(
                            id = id,
                            senderId = senderId,
                            senderName = senderName,
                            receiverId = receiverId,
                            messageType = type,
                            textContent = textContent,
                            mediaUrl = mediaUrl,
                            timestamp = timestamp,
                            isDelivered = finalDelivered,
                            isRead = isRead,
                            readAtTimestamp = readAtTimestamp,
                            isStarred = isStarred,
                            isPinned = isPinned
                        )

                        val rawOtherPartyId = if (senderId == "self" || senderId == currentUserId) receiverId else senderId
                        if (rawOtherPartyId.isBlank()) continue

                        val canonicalOtherPartyId = getCanonicalMemberId(rawOtherPartyId)
                        ensureContactInChatList(canonicalOtherPartyId, fallbackName = senderName)

                        val existingMsgs = (currentMap[canonicalOtherPartyId] ?: currentMap[rawOtherPartyId] ?: emptyList()).toMutableList()
                        val existingIndex = existingMsgs.indexOfFirst { it.id == id }
                        if (existingIndex >= 0) {
                            val existing = existingMsgs[existingIndex]
                            val preservedDelivered = existing.isDelivered || message.isDelivered
                            val preservedRead = existing.isRead || message.isRead
                            val preservedReadAt = message.readAtTimestamp ?: existing.readAtTimestamp
                            existingMsgs[existingIndex] = message.copy(
                                isDelivered = preservedDelivered,
                                isRead = preservedRead,
                                readAtTimestamp = preservedReadAt
                            )
                        } else {
                            existingMsgs.add(message)
                        }
                        existingMsgs.sortBy { it.timestamp }
                        currentMap[canonicalOtherPartyId] = existingMsgs
                        if (canonicalOtherPartyId != rawOtherPartyId) {
                            currentMap[rawOtherPartyId] = existingMsgs
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing chat message doc ${doc.id}: ${e.message}")
                    }
                }

                _messagesMap.value = currentMap
            }
        }

        try {
            // Primary listener on currentUserId
            messagesListener = firestore?.collection("family_chats")
                ?.document(currentUserId)
                ?.collection("messages")
                ?.addSnapshotListener { snapshot, error -> handleMessageSnapshot(snapshot, error) }

            // Secondary listener on user's phone suffix if different
            if (userSuffix.isNotBlank() && userSuffix != currentUserId) {
                secondaryMessagesListener = firestore?.collection("family_chats")
                    ?.document(userSuffix)
                    ?.collection("messages")
                    ?.addSnapshotListener { snapshot, error -> handleMessageSnapshot(snapshot, error) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error starting realtime message sync: ${e.localizedMessage}")
        }
    }

    fun resetSessionOnLogout() {
        try {
            messagesListener?.remove()
            messagesListener = null
            secondaryMessagesListener?.remove()
            secondaryMessagesListener = null
            currentSyncedUserId = null
            _messagesMap.value = emptyMap()
            _familyMembers.value = emptyList()
            _statuses.value = emptyList()
            contactPrefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting session on logout: ${e.localizedMessage}")
        }
    }

    fun ensureContactInChatList(memberOrUidOrPhone: String, fallbackName: String? = null) {
        if (memberOrUidOrPhone.isBlank() || memberOrUidOrPhone == "self") return
        val suffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(memberOrUidOrPhone)
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

        firestore?.collection("users")
            ?.document(memberOrUidOrPhone)
            ?.get()
            ?.addOnSuccessListener { doc ->
                if (doc != null && doc.exists() && !doc.getString("name").isNullOrBlank()) {
                    val uid = doc.id
                    val name = doc.getString("name") ?: validFallback ?: memberOrUidOrPhone
                    val phone = doc.getString("phoneNumber") ?: memberOrUidOrPhone
                    val pic = doc.getString("profilePicUrl")
                    val bio = doc.getString("bio") ?: "Available on Talkly 💬"

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
                } else if (suffix.isNotBlank()) {
                    firestore?.collection("users")
                        ?.whereEqualTo("phoneSuffix", suffix)
                        ?.get()
                        ?.addOnSuccessListener { querySnap ->
                            val foundDoc = querySnap?.documents?.firstOrNull { !it.getString("name").isNullOrBlank() }
                            if (foundDoc != null) {
                                val uid = foundDoc.id
                                val name = foundDoc.getString("name") ?: validFallback ?: memberOrUidOrPhone
                                val phone = foundDoc.getString("phoneNumber") ?: memberOrUidOrPhone
                                val pic = foundDoc.getString("profilePicUrl")
                                val bio = foundDoc.getString("bio") ?: "Available on Talkly 💬"

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
                        ?.addOnFailureListener {
                            createFallbackContact(memberOrUidOrPhone, validFallback)
                        }
                } else {
                    createFallbackContact(memberOrUidOrPhone, validFallback)
                }
            }
            ?.addOnFailureListener {
                createFallbackContact(memberOrUidOrPhone, validFallback)
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

    private fun writeMessageToCollection(targetDocId: String, message: ChatMessage) {
        if (targetDocId.isBlank()) return
        val safeMessage = if ((message.mediaUrl?.length ?: 0) > 800_000) {
            Log.e(TAG, "Preventing Firestore write: mediaUrl length (${message.mediaUrl?.length}) exceeds safe 800KB size limit.")
            message.copy(
                mediaUrl = null,
                textContent = message.textContent + " (মিডিয়া ফাইলের সাইজ অতিরিক্ত বড় হওয়ার কারণে পাঠানো সম্ভব হয়নি)"
            )
        } else {
            message
        }
        try {
            firestore?.collection("family_chats")
                ?.document(targetDocId)
                ?.collection("messages")
                ?.document(safeMessage.id)
                ?.set(safeMessage)
        } catch (e: Exception) {
            Log.w(TAG, "Error writing message to $targetDocId: ${e.localizedMessage}")
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
        replyToText: String? = null
    ) {
        val canonicalId = getCanonicalMemberId(memberId)

        val targetMember = _familyMembers.value.firstOrNull {
            it.id == canonicalId || it.id == memberId || it.firebaseUid == canonicalId || it.firebaseUid == memberId
        }
        val targetPhone = targetMember?.phone ?: if (memberId.startsWith("+") || memberId.all { it.isDigit() }) memberId else ""
        val targetSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(targetPhone)

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)

        val senderUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"

        val senderName = sessionPrefs.getString("user_name", null)
            ?: fallbackPrefs.getString("user_name", null)
            ?: "Talkly User"

        ensureContactInChatList(canonicalId, fallbackName = targetMember?.name)

        val newMessage = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            senderId = senderUid,
            senderName = senderName,
            receiverId = canonicalId,
            messageType = type,
            textContent = textContent,
            mediaUrl = mediaUrl,
            timestamp = forcedTimestamp,
            isDelivered = false,
            isRead = false,
            replyToMessageId = replyToMessageId,
            replyToSenderName = replyToSenderName,
            replyToText = replyToText
        )

        val currentList = (getMessagesForMember(canonicalId)).toMutableList()
        currentList.add(newMessage)

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = currentList
        if (canonicalId != memberId) {
            updatedMap[memberId] = currentList
        }
        _messagesMap.value = updatedMap

        // Sync to Firebase Firestore for both receiver and sender
        val resolvedTargetUid = targetMember?.firebaseUid ?: if (!canonicalId.startsWith("contact_") && !canonicalId.contains(" ")) canonicalId else ""

        if (resolvedTargetUid.isNotBlank() && resolvedTargetUid != "self") {
            writeMessageToCollection(resolvedTargetUid, newMessage)
        }
        if (targetSuffix.isNotBlank() && targetSuffix != resolvedTargetUid) {
            writeMessageToCollection(targetSuffix, newMessage)
        }
        if (canonicalId.isNotBlank() && canonicalId != resolvedTargetUid && canonicalId != targetSuffix) {
            writeMessageToCollection(canonicalId, newMessage)
        }

        // Write to Sender's collection so sender's own realtime listener updates seamlessly across devices
        if (!senderUid.isNullOrBlank() && senderUid != "self") {
            writeMessageToCollection(senderUid, newMessage)
        }

        // Async lookup if firebaseUid wasn't known yet
        if (resolvedTargetUid.isBlank() && targetSuffix.isNotBlank()) {
            try {
                firestore?.collection("users")
                    ?.whereEqualTo("phoneSuffix", targetSuffix)
                    ?.get()
                    ?.addOnSuccessListener { snap ->
                        val foundUid = snap?.documents?.firstOrNull()?.id
                        if (!foundUid.isNullOrBlank()) {
                            writeMessageToCollection(foundUid, newMessage)
                            // Update member in contacts list with foundUid
                            val updatedMembers = _familyMembers.value.map { m ->
                                if (m.id == canonicalId || m.id == memberId) {
                                    m.copy(firebaseUid = foundUid, isRegisteredOnTalkly = true)
                                } else m
                            }
                            setFamilyMembersWithDeduplication(updatedMembers)
                            saveContactsToPrefs()
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Async target lookup exception: ${e.localizedMessage}")
            }
        }
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

    private fun setupFirestoreStatusesListener() {
        try {
            statusesListener?.remove()
            statusesListener = firestore?.collection("statuses")
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore statuses listener error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val firestoreStatuses = mutableListOf<StatusItem>()
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val userId = doc.getString("userId") ?: ""
                                val userName = doc.getString("userName") ?: ""
                                val userAvatarUrl = doc.getString("userAvatarUrl")
                                val textContent = doc.getString("textContent")
                                val photoUrl = doc.getString("photoUrl")
                                val backgroundColorHex = doc.getString("backgroundColorHex") ?: "#321C3B"
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                val isVideo = doc.getBoolean("isVideo") ?: false

                                val viewersList = mutableListOf<StatusViewer>()
                                val rawViewers = doc.get("viewers") as? List<*>
                                rawViewers?.forEach { v ->
                                    if (v is Map<*, *>) {
                                        val vUid = v["userId"]?.toString() ?: ""
                                        val vName = v["userName"]?.toString() ?: ""
                                        val vAvatar = v["userAvatarUrl"]?.toString()
                                        val vTime = v["timeAgo"]?.toString() ?: "Recently"
                                        if (vUid.isNotBlank()) {
                                            viewersList.add(StatusViewer(vUid, vName, vAvatar, vTime))
                                        }
                                    }
                                }

                                val likesList = mutableListOf<StatusLiker>()
                                val rawLikes = doc.get("likes") as? List<*>
                                rawLikes?.forEach { l ->
                                    if (l is Map<*, *>) {
                                        val lUid = l["userId"]?.toString() ?: ""
                                        val lName = l["userName"]?.toString() ?: ""
                                        val lAvatar = l["userAvatarUrl"]?.toString()
                                        if (lUid.isNotBlank()) {
                                            likesList.add(StatusLiker(lUid, lName, lAvatar))
                                        }
                                    }
                                }

                                val existingLocal = _statuses.value.firstOrNull { it.id == id }
                                val isSeen = existingLocal?.isSeen ?: false

                                val item = StatusItem(
                                    id = id,
                                    userId = userId,
                                    userName = userName,
                                    userAvatarUrl = userAvatarUrl,
                                    textContent = textContent,
                                    photoUrl = photoUrl,
                                    isVideo = isVideo,
                                    backgroundColorHex = backgroundColorHex,
                                    timestamp = timestamp,
                                    isSeen = isSeen,
                                    viewers = viewersList,
                                    likes = likesList
                                )

                                if (!item.isExpired(_simulatedTimeOffsetMs.value)) {
                                    firestoreStatuses.add(item)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing status doc ${doc.id}: ${e.localizedMessage}")
                            }
                        }

                        if (firestoreStatuses.isNotEmpty()) {
                            val mergedMap = mutableMapOf<String, StatusItem>()
                            _statuses.value.forEach { local ->
                                if (!local.isExpired(_simulatedTimeOffsetMs.value)) {
                                    mergedMap[local.id] = local
                                }
                            }
                            firestoreStatuses.forEach { remote ->
                                mergedMap[remote.id] = remote
                            }

                            _statuses.value = mergedMap.values.sortedByDescending { it.timestamp }
                            saveStatusesToPrefs()
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to setup Firestore statuses listener: ${e.localizedMessage}")
        }
    }

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

        // Persistent local copy for photo Url if it's a local uri/file
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
        val newStatus = StatusItem(
            id = statusId,
            userId = resolvedUserId,
            userName = userName,
            userAvatarUrl = userAvatarUrl,
            textContent = textContent,
            photoUrl = persistentPhotoUrl,
            backgroundColorHex = backgroundColorHex,
            timestamp = System.currentTimeMillis(),
            isSeen = true,
            viewers = emptyList(),
            likes = emptyList()
        )

        val currentList = _statuses.value.toMutableList()
        currentList.add(0, newStatus)
        _statuses.value = currentList
        saveStatusesToPrefs()

        // Sync status map to Firestore
        val statusMap = mapOf(
            "id" to newStatus.id,
            "userId" to newStatus.userId,
            "userName" to newStatus.userName,
            "userAvatarUrl" to newStatus.userAvatarUrl,
            "textContent" to newStatus.textContent,
            "photoUrl" to persistentPhotoUrl,
            "isVideo" to newStatus.isVideo,
            "backgroundColorHex" to newStatus.backgroundColorHex,
            "timestamp" to newStatus.timestamp,
            "viewers" to emptyList<Map<String, Any>>(),
            "likes" to emptyList<Map<String, Any>>()
        )

        try {
            firestore?.collection("statuses")?.document(newStatus.id)?.set(statusMap)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore status sync skipped: ${e.localizedMessage}")
        }

        // Upload to Firebase Storage in background coroutine if local file exists
        val targetUploadFile = localPhotoFile ?: if (!persistentPhotoUrl.isNullOrBlank() && persistentPhotoUrl.startsWith("file://")) {
            try { File(Uri.parse(persistentPhotoUrl).path ?: "") } catch (e: Exception) { null }
        } else null

        if (targetUploadFile != null && targetUploadFile.exists()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val uploader = MediaCompressorAndUploader(context)
                    val remotePath = "status_photos/${newStatus.id}.jpg"
                    val downloadUrl = uploader.uploadToFirebaseStorage(targetUploadFile, remotePath) { _, _ -> }

                    if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                        val updatedList = _statuses.value.map { item ->
                            if (item.id == newStatus.id) item.copy(photoUrl = downloadUrl) else item
                        }
                        _statuses.value = updatedList
                        saveStatusesToPrefs()

                        firestore?.collection("statuses")?.document(newStatus.id)
                            ?.update("photoUrl", downloadUrl)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Status photo background upload error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun toggleStatusLike(
        statusId: String,
        currentUserId: String = "self",
        currentUserName: String = "You",
        currentUserAvatar: String? = null
    ) {
        val updated = _statuses.value.map { status ->
            if (status.id == statusId) {
                val existingLike = status.likes.firstOrNull { it.userId == currentUserId }
                val newLikes = if (existingLike != null) {
                    status.likes.filter { it.userId != currentUserId }
                } else {
                    status.likes + StatusLiker(currentUserId, currentUserName, currentUserAvatar)
                }

                try {
                    val rawLikesMaps = newLikes.map { l ->
                        mapOf(
                            "userId" to l.userId,
                            "userName" to l.userName,
                            "userAvatarUrl" to l.userAvatarUrl
                        )
                    }
                    firestore?.collection("statuses")?.document(statusId)
                        ?.update("likes", rawLikesMaps)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore status like update failed: ${e.localizedMessage}")
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

                    try {
                        val viewerMap = mapOf(
                            "userId" to realCurrentUid,
                            "userName" to currentUserName,
                            "userAvatarUrl" to currentUserAvatar,
                            "timeAgo" to "Just now"
                        )
                        firestore?.collection("statuses")?.document(statusId)
                            ?.update("viewers", com.google.firebase.firestore.FieldValue.arrayUnion(viewerMap))
                    } catch (e: Exception) {
                        Log.w(TAG, "Firestore status viewer update failed: ${e.localizedMessage}")
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

    fun getGroupedActiveStatuses(currentUserId: String = "self"): List<UserStatusGroup> {
        val activeStatuses = _statuses.value.filter { !it.isExpired(_simulatedTimeOffsetMs.value) }
        val groupedMap = activeStatuses.groupBy { it.userId }

        val realCurrentUid = if (currentUserId != "self") currentUserId else (currentSyncedUserId ?: "self")

        val groups = groupedMap.map { (uId, statusList) ->
            val firstItem = statusList.first()
            val isSelfGroup = uId == "self" || uId == realCurrentUid || (currentSyncedUserId != null && uId == currentSyncedUserId)
            UserStatusGroup(
                userId = uId,
                userName = if (isSelfGroup) "My Status" else firstItem.userName,
                userAvatarUrl = firstItem.userAvatarUrl,
                statuses = statusList.sortedBy { it.timestamp }
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
