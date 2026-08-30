package com.family.talkly.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.family.talkly.data.models.UserProfile
import com.family.talkly.data.supabase.SupabaseClientProvider
import com.family.talkly.data.supabase.SupabaseProfile
import com.family.talkly.util.MediaCompressorAndUploader
import com.family.talkly.util.PhoneUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class AuthState {
    object InitialCheck : AuthState()
    object Unauthenticated : AuthState()
    data class VerificationInProgress(val message: String = "Authenticating...") : AuthState()
    data class ProfileSetupRequired(val uid: String, val phoneNumber: String) : AuthState()
    data class Authenticated(val profile: UserProfile) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "talkly_auth_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_UID = "user_uid"
        private const val KEY_NAME = "user_name"
        private const val KEY_PHONE = "user_phone"
        private const val KEY_PROFILE_PIC = "user_profile_pic"
        private const val KEY_COVER_PHOTO = "user_cover_photo"
        private const val KEY_BIO = "user_bio"

        /**
         * Converts phone number into a deterministic internal email address for Supabase Auth
         */
        fun getInternalEmail(phoneNumber: String): String {
            val cleanNumber = PhoneUtils.cleanPhoneNumber(phoneNumber).ifBlank { phoneNumber.replace("+", "").trim() }
            return "${cleanNumber}@talkly.app"
        }
    }

    private val auth = SupabaseClientProvider.client.auth
    private val postgrest = SupabaseClientProvider.client.postgrest
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var isLoggingOut = false

    private val _authState = MutableStateFlow<AuthState>(AuthState.InitialCheck)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkCurrentSession()
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Checks local session and Supabase Auth session to resume user session
     */
    fun checkCurrentSession() {
        if (isLoggingOut) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
                val savedUid = prefs.getString(KEY_UID, null)

                // If user was previously logged in, ensure Supabase Auth finishes restoring/refreshing session from storage
                if (isLoggedIn || !savedUid.isNullOrEmpty()) {
                    try {
                        withTimeoutOrNull(3000L) {
                            auth.sessionStatus.first { it !is SessionStatus.Initializing }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Auth session restoration wait note: ${e.message}")
                    }

                    // Ensure token is refreshed if session exists
                    try {
                        val currentSession = auth.currentSessionOrNull()
                        if (currentSession != null) {
                            auth.refreshCurrentSession()
                            Log.d(TAG, "Supabase Auth session refreshed successfully")
                        }
                    } catch (refreshEx: Exception) {
                        Log.w(TAG, "Supabase Auth session refresh note: ${refreshEx.localizedMessage}")
                    }
                }

                val session = auth.currentSessionOrNull()
                val user = auth.currentUserOrNull()

                if (user != null || (session != null && !savedUid.isNullOrEmpty()) || (isLoggedIn && !savedUid.isNullOrEmpty())) {
                    val effectiveUid = user?.id ?: savedUid ?: ""
                    val cachedName = prefs.getString(KEY_NAME, "") ?: ""
                    val cachedPhone = prefs.getString(KEY_PHONE, "") ?: (user?.phone ?: "")
                    var cachedPic = prefs.getString(KEY_PROFILE_PIC, "") ?: ""
                    val cachedCover = prefs.getString(KEY_COVER_PHOTO, "") ?: ""
                    val cachedBio = prefs.getString(KEY_BIO, "Available on Talkly 💬") ?: "Available on Talkly 💬"

                    // Check if pic is a content:// URI and convert to persistent internal avatar file if available
                    if (cachedPic.startsWith("content://") || cachedPic.isBlank()) {
                        val avatarDir = File(context.filesDir, "profile_avatars")
                        val internalFile = File(avatarDir, "avatar_${effectiveUid}.jpg")
                        if (internalFile.exists()) {
                            cachedPic = Uri.fromFile(internalFile).toString()
                        }
                    }

                    if (cachedName.isNotBlank() && effectiveUid.isNotBlank()) {
                        val cachedProfile = UserProfile(
                            uid = effectiveUid,
                            name = cachedName,
                            phoneNumber = cachedPhone,
                            phoneSuffix = PhoneUtils.extractPhoneSuffix(cachedPhone),
                            profilePicUrl = cachedPic,
                            coverPhotoUrl = cachedCover,
                            bio = cachedBio
                        )
                        withContext(Dispatchers.Main) {
                            _authState.value = AuthState.Authenticated(cachedProfile)
                        }
                    } else if (effectiveUid.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            _authState.value = AuthState.ProfileSetupRequired(effectiveUid, cachedPhone)
                        }
                    }

                    // Sync latest profile from Supabase in background
                    syncProfileFromSupabase(effectiveUid, cachedPhone)
                } else {
                    withContext(Dispatchers.Main) {
                        _authState.value = AuthState.Unauthenticated
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Supabase session: ${e.message}")
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        }
    }

    /**
     * Registers a new user with Mobile Phone Number and Password via Supabase Auth
     */
    fun signUpWithPhoneAndPassword(
        phoneNumber: String,
        password: String,
        name: String,
        profilePicUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        isLoggingOut = false
        if (phoneNumber.isBlank() || password.isBlank() || name.isBlank()) {
            val err = "Please enter your name, phone number, and password."
            _authState.value = AuthState.Error(err)
            onError(err)
            return
        }

        if (password.length < 6) {
            val err = "Password must be at least 6 characters long."
            _authState.value = AuthState.Error(err)
            onError(err)
            return
        }

        val internalEmail = getInternalEmail(phoneNumber)
        _authState.value = AuthState.VerificationInProgress("Creating user account...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                auth.signUpWith(Email) {
                    this.email = internalEmail
                    this.password = password
                    data = buildJsonObject {
                        put("phone", phoneNumber)
                        put("name", name)
                        put("avatar_url", profilePicUrl)
                        put("bio", "Available on Talkly 💬")
                    }
                }

                val currentUser = auth.currentUserOrNull() ?: auth.currentSessionOrNull()?.user
                val uid = currentUser?.id

                if (uid != null) {
                    Log.d(TAG, "Supabase registration successful for phone $phoneNumber ($internalEmail), UID: $uid")
                    saveUserProfileAndAuthenticate(uid, name, phoneNumber, profilePicUrl, onSuccess, onError)
                } else {
                    // In some Supabase configs, sign up without auto-login requires immediate sign in
                    try {
                        auth.signInWith(Email) {
                            this.email = internalEmail
                            this.password = password
                        }
                        val signedInUid = auth.currentUserOrNull()?.id ?: ""
                        if (signedInUid.isNotBlank()) {
                            saveUserProfileAndAuthenticate(signedInUid, name, phoneNumber, profilePicUrl, onSuccess, onError)
                        } else {
                            val err = "Account created. Please sign in."
                            withContext(Dispatchers.Main) {
                                _authState.value = AuthState.Unauthenticated
                                onError(err)
                            }
                        }
                    } catch (signInEx: Exception) {
                        val err = "Account registered successfully. Please sign in."
                        withContext(Dispatchers.Main) {
                            _authState.value = AuthState.Unauthenticated
                            onError(err)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase Sign up exception: ${e.localizedMessage}", e)
                val rawErr = e.localizedMessage ?: "Registration failed."
                val formatted = when {
                    rawErr.contains("already registered", ignoreCase = true) ||
                    rawErr.contains("already in use", ignoreCase = true) ||
                    rawErr.contains("user_already_exists", ignoreCase = true) ->
                        "An account with this phone number already exists. Please sign in instead."
                    rawErr.contains("invalid email", ignoreCase = true) ||
                    rawErr.contains("invalid-email", ignoreCase = true) ->
                        "Invalid mobile phone number format."
                    rawErr.contains("weak-password", ignoreCase = true) ||
                    (rawErr.contains("password", ignoreCase = true) && rawErr.contains("weak", ignoreCase = true)) ->
                        "Password is too weak. Please use at least 6 characters."
                    else -> rawErr
                }
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Error(formatted)
                    onError(formatted)
                }
            }
        }
    }

    /**
     * Signs in an existing user with Mobile Phone Number and Password via Supabase Auth
     */
    fun signInWithPhoneAndPassword(
        phoneNumber: String,
        password: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        isLoggingOut = false
        if (phoneNumber.isBlank() || password.isBlank()) {
            val err = "Please enter both mobile phone number and password."
            _authState.value = AuthState.Error(err)
            onError(err)
            return
        }

        val internalEmail = getInternalEmail(phoneNumber)
        _authState.value = AuthState.VerificationInProgress("Signing in...")

        CoroutineScope(Dispatchers.IO).launch {
            var attempt = 0
            val maxAttempts = 2
            var lastException: Exception? = null

            while (attempt < maxAttempts) {
                attempt++
                try {
                    auth.signInWith(Email) {
                        this.email = internalEmail
                        this.password = password
                    }

                    val user = auth.currentUserOrNull() ?: auth.currentSessionOrNull()?.user
                    val uid = user?.id

                    if (uid != null) {
                        Log.d(TAG, "Supabase sign in successful for phone $phoneNumber ($internalEmail), UID: $uid")
                        syncProfileFromSupabase(uid, phoneNumber, onComplete = {
                            CoroutineScope(Dispatchers.Main).launch {
                                onSuccess()
                            }
                        })
                        return@launch
                    } else {
                        val err = "Authentication succeeded but user session is null."
                        withContext(Dispatchers.Main) {
                            _authState.value = AuthState.Error(err)
                            onError(err)
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Supabase Sign in attempt $attempt failed: ${e.localizedMessage}")
                    val raw = e.localizedMessage ?: ""
                    val isTimeoutOrNetwork = raw.contains("timeout", ignoreCase = true) ||
                        raw.contains("connect", ignoreCase = true) ||
                        raw.contains("network", ignoreCase = true) ||
                        raw.contains("Unable to resolve host", ignoreCase = true)

                    if (isTimeoutOrNetwork && attempt < maxAttempts) {
                        withContext(Dispatchers.Main) {
                            _authState.value = AuthState.VerificationInProgress("Connecting to server...")
                        }
                        kotlinx.coroutines.delay(1000)
                        continue
                    }
                    break
                }
            }

            val e = lastException ?: Exception("Authentication failed")
            Log.e(TAG, "Supabase Sign in exception: ${e.localizedMessage}", e)
            val rawErr = e.localizedMessage ?: "Authentication failed."
            val formatted = when {
                rawErr.contains("invalid login credentials", ignoreCase = true) ||
                rawErr.contains("invalid_credentials", ignoreCase = true) ||
                rawErr.contains("invalid_grant", ignoreCase = true) ||
                rawErr.contains("invalid-credential", ignoreCase = true) ||
                rawErr.contains("wrong-password", ignoreCase = true) ||
                rawErr.contains("invalid password", ignoreCase = true) ->
                    "Incorrect mobile number or password. If you do not have an account, please switch to Register."
                rawErr.contains("user not found", ignoreCase = true) ||
                rawErr.contains("user-not-found", ignoreCase = true) ->
                    "No account found with this phone number. Please register first."
                rawErr.contains("network", ignoreCase = true) ||
                rawErr.contains("timeout", ignoreCase = true) ||
                rawErr.contains("connect", ignoreCase = true) ||
                rawErr.contains("Unable to resolve host", ignoreCase = true) ->
                    "Connection timed out. Please check your data speed and try again."
                else -> rawErr
            }
            withContext(Dispatchers.Main) {
                _authState.value = AuthState.Error(formatted)
                onError(formatted)
            }
        }
    }

    /**
     * Triggers Supabase password reset using mapped internal email address for phone number
     */
    fun sendPasswordResetForPhone(
        phoneNumber: String,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (phoneNumber.isBlank()) {
            val err = "Please enter your mobile phone number."
            onError(err)
            return
        }

        val internalEmail = getInternalEmail(phoneNumber)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                auth.resetPasswordForEmail(internalEmail)
                Log.d(TAG, "Password reset email sent to internal email: $internalEmail for phone $phoneNumber")
                withContext(Dispatchers.Main) {
                    onSuccess("Password reset instructions sent for account linked to $phoneNumber.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase Password reset exception: ${e.localizedMessage}", e)
                val rawErr = e.localizedMessage ?: "Password reset failed."
                val formatted = when {
                    rawErr.contains("user not found", ignoreCase = true) ||
                    rawErr.contains("user-not-found", ignoreCase = true) ->
                        "No registered account found with mobile number $phoneNumber."
                    rawErr.contains("invalid-email", ignoreCase = true) ||
                    rawErr.contains("invalid email", ignoreCase = true) ->
                        "Invalid phone number format."
                    else -> rawErr
                }
                withContext(Dispatchers.Main) {
                    onError(formatted)
                }
            }
        }
    }

    /**
     * Synchronizes user profile from Supabase public.profiles table
     */
    private fun syncProfileFromSupabase(
        uid: String,
        fallbackPhone: String,
        onComplete: ((UserProfile) -> Unit)? = null
    ) {
        if (isLoggingOut || uid.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileDto = postgrest.from("profiles")
                    .select { filter { eq("id", uid) } }
                    .decodeSingleOrNull<SupabaseProfile>()

                if (profileDto != null && profileDto.name.isNotBlank()) {
                    val phone = if (profileDto.phone.isNotBlank()) profileDto.phone else fallbackPhone
                    val suffix = if (profileDto.phoneSuffix.isNotBlank()) profileDto.phoneSuffix else PhoneUtils.extractPhoneSuffix(phone)
                    val localSavedPic = prefs.getString(KEY_PROFILE_PIC, "") ?: ""
                    val effectivePic = if (profileDto.avatarUrl.isNotBlank()) {
                        profileDto.avatarUrl
                    } else if (localSavedPic.isNotBlank() && !localSavedPic.startsWith("content://")) {
                        localSavedPic
                    } else {
                        ""
                    }
                    val localSavedCover = prefs.getString(KEY_COVER_PHOTO, "") ?: ""
                    val effectiveCover = if (profileDto.coverPhotoUrl.isNotBlank()) {
                        profileDto.coverPhotoUrl
                    } else if (localSavedCover.isNotBlank() && !localSavedCover.startsWith("content://")) {
                        localSavedCover
                    } else {
                        ""
                    }

                    val profile = UserProfile(
                        uid = uid,
                        name = profileDto.name,
                        phoneNumber = phone,
                        phoneSuffix = suffix,
                        profilePicUrl = effectivePic,
                        coverPhotoUrl = effectiveCover,
                        bio = profileDto.bio.ifBlank { "Available on Talkly 💬" }
                    )

                    saveLocalSession(uid, profile.name, profile.phoneNumber, profile.profilePicUrl, profile.bio, profile.coverPhotoUrl)

                    withContext(Dispatchers.Main) {
                        _authState.value = AuthState.Authenticated(profile)
                        onComplete?.invoke(profile)
                    }
                } else {
                    handleMissingProfile(uid, fallbackPhone, onComplete)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching profile from Supabase for $uid: ${e.localizedMessage}")
                handleMissingProfile(uid, fallbackPhone, onComplete)
            }
        }
    }

    private suspend fun handleMissingProfile(
        uid: String,
        fallbackPhone: String,
        onComplete: ((UserProfile) -> Unit)? = null
    ) {
        val localName = prefs.getString(KEY_NAME, "") ?: ""
        if (localName.isNotBlank()) {
            val phone = prefs.getString(KEY_PHONE, fallbackPhone) ?: fallbackPhone
            val pic = prefs.getString(KEY_PROFILE_PIC, "") ?: ""
            val cover = prefs.getString(KEY_COVER_PHOTO, "") ?: ""
            val bio = prefs.getString(KEY_BIO, "Available on Talkly 💬") ?: "Available on Talkly 💬"
            val suffix = PhoneUtils.extractPhoneSuffix(phone)

            val profile = UserProfile(
                uid = uid,
                name = localName,
                phoneNumber = phone,
                phoneSuffix = suffix,
                profilePicUrl = pic,
                coverPhotoUrl = cover,
                bio = bio
            )

            // Auto-provision or update in Supabase profiles
            try {
                val profileJson = buildJsonObject {
                    put("id", uid)
                    put("name", localName)
                    put("phone", phone)
                    put("phone_suffix", suffix)
                    put("avatar_url", pic)
                    put("cover_photo_url", cover)
                    put("bio", bio)
                }
                postgrest.from("profiles").upsert(profileJson)
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing local profile to Supabase: ${e.localizedMessage}")
            }

            saveLocalSession(uid, localName, phone, pic, bio, cover)
            withContext(Dispatchers.Main) {
                _authState.value = AuthState.Authenticated(profile)
                onComplete?.invoke(profile)
            }
        } else {
            saveLocalSession(uid, "", fallbackPhone, "", "")
            withContext(Dispatchers.Main) {
                _authState.value = AuthState.ProfileSetupRequired(uid, fallbackPhone)
            }
        }
    }

    private fun getProfileAvatarFile(key: String): File {
        val avatarDir = File(context.filesDir, "profile_avatars").apply { mkdirs() }
        return File(avatarDir, "avatar_${key}.jpg")
    }

    private fun processProfileAvatarImage(uid: String, rawProfilePicUrl: String): Pair<String, String> {
        if (rawProfilePicUrl.isBlank()) {
            return Pair("", "")
        }
        if (rawProfilePicUrl.startsWith("http://") || rawProfilePicUrl.startsWith("https://") || rawProfilePicUrl.startsWith("data:image")) {
            return Pair(rawProfilePicUrl, rawProfilePicUrl)
        }

        if (rawProfilePicUrl.startsWith("content://") || rawProfilePicUrl.startsWith("file://")) {
            try {
                val uri = Uri.parse(rawProfilePicUrl)
                val destFile = getProfileAvatarFile(uid)

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri), null, options)

                    val maxDim = maxOf(options.outWidth, options.outHeight)
                    var sampleSize = 1
                    while (maxDim / sampleSize > 300) { sampleSize *= 2 }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                    inputStream.close()

                    if (bitmap != null) {
                        val width = bitmap.width
                        val height = bitmap.height
                        val mDim = maxOf(width, height)
                        val scaledBitmap = if (mDim > 300) {
                            val scale = 300f / mDim.toFloat()
                            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
                        } else {
                            bitmap
                        }

                        val outStream = FileOutputStream(destFile)
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
                        outStream.flush()
                        outStream.close()

                        if (scaledBitmap != bitmap) {
                            bitmap.recycle()
                        }
                        bitmap.recycle()

                        val bytes = destFile.readBytes()
                        val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val base64DataUrl = "data:image/jpeg;base64,$base64Str"
                        val localUri = Uri.fromFile(destFile).toString()

                        return Pair(localUri, base64DataUrl)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed processing avatar image: ${e.localizedMessage}")
            }
        }
        return Pair(rawProfilePicUrl, rawProfilePicUrl)
    }

    private fun saveUserProfileAndAuthenticate(
        uid: String,
        name: String,
        phoneNumber: String,
        profilePicUrl: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val bio = "Available on Talkly 💬"
        val phoneSuffix = PhoneUtils.extractPhoneSuffix(phoneNumber)
        val (localPicUrl, cloudPicUrl) = processProfileAvatarImage(uid, profilePicUrl)

        saveLocalSession(uid, name, phoneNumber, localPicUrl, bio)
        val profile = UserProfile(
            uid = uid,
            name = name,
            phoneNumber = phoneNumber,
            phoneSuffix = phoneSuffix,
            profilePicUrl = localPicUrl,
            bio = bio
        )

        CoroutineScope(Dispatchers.IO).launch {
            var remoteAvatarUrl = if (!profilePicUrl.startsWith("content://") && !profilePicUrl.startsWith("file://")) profilePicUrl else ""
            val localPicFile = getProfileAvatarFile(uid)
            if (localPicFile.exists()) {
                try {
                    val uploader = MediaCompressorAndUploader(context)
                    val uploadedUrl = uploader.uploadMediaFile(localPicFile, "avatars/${uid}_avatar.jpg") { _, _ -> }
                    if (uploadedUrl.startsWith("http://") || uploadedUrl.startsWith("https://")) {
                        remoteAvatarUrl = uploadedUrl
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed uploading registration avatar to Cloudinary: ${e.localizedMessage}")
                }
            }

            try {
                val profileJson = buildJsonObject {
                    put("id", uid)
                    put("name", name)
                    put("phone", phoneNumber)
                    put("phone_suffix", phoneSuffix)
                    if (remoteAvatarUrl.isNotBlank()) {
                        put("avatar_url", remoteAvatarUrl)
                    } else if (cloudPicUrl.isNotBlank()) {
                        put("avatar_url", cloudPicUrl)
                    }
                    put("bio", bio)
                }
                postgrest.from("profiles").upsert(profileJson)
            } catch (e: Exception) {
                Log.w(TAG, "Error saving profile to Supabase during registration: ${e.localizedMessage}")
            }

            withContext(Dispatchers.Main) {
                _authState.value = AuthState.Authenticated(profile)
                onSuccess()
            }
        }
    }

    /**
     * Saves name, bio, cover photo and profile picture to Supabase profiles and local session
     */
    fun saveUserProfile(
        name: String,
        profilePicUrl: String,
        bio: String = "Available on Talkly 💬",
        coverPhotoUrl: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentState = _authState.value
        var uid = ""
        var phone = ""

        if (currentState is AuthState.ProfileSetupRequired) {
            uid = currentState.uid
            phone = currentState.phoneNumber
        } else {
            uid = prefs.getString(KEY_UID, "") ?: (auth.currentUserOrNull()?.id ?: "")
            phone = prefs.getString(KEY_PHONE, "") ?: ""
        }

        if (uid.isBlank()) {
            val err = "User session invalid. Please sign in again."
            _authState.value = AuthState.Error(err)
            onError(err)
            return
        }

        val phoneSuffix = PhoneUtils.extractPhoneSuffix(phone)

        CoroutineScope(Dispatchers.IO).launch {
            var localPicUrl = profilePicUrl
            var localCoverUrl = coverPhotoUrl
            var localPicFile: File? = null
            var localCoverFile: File? = null

            if (profilePicUrl.startsWith("content://") || profilePicUrl.startsWith("file://")) {
                val (localPic, processedPic) = processProfileAvatarImage(uid, profilePicUrl)
                localPicUrl = if (localPic.isNotBlank()) localPic else processedPic
                localPicFile = getProfileAvatarFile(uid)
            }

            if (coverPhotoUrl.startsWith("content://") || coverPhotoUrl.startsWith("file://")) {
                val (localCover, processedCover) = processProfileAvatarImage("${uid}_cover", coverPhotoUrl)
                localCoverUrl = if (localCover.isNotBlank()) localCover else processedCover
                localCoverFile = getProfileAvatarFile("${uid}_cover")
            }

            saveLocalSession(uid, name, phone, localPicUrl, bio, localCoverUrl)

            // Force clear Coil image memory & disk caches to prevent stale image rendering
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing Coil image cache: ${e.localizedMessage}")
            }

            val profile = UserProfile(
                uid = uid,
                name = name,
                phoneNumber = phone,
                phoneSuffix = phoneSuffix,
                profilePicUrl = localPicUrl,
                coverPhotoUrl = localCoverUrl,
                bio = bio
            )

            withContext(Dispatchers.Main) {
                _authState.value = AuthState.Authenticated(profile)
                onSuccess()
            }

            // Asynchronously upload to Cloudinary so remote users get valid web URLs
            var remoteAvatarUrl = if (!profilePicUrl.startsWith("content://") && !profilePicUrl.startsWith("file://")) profilePicUrl else ""
            var remoteCoverUrl = if (!coverPhotoUrl.startsWith("content://") && !coverPhotoUrl.startsWith("file://")) coverPhotoUrl else ""

            val uploader = MediaCompressorAndUploader(context)
            if (localPicFile != null && localPicFile.exists()) {
                try {
                    val uploadedUrl = uploader.uploadMediaFile(localPicFile, "avatars/${uid}_avatar.jpg") { _, _ -> }
                    if (uploadedUrl.startsWith("http://") || uploadedUrl.startsWith("https://")) {
                        remoteAvatarUrl = uploadedUrl
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed uploading avatar to Cloudinary: ${e.localizedMessage}")
                }
            }

            if (localCoverFile != null && localCoverFile.exists()) {
                try {
                    val uploadedUrl = uploader.uploadMediaFile(localCoverFile, "covers/${uid}_cover.jpg") { _, _ -> }
                    if (uploadedUrl.startsWith("http://") || uploadedUrl.startsWith("https://")) {
                        remoteCoverUrl = uploadedUrl
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed uploading cover to Cloudinary: ${e.localizedMessage}")
                }
            }

            try {
                val updateJson = buildJsonObject {
                    put("id", uid)
                    put("name", name)
                    put("phone", phone)
                    put("phone_suffix", phoneSuffix)
                    if (remoteAvatarUrl.isNotBlank()) {
                        put("avatar_url", remoteAvatarUrl)
                    }
                    if (remoteCoverUrl.isNotBlank()) {
                        put("cover_photo_url", remoteCoverUrl)
                    }
                    put("bio", bio)
                }
                postgrest.from("profiles").upsert(updateJson)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating Supabase profile: ${e.localizedMessage}")
            }
        }
    }

    private fun saveLocalSession(uid: String, name: String, phone: String, pic: String, bio: String = "Available on Talkly 💬", coverPic: String = "") {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_UID, uid)
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phone)
            .putString(KEY_PROFILE_PIC, pic)
            .putString(KEY_COVER_PHOTO, coverPic)
            .putString(KEY_BIO, bio)
            .apply()

        try {
            context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE).edit()
                .putString("user_uid", uid)
                .putString("user_name", name)
                .putString("user_phone", phone)
                .putString("user_profile_pic", pic)
                .putString("user_cover_photo", coverPic)
                .putString("user_bio", bio)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing talkly_user_session prefs: ${e.message}")
        }
    }

    fun logout() {
        isLoggingOut = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.family.talkly.util.FcmTokenManager.unregisterToken(context)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering FCM token during logout: ${e.localizedMessage}")
            }

            try {
                com.family.talkly.data.local.TalklyDatabase.getInstance(context).chatMessageDao().clearAllMessages()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing Room database during logout: ${e.localizedMessage}")
            }

            try {
                auth.signOut()
            } catch (e: Exception) {
                Log.w(TAG, "Supabase signOut exception: ${e.localizedMessage}")
            }
        }

        val prefNames = listOf(
            PREFS_NAME,
            "talkly_user_session",
            "talkly_saved_contacts_prefs",
            "talkly_theme_prefs",
            "talkly_fcm_prefs",
            "talkly_call_prefs"
        )
        prefNames.forEach { pName ->
            try {
                context.getSharedPreferences(pName, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing prefs $pName: ${e.message}")
            }
        }

        _authState.value = AuthState.Unauthenticated
    }
}
