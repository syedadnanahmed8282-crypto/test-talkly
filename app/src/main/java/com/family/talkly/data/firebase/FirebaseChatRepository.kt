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

import com.family.talkly.data.local.TalklyDatabase
import com.family.talkly.data.local.entity.ChatMessageEntity
import com.family.talkly.data.models.MessageRequest
import com.family.talkly.data.models.StatusItem
import com.family.talkly.data.models.UserStatusGroup
import com.family.talkly.data.models.StatusViewer
import com.family.talkly.data.models.StatusLiker
import com.family.talkly.util.PhoneUtils
import kotlinx.coroutines.runBlocking

class FirebaseChatRepository(private val context: Context) {

    companion object {
        const val TAG = "Talkly_FirebaseChat"
        const val FIREBASE_PROJECT_ID = "familycallapp-e6b21"
        private const val CONTACTS_PREFS = "talkly_saved_contacts_prefs"
        private const val KEY_SAVED_CONTACTS_JSON = "saved_contacts_json"
        private const val KEY_DEMO_CLEARED = "demo_contacts_cleared"
        private const val KEY_STATUSES_JSON = "talkly_statuses_json"
        private const val KEY_BLOCKED_USERS = "talkly_blocked_user_ids"
        private const val KEY_DELETED_CONTACT_IDS = "talkly_deleted_contact_ids"
    }

    private var firestore: FirebaseFirestore? = null
    private var membersListener: ListenerRegistration? = null
    private var usersCollectionListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var statusesListener: ListenerRegistration? = null
    private var messageRequestsListener: ListenerRegistration? = null
    private var contactsSavedMeListener: ListenerRegistration? = null
    private var currentSyncedUserId: String? = null
    private val contactPrefs = context.getSharedPreferences(CONTACTS_PREFS, Context.MODE_PRIVATE)
    private val database: TalklyDatabase by lazy { TalklyDatabase.getInstance(context) }

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
                        val pic = doc.getString("profilePicUrl")
                            ?: doc.getString("photoUrl")
                            ?: doc.getString("photoURL")
                            ?: doc.getString("avatarUrl")
                            ?: doc.getString("profilePic") ?: ""
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
                                            val pic = doc.getString("profilePicUrl")
                                                ?: doc.getString("photoUrl")
                                                ?: doc.getString("photoURL")
                                                ?: doc.getString("avatarUrl")
                                                ?: doc.getString("profilePic") ?: ""
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

        val saveLocalAndFirestoreContact = { targetUid: String?, realName: String, realAvatar: String?, realBio: String? ->
            val contactId = if (!targetUid.isNullOrBlank() && !targetUid.startsWith("contact_")) targetUid else "contact_${phoneSuffix.ifBlank { cleanPhone.replace("+", "").replace(" ", "") }}"

            // Unmark from deleted list if user is explicitly re-adding contact
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

            // Store relationship reference mapping in logged-in user's contacts collection
            if (currentUid.isNotBlank() && currentUid != "self") {
                try {
                    val docKey = if (phoneSuffix.isNotBlank()) phoneSuffix else contactId
                    firestore?.collection("users")
                        ?.document(currentUid)
                        ?.collection("contacts")
                        ?.document(docKey)
                        ?.set(
                            mapOf(
                                "uid" to targetUid,
                                "phoneSuffix" to phoneSuffix,
                                "phone" to cleanPhone,
                                "name" to realName,
                                "avatarUrl" to realAvatar,
                                "savedAt" to System.currentTimeMillis()
                            )
                        )
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore sync contact failed: ${e.localizedMessage}")
                }
            }

            onComplete?.invoke(newMember)
        }

