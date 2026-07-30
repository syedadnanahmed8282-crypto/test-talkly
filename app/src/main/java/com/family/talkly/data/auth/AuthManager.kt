package com.family.talkly.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.family.talkly.data.models.UserProfile
import com.family.talkly.util.MediaCompressorAndUploader
import com.family.talkly.util.PhoneUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        private const val KEY_BIO = "user_bio"

        /**
         * Converts phone number into a deterministic internal email address for Firebase Auth
         */
        fun getInternalEmail(phoneNumber: String): String {
            val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "").trim()
            return "${cleanNumber}@talkly.app"
        }
    }

    private fun ensureFirebase() {
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            try {
                com.google.firebase.FirebaseApp.initializeApp(context)
            } catch (e: Exception) {
                try {
                    val firebaseApiKey = try {
                        val key = com.family.talkly.BuildConfig.FIREBASE_API_KEY
                        if (key.isNullOrBlank()) "AIzaSyCmmYWBqRREKmhNaBvc1drcTJib0EuMgF0" else key
                    } catch (e: Exception) {
                        "AIzaSyCmmYWBqRREKmhNaBvc1drcTJib0EuMgF0"
                    }
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:688875089801:android:07f27e3cf40ca2af913b58")
                        .setGcmSenderId("688875089801")
                        .setProjectId("familycallapp-e6b21")
                        .setApiKey(firebaseApiKey)
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(context, options)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed fallback Firebase init in AuthManager: ${ex.message}")
                }
            }
        }
    }

    private fun getFirebaseAuth(): FirebaseAuth {
        ensureFirebase()
        return FirebaseAuth.getInstance()
    }

    private fun getFirestore(): FirebaseFirestore {
        ensureFirebase()
        return FirebaseFirestore.getInstance()
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
     * Checks local session and Firebase Auth current user to resume session
     */
    fun checkCurrentSession() {
        try {
            val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
            val savedUid = prefs.getString(KEY_UID, null)
            val firebaseUser = try { getFirebaseAuth().currentUser } catch (e: Exception) { null }

            if (isLoggedIn && !savedUid.isNullOrEmpty()) {
                val name = prefs.getString(KEY_NAME, "") ?: ""
                val phone = prefs.getString(KEY_PHONE, "") ?: ""
                var pic = prefs.getString(KEY_PROFILE_PIC, "") ?: ""
                val bio = prefs.getString(KEY_BIO, "Available on Talkly 💬") ?: "Available on Talkly 💬"

                // Check if pic is a content:// URI and convert to persistent internal avatar file if available
                if (pic.startsWith("content://") || pic.isBlank()) {
                    val avatarDir = File(context.filesDir, "profile_avatars")
                    val internalFile = File(avatarDir, "avatar_${savedUid}.jpg")
                    if (internalFile.exists()) {
                        pic = Uri.fromFile(internalFile).toString()
                    }
                }

                if (name.isNotBlank()) {
                    val profile = UserProfile(
                        uid = savedUid,
                        name = name,
                        phoneNumber = phone,
                        profilePicUrl = pic,
                        bio = bio
                    )
                    _authState.value = AuthState.Authenticated(profile)
                } else {
                    _authState.value = AuthState.ProfileSetupRequired(savedUid, phone)
                }

                // Always sync latest user profile from Firestore in background
                checkUserProfileInFirestore(savedUid, phone)
            } else if (firebaseUser != null) {
                val uid = firebaseUser.uid
                val phone = firebaseUser.phoneNumber ?: prefs.getString(KEY_PHONE, "") ?: ""
                checkUserProfileInFirestore(uid, phone)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking current session: ${e.message}")
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Registers a new user with Mobile Phone Number and Password
     */
    fun signUpWithPhoneAndPassword(
        phoneNumber: String,
        password: String,
        name: String,
        profilePicUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300&auto=format&fit=crop",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
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

        try {
            getFirebaseAuth().createUserWithEmailAndPassword(internalEmail, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        val uid = user?.uid
                        if (uid != null) {
                            Log.d(TAG, "Registration successful for phone $phoneNumber ($internalEmail), UID: $uid")
                            saveUserProfileAndAuthenticate(uid, name, phoneNumber, profilePicUrl, onSuccess, onError)
                        } else {
                            val err = "Account created, but user session was null."
                            _authState.value = AuthState.Error(err)
                            onError(err)
                        }
                    } else {
                        val rawErr = task.exception?.message ?: "Registration failed."
                        val formatted = when {
                            rawErr.contains("already in use", ignoreCase = true) ||
                            rawErr.contains("email-already-in-use", ignoreCase = true) ->
                                "An account with this phone number already exists. Please sign in instead."
                            rawErr.contains("badly formatted", ignoreCase = true) ||
                            rawErr.contains("invalid-email", ignoreCase = true) ->
                                "Invalid mobile phone number format."
                            rawErr.contains("weak-password", ignoreCase = true) ->
                                "Password is too weak. Please use at least 6 characters."
                            else -> rawErr
                        }
                        _authState.value = AuthState.Error(formatted)
                        onError(formatted)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Sign up exception: ${e.localizedMessage}", e)
            val err = e.localizedMessage ?: "Registration error. Please try again."
            _authState.value = AuthState.Error(err)
            onError(err)
        }
    }

    /**
     * Signs in an existing user with Mobile Phone Number and Password
     */
    fun signInWithPhoneAndPassword(
        phoneNumber: String,
        password: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (phoneNumber.isBlank() || password.isBlank()) {
            val err = "Please enter both mobile phone number and password."
            _authState.value = AuthState.Error(err)
            onError(err)
            return
        }

        val internalEmail = getInternalEmail(phoneNumber)
        _authState.value = AuthState.VerificationInProgress("Signing in...")

        try {
            getFirebaseAuth().signInWithEmailAndPassword(internalEmail, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        val uid = user?.uid
                        if (uid != null) {
                            Log.d(TAG, "Sign in successful for phone $phoneNumber ($internalEmail), UID: $uid")
                            checkUserProfileInFirestore(uid, phoneNumber)
                            onSuccess()
                        } else {
                            val err = "Authentication succeeded but user session is null."
                            _authState.value = AuthState.Error(err)
                            onError(err)
                        }
                    } else {
                        val rawErr = task.exception?.message ?: "Authentication failed."
                        val formatted = when {
                            rawErr.contains("no user record", ignoreCase = true) ||
                            rawErr.contains("user-not-found", ignoreCase = true) ->
                                "No account found with this phone number. Please register first."
                            rawErr.contains("invalid-credential", ignoreCase = true) ||
                            rawErr.contains("wrong-password", ignoreCase = true) ||
                            rawErr.contains("invalid password", ignoreCase = true) ->
                                "Incorrect password. Please try again."
                            else -> rawErr
                        }
                        _authState.value = AuthState.Error(formatted)
                        onError(formatted)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Sign in exception: ${e.localizedMessage}", e)
            val err = e.localizedMessage ?: "Sign in error. Please try again."
            _authState.value = AuthState.Error(err)
            onError(err)
        }
    }

    /**
     * Triggers Firebase sendPasswordResetEmail using mapped internal email address for phone number
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

        try {
            getFirebaseAuth().sendPasswordResetEmail(internalEmail)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Password reset email sent to internal email: $internalEmail for phone $phoneNumber")
                        onSuccess("Password reset instructions sent for account linked to $phoneNumber.")
                    } else {
                        val rawErr = task.exception?.message ?: "Password reset failed."
                        val formatted = when {
                            rawErr.contains("no user record", ignoreCase = true) ||
                            rawErr.contains("user-not-found", ignoreCase = true) ->
                                "No registered account found with mobile number $phoneNumber."
                            rawErr.contains("invalid-email", ignoreCase = true) ->
                                "Invalid phone number format."
                            else -> rawErr
                        }
                        onError(formatted)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Password reset exception: ${e.localizedMessage}", e)
            onError(e.localizedMessage ?: "Failed to request password reset. Please try again.")
        }
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
                val avatarDir = File(context.filesDir, "profile_avatars").apply { mkdirs() }
                val destFile = File(avatarDir, "avatar_${uid}.jpg")

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

    /**
     * Consolidates duplicate user accounts under a single Primary User document and session.
     */
    fun performAutomaticAccountMerge(
        authUid: String,
        loginPhone: String,
        onComplete: ((UserProfile) -> Unit)? = null
    ) {
        val db = getFirestore()
        val suffix = PhoneUtils.extractPhoneSuffix(loginPhone)

        if (suffix.isBlank()) {
            checkUserProfileInFirestore(authUid, loginPhone)
            return
        }

        try {
            db.collection("users")
                .whereEqualTo("phoneSuffix", suffix)
                .get()
                .addOnSuccessListener { querySnap ->
                    val documents = querySnap?.documents ?: emptyList()

                    if (documents.isEmpty()) {
                        // Document not found by phoneSuffix - search by authUid
                        db.collection("users").document(authUid).get()
                            .addOnSuccessListener { doc ->
                                if (doc != null && doc.exists() && !doc.getString("name").isNullOrBlank()) {
                                    val profile = extractUserProfileFromDoc(authUid, loginPhone, doc)
                                    saveLocalSession(profile.uid, profile.name, profile.phoneNumber, profile.profilePicUrl, profile.bio)
                                    _authState.value = AuthState.Authenticated(profile)
                                    onComplete?.invoke(profile)
                                } else {
                                    handleMissingProfile(authUid, loginPhone)
                                }
                            }
                            .addOnFailureListener {
                                handleMissingProfile(authUid, loginPhone)
                            }
                        return@addOnSuccessListener
                    }

                    // 1. DUPLICATE ACCOUNT DETECTION & SELECTION:
                    // Sort the documents by lastLoginAt, updatedAt, or createdAt timestamp DESCENDING
                    val sortedDocs = documents.sortedByDescending { doc ->
                        doc.getLong("lastLoginAt")
                            ?: doc.getLong("updatedAt")
                            ?: doc.getLong("createdAt")
                            ?: 0L
                    }

                    // Set the NEWEST / MOST RECENT document as the Primary User Account source
                    val newestDoc = sortedDocs.first()
                    val primaryName = newestDoc.getString("name")?.takeIf { it.isNotBlank() }
                        ?: prefs.getString(KEY_NAME, "")?.takeIf { it.isNotBlank() }
                        ?: "Talkly User"
                    val primaryPhone = newestDoc.getString("phoneNumber")?.takeIf { it.isNotBlank() } ?: loginPhone
                    val docPic = newestDoc.getString("profilePicUrl") ?: ""
                    val localPic = prefs.getString(KEY_PROFILE_PIC, "") ?: ""
                    val primaryPic = if (docPic.startsWith("http://") || docPic.startsWith("https://") || docPic.startsWith("data:")) {
                        docPic
                    } else if (localPic.isNotBlank() && !localPic.startsWith("content://")) {
                        localPic
                    } else {
                        docPic
                    }
                    val primaryBio = newestDoc.getString("bio")?.takeIf { it.isNotBlank() } ?: "Available on Talkly 💬"
                    val primaryCreated = newestDoc.getLong("createdAt") ?: System.currentTimeMillis()

                    val primaryUid = authUid
                    val now = System.currentTimeMillis()

                    val primaryProfile = UserProfile(
                        uid = primaryUid,
                        name = primaryName,
                        phoneNumber = primaryPhone,
                        phoneSuffix = suffix,
                        profilePicUrl = primaryPic,
                        bio = primaryBio
                    )

                    // 3. PERSIST SINGLE PRIMARY SESSION:
                    val primaryProfileMap = mapOf(
                        "uid" to primaryUid,
                        "name" to primaryName,
                        "phoneNumber" to primaryPhone,
                        "phoneSuffix" to suffix,
                        "email" to getInternalEmail(primaryPhone),
                        "profilePicUrl" to primaryPic,
                        "bio" to primaryBio,
                        "createdAt" to primaryCreated,
                        "lastLoginAt" to now,
                        "updatedAt" to now,
                        "isMerged" to false
                    )

                    db.collection("users").document(primaryUid)
                        .set(primaryProfileMap, SetOptions.merge())

                    saveLocalSession(primaryUid, primaryName, primaryPhone, primaryPic, primaryBio)

                    // 2. DATA MIGRATION & CLEANUP:
                    val olderDuplicateDocs = documents.filter { it.id != primaryUid }
                    for (dupDoc in olderDuplicateDocs) {
                        val dupUid = dupDoc.id
                        Log.d(TAG, "Merging duplicate user account $dupUid into primary account $primaryUid")

                        db.collection("users").document(dupUid).set(
                            mapOf("isMerged" to true, "mergedIntoUid" to primaryUid),
                            SetOptions.merge()
                        )

                        migrateDuplicateAccountData(dupUid, primaryUid)

                        db.collection("users").document(dupUid).delete()
                    }

                    _authState.value = AuthState.Authenticated(primaryProfile)
                    onComplete?.invoke(primaryProfile)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error querying users by phoneSuffix for merge: ${e.localizedMessage}")
                    checkUserProfileInFirestore(authUid, loginPhone)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Exception in performAutomaticAccountMerge: ${e.localizedMessage}")
            checkUserProfileInFirestore(authUid, loginPhone)
        }
    }

    private fun migrateDuplicateAccountData(fromUid: String, toUid: String) {
        if (fromUid == toUid) return
        val db = getFirestore()
        try {
            // Re-link existing chat threads and messages
            db.collection("family_chats").document(fromUid).collection("messages").get()
                .addOnSuccessListener { snap ->
                    if (snap != null && !snap.isEmpty) {
                        for (doc in snap.documents) {
                            val msgData = doc.data?.toMutableMap() ?: mutableMapOf()
                            val msgId = doc.id
                            if (msgData["senderId"] == fromUid) msgData["senderId"] = toUid
                            if (msgData["receiverId"] == fromUid) msgData["receiverId"] = toUid

                            db.collection("family_chats").document(toUid)
                                .collection("messages").document(msgId)
                                .set(msgData)

                            doc.reference.delete()
                        }
                    }
                    db.collection("family_chats").document(fromUid).delete()
                }

            // Re-link contact references in family_members
            db.collection("family_members").document(fromUid).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val memberData = doc.data?.toMutableMap() ?: mutableMapOf()
                        memberData["id"] = toUid
                        memberData["firebaseUid"] = toUid
                        db.collection("family_members").document(toUid).set(memberData)
                        doc.reference.delete()
                    }
                }

            // Re-link / Merge Statuses & Stories in Firestore "statuses" collection
            db.collection("statuses").whereEqualTo("userId", fromUid).get()
                .addOnSuccessListener { snap ->
                    if (snap != null && !snap.isEmpty) {
                        for (doc in snap.documents) {
                            doc.reference.update("userId", toUid)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error migrating duplicate data from $fromUid to $toUid: ${e.localizedMessage}")
        }
    }

    private fun extractUserProfileFromDoc(uid: String, fallbackPhone: String, doc: com.google.firebase.firestore.DocumentSnapshot): UserProfile {
        val name = doc.getString("name") ?: ""
        val phone = doc.getString("phoneNumber") ?: fallbackPhone
        val suffix = doc.getString("phoneSuffix") ?: PhoneUtils.extractPhoneSuffix(phone)
        val docPic = doc.getString("profilePicUrl") ?: ""
        val localPic = prefs.getString(KEY_PROFILE_PIC, "") ?: ""
        val finalPic = if (docPic.startsWith("http://") || docPic.startsWith("https://") || docPic.startsWith("data:")) {
            docPic
        } else if (localPic.isNotBlank() && !localPic.startsWith("content://")) {
            localPic
        } else {
            docPic
        }
        val bio = doc.getString("bio") ?: "Available on Talkly 💬"

        return UserProfile(
            uid = uid,
            name = name,
            phoneNumber = phone,
            phoneSuffix = suffix,
            profilePicUrl = finalPic,
            bio = bio
        )
    }

    private fun cleanupDuplicateUserDocsAndSave(
        uid: String,
        profileMap: Map<String, Any?>,
        onComplete: (() -> Unit)? = null
    ) {
        val phone = profileMap["phoneNumber"] as? String ?: ""
        performAutomaticAccountMerge(uid, phone) {
            onComplete?.invoke()
        }
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
        val (localPicUrl, firestorePicUrl) = processProfileAvatarImage(uid, profilePicUrl)

        saveLocalSession(uid, name, phoneNumber, localPicUrl, bio)
        val profile = UserProfile(
            uid = uid,
            name = name,
            phoneNumber = phoneNumber,
            phoneSuffix = phoneSuffix,
            profilePicUrl = localPicUrl,
            bio = bio
        )
        _authState.value = AuthState.Authenticated(profile)

        val profileMap = mapOf(
            "uid" to uid,
            "name" to name,
            "phoneNumber" to phoneNumber,
            "phoneSuffix" to phoneSuffix,
            "email" to getInternalEmail(phoneNumber),
            "profilePicUrl" to firestorePicUrl,
            "bio" to bio,
            "createdAt" to System.currentTimeMillis(),
            "lastLoginAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )

        cleanupDuplicateUserDocsAndSave(uid, profileMap, onSuccess)
    }

    /**
     * Checks Firestore 'users/{uid}' collection to see if user has completed profile setup
     */
    private fun checkUserProfileInFirestore(uid: String, phoneNumber: String) {
        performAutomaticAccountMerge(uid, phoneNumber)
    }

    private fun restoreAndAuthenticateUser(uid: String, loginPhone: String, doc: com.google.firebase.firestore.DocumentSnapshot) {
        val name = doc.getString("name") ?: ""
        val phone = doc.getString("phoneNumber") ?: loginPhone
        val suffix = doc.getString("phoneSuffix") ?: PhoneUtils.extractPhoneSuffix(phone)
        val docPic = doc.getString("profilePicUrl") ?: ""
        val bio = doc.getString("bio") ?: "Available on Talkly 💬"

        val profile = UserProfile(
            uid = uid,
            name = name,
            phoneNumber = phone,
            phoneSuffix = suffix,
            profilePicUrl = docPic,
            bio = bio
        )
        saveLocalSession(uid, name, phone, docPic, bio)

        val profileMap = mapOf(
            "uid" to uid,
            "name" to name,
            "phoneNumber" to phone,
            "phoneSuffix" to suffix,
            "email" to getInternalEmail(phone),
            "profilePicUrl" to docPic,
            "bio" to bio,
            "updatedAt" to System.currentTimeMillis()
        )

        cleanupDuplicateUserDocsAndSave(uid, profileMap)

        if (doc.id != uid) {
            try {
                getFirestore().collection("users").document(doc.id).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting old user document ${doc.id}: ${e.localizedMessage}")
            }
        }

        _authState.value = AuthState.Authenticated(profile)
    }

    private fun handleMissingProfile(uid: String, phoneNumber: String) {
        val localName = prefs.getString(KEY_NAME, "") ?: ""
        if (localName.isNotBlank()) {
            val phone = prefs.getString(KEY_PHONE, phoneNumber) ?: phoneNumber
            val pic = prefs.getString(KEY_PROFILE_PIC, "") ?: ""
            val bio = prefs.getString(KEY_BIO, "Available on Talkly 💬") ?: "Available on Talkly 💬"
            val suffix = PhoneUtils.extractPhoneSuffix(phone)

            val profile = UserProfile(
                uid = uid,
                name = localName,
                phoneNumber = phone,
                phoneSuffix = suffix,
                profilePicUrl = pic,
                bio = bio
            )

            val profileMap = mapOf(
                "uid" to uid,
                "name" to localName,
                "phoneNumber" to phone,
                "phoneSuffix" to suffix,
                "email" to getInternalEmail(phone),
                "profilePicUrl" to pic,
                "bio" to bio,
                "updatedAt" to System.currentTimeMillis()
            )

            cleanupDuplicateUserDocsAndSave(uid, profileMap)

            _authState.value = AuthState.Authenticated(profile)
        } else {
            saveLocalSession(uid, "", phoneNumber, "", "")
            _authState.value = AuthState.ProfileSetupRequired(uid, phoneNumber)
        }
    }

    /**
     * Saves name, bio and profile picture to Firestore 'users' collection and local session
     */
    fun saveUserProfile(
        name: String,
        profilePicUrl: String,
        bio: String = "Available on Talkly 💬",
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
            uid = prefs.getString(KEY_UID, "") ?: ""
            phone = prefs.getString(KEY_PHONE, "") ?: ""
        }

        if (uid.isBlank()) {
            val err = "User session invalid. Please sign in again."
            _authState.value = AuthState.Error(err)
            onError(err)
            return
        }

        val phoneSuffix = PhoneUtils.extractPhoneSuffix(phone)
        val (localPicUrl, firestorePicUrl) = processProfileAvatarImage(uid, profilePicUrl)

        // Save local session immediately
        saveLocalSession(uid, name, phone, localPicUrl, bio)
        val profile = UserProfile(
            uid = uid,
            name = name,
            phoneNumber = phone,
            phoneSuffix = phoneSuffix,
            profilePicUrl = localPicUrl,
            bio = bio
        )
        _authState.value = AuthState.Authenticated(profile)

        val profileMap = mapOf(
            "uid" to uid,
            "name" to name,
            "phoneNumber" to phone,
            "phoneSuffix" to phoneSuffix,
            "email" to getInternalEmail(phone),
            "profilePicUrl" to firestorePicUrl,
            "bio" to bio,
            "updatedAt" to System.currentTimeMillis()
        )

        cleanupDuplicateUserDocsAndSave(uid, profileMap, onSuccess)
    }

    private fun saveLocalSession(uid: String, name: String, phone: String, pic: String, bio: String = "Available on Talkly 💬") {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_UID, uid)
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phone)
            .putString(KEY_PROFILE_PIC, pic)
            .putString(KEY_BIO, bio)
            .apply()

        try {
            context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE).edit()
                .putString("user_uid", uid)
                .putString("user_name", name)
                .putString("user_phone", phone)
                .putString("user_profile_pic", pic)
                .putString("user_bio", bio)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing talkly_user_session prefs: ${e.message}")
        }
    }

    fun logout() {
        try {
            getFirebaseAuth().signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Sign out exception: ${e.localizedMessage}")
        }
        prefs.edit().clear().apply()
        try {
            context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing talkly_user_session prefs: ${e.message}")
        }
        _authState.value = AuthState.Unauthenticated
    }
}
