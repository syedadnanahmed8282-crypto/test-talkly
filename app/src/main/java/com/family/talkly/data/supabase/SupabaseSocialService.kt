package com.family.talkly.data.supabase

import android.content.Context
import android.util.Log
import com.family.talkly.data.models.FamilyMember
import com.family.talkly.data.models.StatusItem
import com.family.talkly.data.models.StatusLiker
import com.family.talkly.data.models.StatusViewer
import com.family.talkly.util.PhoneUtils
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Service managing Contacts, Online Presence (via Supabase Realtime Presence),
 * and 24-hour Stories/Statuses with Viewers & Likes.
 *
 * Adheres strictly to:
 * - No database polling / No heartbeat timers for presence.
 * - Indexed one-shot PostgREST queries with local cache.
 * - Realtime Presence for live online/offline tracking.
 */
class SupabaseSocialService(private val context: Context) {

    companion object {
        private const val TAG = "SupabaseSocialService"
        private const val TABLE_CONTACTS = "contacts"
        private const val TABLE_PROFILES = "profiles"
        private const val TABLE_STATUSES = "statuses"
        private const val TABLE_STATUS_VIEWERS = "status_viewers"
        private const val TABLE_STATUS_LIKES = "status_likes"
        private const val PRESENCE_CHANNEL_NAME = "presence:talkly_live"

        @Volatile
        private var instance: SupabaseSocialService? = null

        fun getInstance(context: Context): SupabaseSocialService {
            return instance ?: synchronized(this) {
                instance ?: SupabaseSocialService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val postgrest = SupabaseClientProvider.postgrest
    private val realtime = SupabaseClientProvider.realtime

    private var presenceChannel: RealtimeChannel? = null
    private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineUserIds = _onlineUserIds.asStateFlow()

    // ==========================================
    // 1. CONTACTS & USER DISCOVERY
    // ==========================================

    /**
     * Search a registered Talkly user in `profiles` by full phone number or phone suffix.
     */
    suspend fun searchUserByPhone(phoneQuery: String): Result<SupabaseProfile?> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = PhoneUtils.cleanPhoneNumber(phoneQuery)
            val suffix = PhoneUtils.extractPhoneSuffix(phoneQuery)

            if (suffix.isBlank() && cleanPhone.isBlank()) {
                return@withContext Result.success(null)
            }

            val profiles = postgrest.from(TABLE_PROFILES)
                .select {
                    filter {
                        if (cleanPhone.isNotBlank() && suffix.isNotBlank()) {
                            or {
                                eq("phone", cleanPhone)
                                eq("phone_suffix", suffix)
                            }
                        } else if (cleanPhone.isNotBlank()) {
                            eq("phone", cleanPhone)
                        } else {
                            eq("phone_suffix", suffix)
                        }
                    }
                    limit(1)
                }
                .decodeList<SupabaseProfile>()

            Result.success(profiles.firstOrNull())
        } catch (e: Exception) {
            Log.e(TAG, "searchUserByPhone failed for query '$phoneQuery': ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Load all saved contacts for a given user from `contacts` table.
     */
    suspend fun loadContacts(userId: String): Result<List<SupabaseContact>> = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank() || userId == "self") {
                return@withContext Result.success(emptyList())
            }

            val contacts = postgrest.from(TABLE_CONTACTS)
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order("is_pinned", Order.DESCENDING)
                    order("contact_name", Order.ASCENDING)
                }
                .decodeList<SupabaseContact>()

            Result.success(contacts)
        } catch (e: Exception) {
            Log.e(TAG, "loadContacts failed for user '$userId': ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Add or update a contact in the `contacts` table.
     */
    suspend fun saveContact(contact: SupabaseContact): Result<SupabaseContact> = withContext(Dispatchers.IO) {
        try {
            if (contact.userId.isBlank() || contact.userId == "self") {
                val err = IllegalArgumentException("Invalid owner userId: '${contact.userId}'")
                Log.e(TAG, "saveContact failed: ${err.message}")
                return@withContext Result.failure(err)
            }
            if (contact.contactPhone.isBlank()) {
                val err = IllegalArgumentException("Invalid contactPhone: phone cannot be blank")
                Log.e(TAG, "saveContact failed: ${err.message}")
                return@withContext Result.failure(err)
            }

            val contactToSave = contact.ensureValidForInsert()
            val phoneSuffix = contactToSave.contactPhoneSuffix.ifBlank { PhoneUtils.extractPhoneSuffix(contactToSave.contactPhone) }
            val finalContact = if (contactToSave.contactPhoneSuffix.isBlank() && phoneSuffix.isNotBlank()) {
                contactToSave.copy(contactPhoneSuffix = phoneSuffix)
            } else {
                contactToSave
            }

            Log.d(
                TAG,
                "saveContact: Persisting contact in '$TABLE_CONTACTS': userId=${finalContact.userId}, " +
                        "phone=${finalContact.contactPhone}, suffix=${finalContact.contactPhoneSuffix}, " +
                        "name=${finalContact.contactName}, candidateId=${finalContact.id}"
            )

            val result = postgrest.from(TABLE_CONTACTS)
                .upsert(finalContact) {
                    onConflict = "user_id,contact_phone"
                    select()
                }
                .decodeSingle<SupabaseContact>()

            Log.d(
                TAG,
                "saveContact: Successfully saved contact in Supabase: id=${result.id}, " +
                        "userId=${result.userId}, phone=${result.contactPhone}, suffix=${result.contactPhoneSuffix}, " +
                        "name=${result.contactName}"
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "saveContact failed for userId='${contact.userId}', phone='${contact.contactPhone}', " +
                        "suffix='${contact.contactPhoneSuffix}', name='${contact.contactName}': ${e.localizedMessage}",
                e
            )
            Result.failure(e)
        }
    }

    /**
     * Delete a contact by owner userId and contactPhoneSuffix.
     */
    suspend fun deleteContact(userId: String, contactPhoneSuffix: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank() || userId == "self" || contactPhoneSuffix.isBlank()) {
                return@withContext Result.success(Unit)
            }

            postgrest.from(TABLE_CONTACTS)
                .delete {
                    filter {
                        eq("user_id", userId)
                        eq("contact_phone_suffix", contactPhoneSuffix)
                    }
                }

            Log.d(TAG, "Deleted contact with suffix '$contactPhoneSuffix' for user '$userId'")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteContact failed: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Toggle pinned status of a contact in `contacts`.
     */
    suspend fun togglePinContact(userId: String, contactPhoneSuffix: String, isPinned: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank() || userId == "self" || contactPhoneSuffix.isBlank()) {
                return@withContext Result.success(Unit)
            }

            @kotlinx.serialization.Serializable
            data class PinUpdate(
                @kotlinx.serialization.SerialName("is_pinned")
                val isPinned: Boolean,
                @kotlinx.serialization.SerialName("updated_at")
                val updatedAt: String
            )

            val nowIso = SupabaseMessage.millisToIsoTimestamp(System.currentTimeMillis())
            postgrest.from(TABLE_CONTACTS)
                .update(PinUpdate(isPinned = isPinned, updatedAt = nowIso)) {
                    filter {
                        eq("user_id", userId)
                        eq("contact_phone_suffix", contactPhoneSuffix)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "togglePinContact failed: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Find list of phone suffixes who have saved the current user as a contact (mutual contact verification).
     */
    suspend fun loadContactsWhoSavedMe(myPhoneSuffix: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (myPhoneSuffix.isBlank()) return@withContext Result.success(emptyList())

            val contacts = postgrest.from(TABLE_CONTACTS)
                .select {
                    filter {
                        eq("contact_phone_suffix", myPhoneSuffix)
                    }
                }
                .decodeList<SupabaseContact>()

            val ownerUserIds = contacts.map { it.userId }.distinct()
            Result.success(ownerUserIds)
        } catch (e: Exception) {
            Log.w(TAG, "loadContactsWhoSavedMe warning: ${e.localizedMessage}")
            Result.success(emptyList())
        }
    }

    // ==========================================
    // 2. SUPABASE REALTIME PRESENCE (LIVE ONLINE / OFFLINE)
    // ==========================================

    private var lastRecordedTimestampUpdate: Long = 0L

    /**
     * Connect to the Supabase Realtime Presence channel and track local user state.
     * ZERO database polling or heartbeat loops are used.
     */
    suspend fun connectPresence(
        userId: String,
        userName: String,
        avatarUrl: String?
    ): Flow<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val status = realtime.status.value
            if (status != io.github.jan.supabase.realtime.Realtime.Status.CONNECTED &&
                status != io.github.jan.supabase.realtime.Realtime.Status.CONNECTING) {
                realtime.connect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Realtime connect note: ${e.localizedMessage}")
        }

        val channel = realtime.channel(PRESENCE_CHANNEL_NAME)
        presenceChannel = channel

        val presenceFlow = channel.presenceDataFlow<SupabasePresencePayload>()

        try {
            if (channel.status.value != io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
                channel.subscribe(blockUntilSubscribed = true)
            }

            if (userId.isNotBlank() && userId != "self") {
                val payload = SupabasePresencePayload(
                    userId = userId,
                    userName = userName,
                    avatarUrl = avatarUrl,
                    onlineAt = System.currentTimeMillis()
                )
                channel.track(payload)
                _onlineUserIds.value = _onlineUserIds.value + userId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to track initial presence: ${e.localizedMessage}")
        }

        flow {
            emit(_onlineUserIds.value)

            try {
                presenceFlow.collect { onlineList ->
                    val userIds = onlineList.map { it.userId }.filter { it.isNotBlank() }.toSet()
                    _onlineUserIds.value = userIds
                    emit(userIds)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Presence data flow ended: ${e.localizedMessage}")
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Periodically re-track presence on the existing channel to prevent presence lease expiry.
     * Reuses the existing presenceChannel instance without creating or re-subscribing a new channel.
     */
    suspend fun retrackPresence(
        userId: String,
        userName: String = "Talkly User",
        avatarUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank() || userId == "self") return@withContext
            val ch = presenceChannel ?: return@withContext
            if (ch.status.value == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
                val payload = SupabasePresencePayload(
                    userId = userId,
                    userName = userName,
                    avatarUrl = avatarUrl,
                    onlineAt = System.currentTimeMillis()
                )
                ch.track(payload)
                Log.d(TAG, "Presence re-tracked successfully for user: $userId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "retrackPresence warning: ${e.localizedMessage}")
        }
    }

    /**
     * Untrack and unsubscribe from the presence channel.
     */
    suspend fun disconnectPresence(userId: String) = withContext(Dispatchers.IO) {
        try {
            presenceChannel?.let { ch ->
                try { ch.untrack() } catch (e: Exception) {}
                try { ch.unsubscribe() } catch (e: Exception) {}
            }
            presenceChannel = null

            val current = _onlineUserIds.value.toMutableSet()
            current.remove(userId)
            _onlineUserIds.value = current

            // Update last_seen_at in profiles immediately upon disconnect
            if (userId.isNotBlank() && userId != "self") {
                updateLastSeenTimestamp(userId, force = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting presence: ${e.localizedMessage}")
        }
    }

    /**
     * Update `last_seen_at` on `profiles` upon disconnect / heartbeat.
     * Throttled with 15-second window (unless force = true) to keep database updated accurately.
     */
    suspend fun updateLastSeenTimestamp(userId: String, force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank() || userId == "self") return@withContext
            val now = System.currentTimeMillis()
            if (!force && (now - lastRecordedTimestampUpdate < 15_000L)) {
                return@withContext // 15s throttling protection
            }
            lastRecordedTimestampUpdate = now

            @kotlinx.serialization.Serializable
            data class LastSeenUpdate(
                @kotlinx.serialization.SerialName("last_seen_at")
                val lastSeenAt: String,
                @kotlinx.serialization.SerialName("updated_at")
                val updatedAt: String
            )

            val nowIso = SupabaseMessage.millisToIsoTimestamp(now)
            postgrest.from(TABLE_PROFILES)
                .update(LastSeenUpdate(lastSeenAt = nowIso, updatedAt = nowIso)) {
                    filter {
                        eq("id", userId)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "updateLastSeenTimestamp warning: ${e.localizedMessage}")
        }
    }

    // ==========================================
    // 3. 24-HOUR STORIES / STATUSES
    // ==========================================

    /**
     * Load active stories (created within the last 24 hours / expires_at > now).
     * Single one-shot PostgREST query on load/refresh without continuous DB listeners.
     */
    suspend fun loadActiveStatuses(): Result<List<StatusItem>> = withContext(Dispatchers.IO) {
        try {
            val nowIso = SupabaseMessage.millisToIsoTimestamp(System.currentTimeMillis())

            // 1. Fetch active statuses
            val statuses = postgrest.from(TABLE_STATUSES)
                .select {
                    filter {
                        gt("expires_at", nowIso)
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<SupabaseStatus>()

            if (statuses.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val statusIds = statuses.map { it.id }

            // 2. Fetch viewers for active statuses
            val viewers = try {
                postgrest.from(TABLE_STATUS_VIEWERS)
                    .select {
                        filter {
                            isIn("status_id", statusIds)
                        }
                    }
                    .decodeList<SupabaseStatusViewer>()
            } catch (e: Exception) {
                Log.w(TAG, "Warning loading status viewers: ${e.localizedMessage}")
                emptyList()
            }

            // 3. Fetch likes for active statuses
            val likes = try {
                postgrest.from(TABLE_STATUS_LIKES)
                    .select {
                        filter {
                            isIn("status_id", statusIds)
                        }
                    }
                    .decodeList<SupabaseStatusLike>()
            } catch (e: Exception) {
                Log.w(TAG, "Warning loading status likes: ${e.localizedMessage}")
                emptyList()
            }

            val viewersByStatus = viewers.groupBy { it.statusId }
            val likesByStatus = likes.groupBy { it.statusId }

            val statusItems = statuses.map { status ->
                val vList = viewersByStatus[status.id]?.map { it.toStatusViewer() } ?: emptyList()
                val lList = likesByStatus[status.id]?.map { it.toStatusLiker() } ?: emptyList()
                status.toStatusItem(viewers = vList, likes = lList)
            }

            Result.success(statusItems)
        } catch (e: Exception) {
            Log.e(TAG, "loadActiveStatuses failed: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Post a new 24-hour status story to Supabase.
     */
    suspend fun postStatus(status: SupabaseStatus): Result<SupabaseStatus> = withContext(Dispatchers.IO) {
        try {
            val result = postgrest.from(TABLE_STATUSES)
                .insert(status) {
                    select()
                }
                .decodeSingle<SupabaseStatus>()

            Log.d(TAG, "Status posted successfully: ${result.id}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "postStatus failed: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Record a viewer on a status story (if not already recorded).
     */
    suspend fun markStatusViewed(
        statusId: String,
        viewerUserId: String,
        viewerName: String,
        viewerAvatarUrl: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (viewerUserId.isBlank() || viewerUserId == "self") {
                return@withContext Result.success(Unit)
            }

            val existing = try {
                postgrest.from(TABLE_STATUS_VIEWERS)
                    .select {
                        filter {
                            eq("status_id", statusId)
                            eq("viewer_id", viewerUserId)
                        }
                        limit(1)
                    }
                    .decodeList<SupabaseStatusViewer>()
            } catch (_: Exception) {
                emptyList()
            }

            if (existing.isEmpty()) {
                val viewer = SupabaseStatusViewer(
                    id = UUID.randomUUID().toString(),
                    statusId = statusId,
                    viewerId = viewerUserId,
                    viewerName = viewerName,
                    viewerAvatarUrl = viewerAvatarUrl,
                    viewedAt = SupabaseMessage.millisToIsoTimestamp(
                        System.currentTimeMillis()
                    )
                )

                postgrest.from(TABLE_STATUS_VIEWERS)
                    .insert(viewer)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(
                TAG,
                "markStatusViewed failed for status=$statusId, viewer=$viewerUserId: ${e.localizedMessage}"
            )
            Result.failure(e)
        }
    }

    /**
     * Toggle like on a status story. Returns true if now liked, false if unliked.
     */
    suspend fun toggleStatusLike(
        statusId: String,
        userId: String,
        userName: String,
        userAvatarUrl: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank() || userId == "self") {
                return@withContext Result.success(false)
            }

            val existing = postgrest.from(TABLE_STATUS_LIKES)
                .select {
                    filter {
                        eq("status_id", statusId)
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeList<SupabaseStatusLike>()

            if (existing.isNotEmpty()) {
                postgrest.from(TABLE_STATUS_LIKES)
                    .delete {
                        filter {
                            eq("status_id", statusId)
                            eq("user_id", userId)
                        }
                    }

                Result.success(false)
            } else {
                val newLike = SupabaseStatusLike(
                    id = UUID.randomUUID().toString(),
                    statusId = statusId,
                    userId = userId,
                    userName = userName,
                    userAvatarUrl = userAvatarUrl,
                    createdAt = SupabaseMessage.millisToIsoTimestamp(
                        System.currentTimeMillis()
                    )
                )

                postgrest.from(TABLE_STATUS_LIKES)
                    .insert(newLike)

                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "toggleStatusLike failed for status=$statusId, user=$userId: ${e.localizedMessage}"
            )
            Result.failure(e)
        }
    }

    /**
     * Delete a status story by statusId and owner userId.
     */
    suspend fun deleteStatus(statusId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            try {
                postgrest.from(TABLE_STATUS_VIEWERS).delete {
                    filter {
                        eq("status_id", statusId)
                    }
                }
            } catch (_: Exception) {}

            try {
                postgrest.from(TABLE_STATUS_LIKES).delete {
                    filter {
                        eq("status_id", statusId)
                    }
                }
            } catch (_: Exception) {}

            postgrest.from(TABLE_STATUSES)
                .delete {
                    filter {
                        eq("id", statusId)
                        eq("user_id", userId)
                    }
                }
            Log.d(TAG, "Status $statusId deleted successfully from Supabase by $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteStatus failed: ${e.localizedMessage}")
            Result.failure(e)
        }
    }
}