        // Query Firestore 'users' collection to check if target user already exists
        if (phoneSuffix.isNotBlank()) {
            firestore?.collection("users")
                ?.whereEqualTo("phoneSuffix", phoneSuffix)
                ?.get()
                ?.addOnSuccessListener { querySnap ->
                    val matchedDoc = querySnap?.documents?.firstOrNull()
                    if (matchedDoc != null && matchedDoc.exists()) {
                        val targetUid = matchedDoc.id
                        val realName = matchedDoc.getString("name")?.takeIf { it.isNotBlank() } ?: name.trim()
                        val realAvatar = matchedDoc.getString("profilePicUrl")
                            ?: matchedDoc.getString("photoUrl")
                            ?: matchedDoc.getString("photoURL")
                            ?: matchedDoc.getString("avatarUrl")
                            ?: matchedDoc.getString("profilePic")
                            ?: avatarUrl
                        val realBio = matchedDoc.getString("bio")?.takeIf { it.isNotBlank() } ?: bio
                        saveLocalAndFirestoreContact(targetUid, realName, realAvatar, realBio)
                    } else {
                        saveLocalAndFirestoreContact(null, name.trim(), avatarUrl, bio)
                    }
                }
                ?.addOnFailureListener {
                    saveLocalAndFirestoreContact(null, name.trim(), avatarUrl, bio)
                }
        } else {
            saveLocalAndFirestoreContact(null, name.trim(), avatarUrl, bio)
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

        // 2. Remove contact from _familyMembers and invalidate/purge local contact cache
        val updatedList = _familyMembers.value.filter { member ->
            member.id != memberId &&
            member.id != canonicalId &&
            (targetFirebaseUid.isBlank() || member.firebaseUid != targetFirebaseUid) &&
            (targetSuffix.isBlank() || PhoneUtils.extractPhoneSuffix(member.phone) != targetSuffix)
        }
        setFamilyMembersWithDeduplication(updatedList)
        saveContactsToPrefs()

        // 3. Delete from Firestore 'family_members'
        try {
            firestore?.collection("family_members")?.document(memberId)?.delete()
            if (canonicalId != memberId) {
                firestore?.collection("family_members")?.document(canonicalId)?.delete()
            }
            if (targetFirebaseUid.isNotBlank()) {
                firestore?.collection("family_members")?.document(targetFirebaseUid)?.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting contact from Firestore family_members: ${e.message}")
        }

        // 4. Delete contact record from Firestore 'users/{uid}/contacts'
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null) ?: "self"
        val myPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val mySuffix = PhoneUtils.extractPhoneSuffix(myPhone)

        if (currentUid.isNotBlank() && currentUid != "self") {
            try {
                val userContactsRef = firestore?.collection("users")?.document(currentUid)?.collection("contacts")
                userContactsRef?.document(memberId)?.delete()
                if (canonicalId != memberId) userContactsRef?.document(canonicalId)?.delete()
                if (targetSuffix.isNotBlank()) userContactsRef?.document(targetSuffix)?.delete()
                if (targetFirebaseUid.isNotBlank()) userContactsRef?.document(targetFirebaseUid)?.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting contact from Firestore users/$currentUid/contacts: ${e.localizedMessage}")
            }
        }

        // 5. PURGE & REVOKE MESSAGE REQUESTS to reset status to Message Request required
        val remainingRequests = _messageRequests.value.filterNot { req ->
            (req.senderId == memberId || req.senderId == canonicalId || (targetFirebaseUid.isNotBlank() && req.senderId == targetFirebaseUid) || (targetSuffix.isNotBlank() && req.senderPhoneSuffix == targetSuffix)) ||
            (req.receiverId == memberId || req.receiverId == canonicalId || (targetFirebaseUid.isNotBlank() && req.receiverId == targetFirebaseUid) || (targetSuffix.isNotBlank() && req.receiverPhoneSuffix == targetSuffix))
        }
        _messageRequests.value = remainingRequests

        // Remove from Firestore message_requests collection
        try {
            firestore?.collection("message_requests")
                ?.get()
                ?.addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        val sId = doc.getString("senderId") ?: ""
                        val rId = doc.getString("receiverId") ?: ""
                        val sSuffix = doc.getString("senderPhoneSuffix") ?: ""
                        val rSuffix = doc.getString("receiverPhoneSuffix") ?: ""

                        val isSenderTarget = sId in listOf(memberId, canonicalId, targetFirebaseUid) || (targetSuffix.isNotBlank() && sSuffix == targetSuffix)
                        val isReceiverTarget = rId in listOf(memberId, canonicalId, targetFirebaseUid) || (targetSuffix.isNotBlank() && rSuffix == targetSuffix)

                        val isSenderMe = sId == currentUid || (mySuffix.isNotBlank() && sSuffix == mySuffix)
                        val isReceiverMe = rId == currentUid || (mySuffix.isNotBlank() && rSuffix == mySuffix)

                        if ((isSenderTarget && isReceiverMe) || (isReceiverTarget && isSenderMe)) {
                            doc.reference.delete()
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting message requests from Firestore: ${e.localizedMessage}")
        }

        // 6. Purge from contactsWhoSavedMe
        val currentSavedMe = _contactsWhoSavedMe.value.toMutableSet()
        currentSavedMe.remove(memberId)
        currentSavedMe.remove(canonicalId)
        if (targetSuffix.isNotBlank()) currentSavedMe.remove(targetSuffix)
        if (targetFirebaseUid.isNotBlank()) currentSavedMe.remove(targetFirebaseUid)
        _contactsWhoSavedMe.value = currentSavedMe
    }

    fun clearDemoContacts() {
        contactPrefs.edit().putBoolean(KEY_DEMO_CLEARED, true).apply()
        val filteredList = _familyMembers.value.filter { it.id !in demoIdsSet && !it.id.startsWith("demo_") }
        setFamilyMembersWithDeduplication(filteredList)
        saveContactsToPrefs()
    }

    private fun setupFirestorePresenceListener() {
        try {
            membersListener?.remove()
            membersListener = firestore?.collection("family_members")
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Listen failed for family_members: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
                        val myPhone = sessionPrefs.getString("user_phone", "") ?: ""
                        val myPhoneSuffix = PhoneUtils.extractPhoneSuffix(myPhone)
                        val myId = currentSyncedUserId ?: ""

                        val updatedMembers = _familyMembers.value.map { member ->
                            val doc = snapshot.documents.filter { 
                                it.id == member.id || 
                                it.id == member.firebaseUid ||
                                (member.phone.isNotBlank() && PhoneUtils.extractPhoneSuffix(it.id) == PhoneUtils.extractPhoneSuffix(member.phone))
                            }.maxByOrNull { it.getLong("updatedAt") ?: 0L }

                            if (doc != null) {
                                val online = doc.getBoolean("isOnline") ?: member.isOnline
                                val rawTyping = doc.getBoolean("isTyping") ?: false
                                val typingTo = doc.getString("typingTo")
                                
                                val isTypingTargetedToMe = rawTyping && (
                                    typingTo.isNullOrBlank() ||
                                    typingTo == myId ||
                                    typingTo == "self" ||
                                    (myPhoneSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(typingTo) == myPhoneSuffix)
                                )

                                val seen = doc.getString("lastSeen") ?: member.lastSeen
                                val activeTs = doc.getLong("lastActiveTimestamp") ?: member.lastActiveTimestamp
                                val avatar = doc.getString("profilePicUrl") ?: doc.getString("avatarUrl") ?: doc.getString("photoUrl") ?: doc.getString("photoURL") ?: doc.getString("profilePic")
                                val cover = doc.getString("coverPhotoUrl") ?: doc.getString("coverUrl")
                                val nameStr = doc.getString("name")
                                val statusStr = doc.getString("status") ?: doc.getString("bio")

                                member.copy(
                                    name = if (!nameStr.isNullOrBlank() && nameStr != "Talkly User") nameStr else member.name,
                                    avatarUrl = if (!avatar.isNullOrBlank()) avatar else member.avatarUrl,
                                    coverPhotoUrl = if (!cover.isNullOrBlank()) cover else member.coverPhotoUrl,
                                    status = if (!statusStr.isNullOrBlank()) statusStr else member.status,
                                    isOnline = online,
                                    isTyping = isTypingTargetedToMe,
                                    lastSeen = seen,
                                    lastActiveTimestamp = activeTs
                                )
                            } else {
                                member
                            }
                        }
                        setFamilyMembersWithDeduplication(updatedMembers)
                        saveContactsToPrefs()
                    }
                }

            usersCollectionListener?.remove()
            usersCollectionListener = firestore?.collection("users")
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                    val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
                    val myPhone = sessionPrefs.getString("user_phone", "") ?: ""
                    val myPhoneSuffix = PhoneUtils.extractPhoneSuffix(myPhone)
                    val myId = currentSyncedUserId ?: ""

                    val updatedMembers = _familyMembers.value.map { member ->
                        val doc = snapshot.documents.filter { 
                            it.id == member.id || 
                            it.id == member.firebaseUid ||
                            (member.phone.isNotBlank() && PhoneUtils.extractPhoneSuffix(it.getString("phoneNumber") ?: "") == PhoneUtils.extractPhoneSuffix(member.phone))
                        }.maxByOrNull { it.getLong("updatedAt") ?: 0L }

                        if (doc != null) {
                            val avatar = doc.getString("profilePicUrl") ?: doc.getString("photoUrl") ?: doc.getString("photoURL") ?: doc.getString("avatarUrl") ?: doc.getString("profilePic")
                            val cover = doc.getString("coverPhotoUrl") ?: doc.getString("coverUrl")
                            val nameStr = doc.getString("name")
                            val bioStr = doc.getString("bio") ?: doc.getString("status")

                            val rawTyping = doc.getBoolean("isTyping") ?: false
                            val typingTo = doc.getString("typingTo")
                            
                            val isTypingTargetedToMe = rawTyping && (
                                typingTo.isNullOrBlank() ||
                                typingTo == myId ||
                                typingTo == "self" ||
                                (myPhoneSuffix.isNotBlank() && PhoneUtils.extractPhoneSuffix(typingTo) == myPhoneSuffix)
                            )

                            member.copy(
                                name = if (!nameStr.isNullOrBlank() && nameStr != "Talkly User") nameStr else member.name,
                                avatarUrl = if (!avatar.isNullOrBlank()) avatar else member.avatarUrl,
                                coverPhotoUrl = if (!cover.isNullOrBlank()) cover else member.coverPhotoUrl,
                                status = if (!bioStr.isNullOrBlank()) bioStr else member.status,
                                isTyping = isTypingTargetedToMe || member.isTyping
                            )
                        } else {
                            member
                        }
                    }
                    setFamilyMembersWithDeduplication(updatedMembers)
                    saveContactsToPrefs()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set up Firestore snapshot listener: ${e.localizedMessage}")
        }
    }

    fun setTypingStatus(targetMemberId: String, isTyping: Boolean) {
        val myId = currentSyncedUserId ?: "self"
        
        // Update local state if targetMemberId matches
        val currentList = _familyMembers.value.map { member ->
            if (member.id == targetMemberId && (myId == "self" || myId.isBlank())) {
                member.copy(isTyping = isTyping)
            } else {
                member
            }
        }
        setFamilyMembersWithDeduplication(currentList)

        try {
            val typingData = mapOf(
                "isTyping" to isTyping,
                "typingTo" to targetMemberId,
                "isOnline" to true,
                "lastActiveTimestamp" to System.currentTimeMillis()
            )

            if (!myId.isNullOrBlank() && myId != "self" && firestore != null) {
                firestore?.collection("family_members")
                    ?.document(myId)
                    ?.set(typingData, com.google.firebase.firestore.SetOptions.merge())

                firestore?.collection("users")
                    ?.document(myId)
                    ?.set(typingData, com.google.firebase.firestore.SetOptions.merge())

                val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
                val myPhone = sessionPrefs.getString("user_phone", "") ?: ""
                val myPhoneSuffix = PhoneUtils.extractPhoneSuffix(myPhone)
                if (myPhoneSuffix.isNotBlank()) {
                    firestore?.collection("family_members")
                        ?.document(myPhoneSuffix)
                        ?.set(typingData, com.google.firebase.firestore.SetOptions.merge())
                }
            } else if (firestore != null) {
                // If local myId is not set, set directly on target member for fallback sync
                firestore?.collection("family_members")
                    ?.document(targetMemberId)
                    ?.set(mapOf("isTyping" to isTyping, "isOnline" to true), com.google.firebase.firestore.SetOptions.merge())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore setTypingStatus error: ${e.localizedMessage}")
        }
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

        try {
            val presenceMap = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to effectiveLastSeen,
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
                                    ?: matchedDoc.getString("photoUrl")
                                    ?: matchedDoc.getString("photoURL")
                                    ?: matchedDoc.getString("avatarUrl")
                                    ?: matchedDoc.getString("profilePic")
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

        // Delete associated media files from Firebase Storage bucket
        messagesToDelete.distinctBy { it.id }.forEach { msg ->
            val url = msg.mediaUrl
            if (!url.isNullOrBlank() && (url.startsWith("http") || url.startsWith("gs://"))) {
                try {
                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().getReferenceFromUrl(url)
                    storageRef.delete().addOnFailureListener { e ->
                        Log.w(TAG, "Storage media delete failed: ${e.localizedMessage}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing media URL for deletion: ${e.localizedMessage}")
                }
            }
        }

        // 2. Wipe from local messagesMap memory cache and Room Database
        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap.remove(memberId)
        updatedMap.remove(canonicalId)
        if (targetFirebaseUid.isNotBlank()) updatedMap.remove(targetFirebaseUid)
        if (targetSuffix.isNotBlank()) updatedMap.remove(targetSuffix)
        _messagesMap.value = updatedMap

        runBlocking {
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

        // 4. Delete from Firestore collections 'family_chats'
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null) ?: "self"

        val targetDocs = listOfNotNull(
            memberId.ifBlank { null },
            canonicalId.ifBlank { null },
            targetFirebaseUid.ifBlank { null },
            targetSuffix.ifBlank { null }
        ).distinct()

        targetDocs.forEach { docId ->
            try {
                firestore?.collection("family_chats")
                    ?.document(docId)
                    ?.collection("messages")
                    ?.get()
                    ?.addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) {
                            doc.reference.delete()
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing family_chats/$docId/messages in Firestore: ${e.localizedMessage}")
            }
        }

        // Also clear from current user's family_chats document
        if (currentUid.isNotBlank() && currentUid != "self") {
            try {
                firestore?.collection("family_chats")
                    ?.document(currentUid)
                    ?.collection("messages")
                    ?.get()
                    ?.addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) {
                            val rId = doc.getString("receiverId") ?: ""
                            val sId = doc.getString("senderId") ?: ""
                            val rSuffix = PhoneUtils.extractPhoneSuffix(doc.getString("receiverPhone") ?: "")
                            val sSuffix = PhoneUtils.extractPhoneSuffix(doc.getString("senderPhone") ?: "")
                            if (rId in targetDocs || sId in targetDocs ||
                                (targetSuffix.isNotBlank() && (rSuffix == targetSuffix || sSuffix == targetSuffix))) {
                                doc.reference.delete()
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing currentUid messages in Firestore: ${e.localizedMessage}")
            }
        }
    }

    fun triggerSimulatedTypingReply(memberId: String) {
        // Disabled per requirements: No automated mock replies, bot responses, or local fallback test logic
    }

    private val diskExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun saveMessagesToDisk() {
        diskExecutor.execute {
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

                // 1. Save to Room Database
                runBlocking {
                    database.chatMessageDao().clearAllMessages()
                    if (entities.isNotEmpty()) {
                        database.chatMessageDao().insertMessages(entities)
                    }
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
        try {
            // 1. Try loading from Room Database
            val loadedEntities = runBlocking {
                database.chatMessageDao().getAllMessagesSync()
            }

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
                    return
                }
            }

            // 2. Fallback: Load from JSON file if Room is empty
            val file = java.io.File(context.filesDir, "cached_talkly_messages_v2.json")
            if (!file.exists()) return
            val jsonStr = file.readText()
            if (jsonStr.isBlank()) return
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

    private fun autoStartRealtimeSyncIfLoggedIn() {
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val currentUid = sessionPrefs.getString("user_uid", null) ?: fallbackPrefs.getString("user_uid", null)
        if (!currentUid.isNullOrBlank()) {
            startRealtimeMessageSync(currentUid)
        }
    }

    private fun seedInitialFamilyChats() {
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
            saveMessagesToDisk()
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
        val rawList = _messagesMap.value[canonicalId] ?: _messagesMap.value[memberId] ?: emptyList()
        if (rawList.isEmpty()) return

        val targetMember = _familyMembers.value.firstOrNull {
            it.id == canonicalId || it.id == memberId || it.firebaseUid == canonicalId || it.firebaseUid == memberId
        }
        val targetPhone = targetMember?.phone ?: if (memberId.startsWith("+") || memberId.all { it.isDigit() }) memberId else ""
        val targetSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(targetPhone)
        val resolvedTargetUid = targetMember?.firebaseUid ?: if (!canonicalId.startsWith("contact_") && !canonicalId.contains(" ")) canonicalId else ""

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val senderUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"

        var newReactionValue: String? = null

        val updatedMessages = rawList.map { msg ->
            if (msg.id == messageId) {
                val newReaction = if (msg.reaction == reactionEmoji) null else reactionEmoji
                newReactionValue = newReaction
                msg.copy(reaction = newReaction)
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

        val targetDocs = listOfNotNull(
            canonicalId.ifBlank { null },
            memberId.ifBlank { null },
            resolvedTargetUid.ifBlank { null },
            targetSuffix.ifBlank { null },
            senderUid.ifBlank { null }
        ).distinct()

        targetDocs.forEach { docId ->
            try {
                firestore?.collection("family_chats")
                    ?.document(docId)
                    ?.collection("messages")
                    ?.document(messageId)
                    ?.update("reaction", newReactionValue)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating reaction in Firestore doc $docId: ${e.localizedMessage}")
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

        val rawList = _messagesMap.value[canonicalId] ?: _messagesMap.value[memberId] ?: emptyList()
        var updatedMsg: ChatMessage? = null

        val updatedList = rawList.map { msg ->
            if (msg.id == messageId) {
                val currentDeleted = msg.deletedForUsers.toMutableList()
                if (!currentDeleted.contains(currentUid)) currentDeleted.add(currentUid)
                if (!currentDeleted.contains("self")) currentDeleted.add("self")
                val newMsg = msg.copy(deletedForUsers = currentDeleted)
                updatedMsg = newMsg
                newMsg
            } else {
                msg
            }
        }

        val updatedMap = _messagesMap.value.toMutableMap()
        updatedMap[canonicalId] = updatedList
        if (canonicalId != memberId) updatedMap[memberId] = updatedList
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        if (currentUid.isNotBlank() && currentUid != "self" && updatedMsg != null) {
            try {
                firestore?.collection("family_chats")
                    ?.document(currentUid)
                    ?.collection("messages")
                    ?.document(messageId)
                    ?.update("deletedForUsers", updatedMsg?.deletedForUsers)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating deletedForUsers in Firestore: ${e.localizedMessage}")
            }
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

        val targetMember = _familyMembers.value.firstOrNull {
            it.id == canonicalId || it.id == memberId || it.firebaseUid == canonicalId || it.firebaseUid == memberId
        }
        val targetPhone = targetMember?.phone ?: if (memberId.startsWith("+") || memberId.all { it.isDigit() }) memberId else ""
        val targetSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(targetPhone)
        val resolvedTargetUid = targetMember?.firebaseUid ?: if (!canonicalId.startsWith("contact_") && !canonicalId.contains(" ")) canonicalId else ""

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val senderUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"

        val targetDocs = listOfNotNull(
            canonicalId.ifBlank { null },
            memberId.ifBlank { null },
            resolvedTargetUid.ifBlank { null },
            targetSuffix.ifBlank { null },
            senderUid.ifBlank { null }
        ).distinct()

        val updateData = mapOf(
            "isDeletedForEveryone" to true,
            "textContent" to "This message was deleted",
            "mediaUrl" to null
        )

        targetDocs.forEach { docId ->
            try {
                firestore?.collection("family_chats")
                    ?.document(docId)
                    ?.collection("messages")
                    ?.document(messageId)
                    ?.update(updateData)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating delete for everyone in Firestore doc $docId: ${e.localizedMessage}")
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

        val targetMember = _familyMembers.value.firstOrNull {
            it.id == canonicalId || it.id == memberId || it.firebaseUid == canonicalId || it.firebaseUid == memberId
        }
        val targetPhone = targetMember?.phone ?: if (memberId.startsWith("+") || memberId.all { it.isDigit() }) memberId else ""
        val targetSuffix = com.family.talkly.util.PhoneUtils.extractPhoneSuffix(targetPhone)
        val resolvedTargetUid = targetMember?.firebaseUid ?: if (!canonicalId.startsWith("contact_") && !canonicalId.contains(" ")) canonicalId else ""

        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val senderUid = currentSyncedUserId
            ?: sessionPrefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)
            ?: "self"

        val targetDocs = listOfNotNull(
            canonicalId.ifBlank { null },
            memberId.ifBlank { null },
            resolvedTargetUid.ifBlank { null },
            targetSuffix.ifBlank { null },
            senderUid.ifBlank { null }
        ).distinct()

        val updateData = mapOf(
            "textContent" to newText,
            "isEdited" to true
        )

        targetDocs.forEach { docId ->
            try {
                firestore?.collection("family_chats")
                    ?.document(docId)
                    ?.collection("messages")
                    ?.document(messageId)
                    ?.update(updateData)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating editMessage in Firestore doc $docId: ${e.localizedMessage}")
            }
        }

        return true
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
        saveMessagesToDisk()
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
        saveMessagesToDisk()
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
    private var thirdMessagesListener: ListenerRegistration? = null
    private var isInitialMessageSyncDone = false

    fun startRealtimeMessageSync(currentUserId: String?) {
        if (currentUserId.isNullOrBlank()) return
        if (currentSyncedUserId == currentUserId && messagesListener != null) return

        messagesListener?.remove()
        secondaryMessagesListener?.remove()
        thirdMessagesListener?.remove()
        currentSyncedUserId = currentUserId
        isInitialMessageSyncDone = false

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
                        val reaction = doc.getString("reaction")
                        val isStarred = doc.getBoolean("isStarred") ?: false
                        val isPinned = doc.getBoolean("isPinned") ?: false
                        val replyToMessageId = doc.getString("replyToMessageId")
                        val replyToSenderName = doc.getString("replyToSenderName")
                        val replyToText = doc.getString("replyToText")
                        val isEdited = doc.getBoolean("isEdited") ?: false
                        val isDeletedForEveryone = doc.getBoolean("isDeletedForEveryone") ?: false
                        val deletedForUsers = (doc.get("deletedForUsers") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

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
                            if (isInitialMessageSyncDone && senderId != "self" && senderId != currentUserId && !isRead) {
                                val displayContent = when (type) {
                                    MessageType.TEXT -> textContent
                                    MessageType.IMAGE -> "📷 Photo"
                                    MessageType.VIDEO -> "📹 Video"
                                    MessageType.VOICE_NOTE -> "🎵 Voice message"
                                    MessageType.CALL_LOG -> "📞 Call"
                                }
                                com.family.talkly.util.TalklyNotificationHelper.postIncomingMessageNotification(
                                    context = context,
                                    senderName = if (senderName.isNotBlank() && senderName != "Talkly User") senderName else "New Message",
                                    messageText = displayContent,
                                    chatMemberId = canonicalOtherPartyId
                                )
                            }
                        }
                        existingMsgs.sortBy { it.timestamp }
                        currentMap[canonicalOtherPartyId] = existingMsgs
                        if (canonicalOtherPartyId != rawOtherPartyId && currentMap.containsKey(rawOtherPartyId)) {
                            currentMap.remove(rawOtherPartyId)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing chat message doc ${doc.id}: ${e.message}")
                    }
                }

                _messagesMap.value = currentMap
                saveMessagesToDisk()
                isInitialMessageSyncDone = true
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

            val cleanUserPhone = com.family.talkly.util.PhoneUtils.cleanPhoneNumber(userPhone)
            if (cleanUserPhone.isNotBlank() && cleanUserPhone != currentUserId && cleanUserPhone != userSuffix) {
                thirdMessagesListener = firestore?.collection("family_chats")
                    ?.document(cleanUserPhone)
                    ?.collection("messages")
                    ?.addSnapshotListener { snapshot, error -> handleMessageSnapshot(snapshot, error) }
            }

            // Realtime listener for message requests & mutual contact sync
            setupFirestoreMessageRequestsListener(currentUserId)
        } catch (e: Exception) {
            Log.w(TAG, "Error starting realtime message sync: ${e.localizedMessage}")
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

        // Merge & normalize local statuses for self profile
        val updatedStatuses = _statuses.value.map { item ->
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

        // Restart realtime message listener with primary user ID
        startRealtimeMessageSync(primaryUid)
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

        firestore?.collection("users")
            ?.document(memberOrUidOrPhone)
            ?.get()
            ?.addOnSuccessListener { doc ->
                if (doc != null && doc.exists() && !doc.getString("name").isNullOrBlank()) {
                    val uid = doc.id
                    val name = doc.getString("name") ?: validFallback ?: memberOrUidOrPhone
                    val phone = doc.getString("phoneNumber") ?: memberOrUidOrPhone
                    val pic = doc.getString("profilePicUrl")
                        ?: doc.getString("photoUrl")
                        ?: doc.getString("photoURL")
                        ?: doc.getString("avatarUrl")
                        ?: doc.getString("profilePic")
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
                                    ?: foundDoc.getString("photoUrl")
                                    ?: foundDoc.getString("photoURL")
                                    ?: foundDoc.getString("avatarUrl")
                                    ?: foundDoc.getString("profilePic")
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
        if (canonicalId != memberId && updatedMap.containsKey(memberId)) {
            updatedMap.remove(memberId)
        }
        _messagesMap.value = updatedMap
        saveMessagesToDisk()

        val cleanTargetPhone = com.family.talkly.util.PhoneUtils.cleanPhoneNumber(targetPhone)

        // Sync to Firebase Firestore using unique Chat Room ID and user UIDs
        val resolvedTargetUid = targetMember?.firebaseUid ?: if (!canonicalId.startsWith("contact_") && !canonicalId.contains(" ")) canonicalId else ""

        val roomId = if (senderUid.isNotBlank() && senderUid != "self" && resolvedTargetUid.isNotBlank() && resolvedTargetUid != "self") {
            getChatRoomId(senderUid, resolvedTargetUid)
        } else null

        if (roomId != null) {
            writeMessageToCollection(roomId, newMessage)
        }
        if (resolvedTargetUid.isNotBlank() && resolvedTargetUid != "self") {
            writeMessageToCollection(resolvedTargetUid, newMessage)
        }
        if (targetSuffix.isNotBlank() && targetSuffix != resolvedTargetUid && targetSuffix != roomId) {
            writeMessageToCollection(targetSuffix, newMessage)
        }
        if (cleanTargetPhone.isNotBlank() && cleanTargetPhone != resolvedTargetUid && cleanTargetPhone != targetSuffix && cleanTargetPhone != roomId) {
            writeMessageToCollection(cleanTargetPhone, newMessage)
        }
        if (canonicalId.isNotBlank() && canonicalId != resolvedTargetUid && canonicalId != targetSuffix && canonicalId != cleanTargetPhone && canonicalId != roomId) {
            writeMessageToCollection(canonicalId, newMessage)
        }

        // Write to Sender's collection so sender's own realtime listener updates seamlessly across devices
        if (!senderUid.isNullOrBlank() && senderUid != "self" && senderUid != roomId) {
            writeMessageToCollection(senderUid, newMessage)
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
        com.family.talkly.util.FcmTokenManager.sendHighPriorityPush(
            targetUid = resolvedTargetUid,
            targetPhoneSuffix = targetSuffix,
            dataPayload = fcmPayload
        )

        // Ultra-fast async lookup if firebaseUid wasn't known yet
        if (resolvedTargetUid.isBlank() && targetSuffix.isNotBlank()) {
            try {
                firestore?.collection("users_phone_index")
                    ?.document(targetSuffix)
                    ?.get()
                    ?.addOnSuccessListener { doc ->
                        val foundUid = doc?.getString("uid") ?: doc?.getString("firebaseUid")
                        if (!foundUid.isNullOrBlank()) {
                            writeMessageToCollection(foundUid, newMessage)
                            val fastRoomId = getChatRoomId(senderUid, foundUid)
                            writeMessageToCollection(fastRoomId, newMessage)
                            val updatedMembers = _familyMembers.value.map { m ->
                                if (m.id == canonicalId || m.id == memberId) {
                                    m.copy(firebaseUid = foundUid, isRegisteredOnTalkly = true)
                                } else m
                            }
                            setFamilyMembersWithDeduplication(updatedMembers)
                            saveContactsToPrefs()
                        }
                    }

                firestore?.collection("users")
                    ?.whereEqualTo("phoneSuffix", targetSuffix)
                    ?.get()
                    ?.addOnSuccessListener { snap ->
                        val foundUid = snap?.documents?.firstOrNull()?.id
                        if (!foundUid.isNullOrBlank()) {
                            writeMessageToCollection(foundUid, newMessage)
                            val fastRoomId = getChatRoomId(senderUid, foundUid)
                            writeMessageToCollection(fastRoomId, newMessage)
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

    // --- MESSAGE REQUEST & STRICT MUTUAL CONTACT PRIVACY METHODS ---

    fun setupFirestoreMessageRequestsListener(currentUid: String) {
        if (currentUid.isBlank()) return
        val sessionPrefs = context.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        val userPhone = sessionPrefs.getString("user_phone", null) ?: fallbackPrefs.getString("user_phone", "") ?: ""
        val userSuffix = PhoneUtils.extractPhoneSuffix(userPhone)

        try {
            messageRequestsListener?.remove()
            messageRequestsListener = firestore?.collection("message_requests")
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Message requests snapshot listener error: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = mutableListOf<MessageRequest>()
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val sId = doc.getString("senderId") ?: ""
                                val sPhone = doc.getString("senderPhone") ?: ""
                                val sSuffix = doc.getString("senderPhoneSuffix") ?: PhoneUtils.extractPhoneSuffix(sPhone)
                                val sName = doc.getString("senderName") ?: "Talkly User"
                                val sAvatar = doc.getString("senderAvatar") ?: ""

                                val rId = doc.getString("receiverId") ?: ""
                                val rPhone = doc.getString("receiverPhone") ?: ""
                                val rSuffix = doc.getString("receiverPhoneSuffix") ?: PhoneUtils.extractPhoneSuffix(rPhone)
                                val rName = doc.getString("receiverName") ?: "Talkly User"

                                val status = doc.getString("status") ?: "PENDING"
                                val initialMsg = doc.getString("initialMessage") ?: "Hello, I would like to connect on Talkly!"
                                val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()

                                val isForMe = rId == currentUid ||
                                        (rSuffix.isNotBlank() && rSuffix == userSuffix) ||
                                        (userPhone.isNotBlank() && rPhone == userPhone)

                                val isByMe = sId == currentUid ||
                                        (sSuffix.isNotBlank() && sSuffix == userSuffix) ||
                                        (userPhone.isNotBlank() && sPhone == userPhone)

                                if (isForMe || isByMe) {
                                    list.add(
                                        MessageRequest(
                                            id = id,
                                            senderId = sId,
                                            senderPhone = sPhone,
                                            senderPhoneSuffix = sSuffix,
                                            senderName = sName,
                                            senderAvatar = sAvatar,
                                            receiverId = rId,
                                            receiverPhone = rPhone,
                                            receiverPhoneSuffix = rSuffix,
                                            receiverName = rName,
                                            status = status,
                                            initialMessage = initialMsg,
                                            timestamp = ts
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing message request doc: ${e.localizedMessage}")
                            }
                        }
                        _messageRequests.value = list
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to setup message requests listener: ${e.localizedMessage}")
        }

        try {
            contactsSavedMeListener?.remove()
            contactsSavedMeListener = firestore?.collectionGroup("contacts")
                ?.addSnapshotListener { snap, _ ->
                    if (snap != null && !snap.isEmpty) {
                        val savedMeSet = mutableSetOf<String>()
                        for (doc in snap.documents) {
                            val cSuffix = doc.getString("phoneSuffix") ?: ""
                            val cPhone = doc.getString("phone") ?: ""
                            if ((userSuffix.isNotBlank() && cSuffix == userSuffix) || (userPhone.isNotBlank() && cPhone == userPhone)) {
                                val uploaderUid = doc.reference.parent.parent?.id ?: ""
                                if (uploaderUid.isNotBlank()) {
                                    savedMeSet.add(uploaderUid)
                                }
                            }
                        }
                        _contactsWhoSavedMe.value = savedMeSet
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to setup contactsSavedMeListener: ${e.localizedMessage}")
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
        val targetSuffix = PhoneUtils.extractPhoneSuffix(targetPhone)
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

        return iHaveTargetSaved && targetHasMeSaved
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

        val docId = "req_${senderSuffix.ifBlank { senderUid }}_${receiverSuffix.ifBlank { receiverUid }}"

        val requestMap = mapOf(
            "id" to docId,
            "senderId" to senderUid,
            "senderPhone" to senderPhone,
            "senderPhoneSuffix" to senderSuffix,
            "senderName" to senderName,
            "senderAvatar" to senderAvatar,
            "receiverId" to receiverUid,
            "receiverPhone" to receiverPhone,
            "receiverPhoneSuffix" to receiverSuffix,
            "receiverName" to targetMember.name,
            "status" to "PENDING",
            "initialMessage" to initialText,
            "timestamp" to System.currentTimeMillis()
        )

        try {
            firestore?.collection("message_requests")
                ?.document(docId)
                ?.set(requestMap, com.google.firebase.firestore.SetOptions.merge())
                ?.addOnSuccessListener {
                    onComplete?.invoke(true)
                }
                ?.addOnFailureListener {
                    onComplete?.invoke(false)
                }

            sendMessage(
                memberId = targetMember.id,
                textContent = "📩 Message Request: $initialText",
                type = MessageType.TEXT
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error sending message request: ${e.localizedMessage}")
            onComplete?.invoke(false)
        }
    }

    fun acceptMessageRequest(request: MessageRequest, onComplete: (() -> Unit)? = null) {
        val reqId = request.id
        try {
            firestore?.collection("message_requests")
                ?.document(reqId)
                ?.update("status", "ACCEPTED")
                ?.addOnSuccessListener {
                    Log.d(TAG, "Message request $reqId accepted")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error updating message request status: ${e.localizedMessage}")
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

            // Save contact mapping in User B's contacts collection: users/{currentUid}/contacts/{senderSuffix}
            if (currentUid.isNotBlank() && currentUid != "self") {
                try {
                    val docKey = if (senderSuffix.isNotBlank()) senderSuffix else contactId
                    firestore?.collection("users")?.document(currentUid)?.collection("contacts")?.document(docKey)
                        ?.set(
                            mapOf(
                                "uid" to (if (senderUid.isNotBlank() && !senderUid.startsWith("contact_")) senderUid else null),
                                "phoneSuffix" to senderSuffix,
                                "phone" to senderPhone,
                                "name" to realName,
                                "avatarUrl" to realAvatar,
                                "savedAt" to System.currentTimeMillis()
                            )
                        )
                } catch (e: Exception) {
                    Log.w(TAG, "Error saving contact to User B contacts: ${e.localizedMessage}")
                }
            }

            // Save reciprocal contact mapping in User A's contacts collection: users/{senderUid}/contacts/{mySuffix}
            if (senderUid.isNotBlank() && senderUid != "self" && !senderUid.startsWith("contact_")) {
                try {
                    val myDocKey = if (mySuffix.isNotBlank()) mySuffix else currentUid
                    firestore?.collection("users")?.document(senderUid)?.collection("contacts")?.document(myDocKey)
                        ?.set(
                            mapOf(
                                "uid" to currentUid,
                                "phoneSuffix" to mySuffix,
                                "phone" to myPhone,
                                "name" to myName,
                                "avatarUrl" to myAvatar,
                                "savedAt" to System.currentTimeMillis()
                            )
                        )
                } catch (e: Exception) {
                    Log.w(TAG, "Error saving reciprocal contact to User A contacts: ${e.localizedMessage}")
                }
            }

            onComplete?.invoke()
        }

        // Fetch target user's document directly from Firestore 'users' collection to read exact profile picture field
        if (senderUid.isNotBlank() && senderUid != "self" && !senderUid.startsWith("contact_")) {
            firestore?.collection("users")?.document(senderUid)?.get()?.addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val realAvatar = doc.getString("profilePicUrl")
                        ?: doc.getString("photoUrl")
                        ?: doc.getString("photoURL")
                        ?: doc.getString("avatarUrl")
                        ?: doc.getString("profilePic")
                        ?: request.senderAvatar.ifBlank { null }
                    val realName = doc.getString("name")?.ifBlank { null } ?: senderName
                    val realBio = doc.getString("bio")
                    applyContactRelationship(realName, realAvatar, realBio)
                } else {
                    applyContactRelationship(senderName, request.senderAvatar.ifBlank { null }, null)
                }
            }?.addOnFailureListener {
                applyContactRelationship(senderName, request.senderAvatar.ifBlank { null }, null)
            }
        } else {
            applyContactRelationship(senderName, request.senderAvatar.ifBlank { null }, null)
        }
    }

    fun declineMessageRequest(requestId: String, onComplete: (() -> Unit)? = null) {
        try {
            firestore?.collection("message_requests")
                ?.document(requestId)
                ?.update("status", "DECLINED")
        } catch (e: Exception) {
            Log.w(TAG, "Error declining message request: ${e.localizedMessage}")
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
