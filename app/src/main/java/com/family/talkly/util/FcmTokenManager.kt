package com.family.talkly.util

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.SetOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object FcmTokenManager {

    private const val TAG = "FcmTokenManager"
    private const val PREFS_NAME = "talkly_auth_session"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private val httpClient by lazy { OkHttpClient() }

    /**
     * Fetches current FCM token and registers it in Firestore for the authenticated user.
     */
    fun syncFcmToken(context: Context) {
        try {
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.w(TAG, "Google Play Services unavailable ($resultCode). Syncing cached token if available.")
                syncExistingCachedToken(context)
                return
            }

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed: ${task.exception?.localizedMessage}")
                    syncExistingCachedToken(context)
                    return@addOnCompleteListener
                }

                val token = task.result ?: run {
                    syncExistingCachedToken(context)
                    return@addOnCompleteListener
                }
                Log.d(TAG, "FCM registration token obtained: $token")

                // Save locally
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

                updateTokenInFirestore(context, token)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncFcmToken: ${e.localizedMessage}")
            syncExistingCachedToken(context)
        }
    }

    private fun syncExistingCachedToken(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedToken = prefs.getString(KEY_FCM_TOKEN, null)
            if (!cachedToken.isNullOrBlank()) {
                updateTokenInFirestore(context, cachedToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing cached FCM token: ${e.localizedMessage}")
        }
    }

    private fun updateTokenInFirestore(context: Context, token: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: prefs.getString("user_uid", null)
            val phone = prefs.getString("user_phone", "") ?: ""
            val phoneSuffix = PhoneUtils.extractPhoneSuffix(phone)

            val firestore = FirebaseFirestore.getInstance()

            if (!uid.isNullOrBlank()) {
                val tokenMap = mapOf(
                    "fcmToken" to token,
                    "lastTokenUpdate" to System.currentTimeMillis()
                )
                firestore.collection("users").document(uid)
                    .set(tokenMap, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully updated FCM token for user $uid")
                    }
            }

            if (phoneSuffix.isNotBlank()) {
                val phoneMap = mapOf("fcmToken" to token)
                firestore.collection("users_phone_index").document(phoneSuffix)
                    .set(phoneMap, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully updated FCM token for phone suffix $phoneSuffix")
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating token in Firestore: ${e.localizedMessage}")
        }
    }

    /**
     * Triggers high priority FCM push notification for incoming calls or chat messages
     */
    fun sendHighPriorityPush(
        targetUid: String,
        targetPhoneSuffix: String,
        dataPayload: Map<String, String>
    ) {
        try {
            val callerUid = dataPayload["callerUid"] ?: dataPayload["senderUid"] ?: ""
            val callerPhone = dataPayload["callerPhone"] ?: ""
            val callerPhoneSuffix = PhoneUtils.extractPhoneSuffix(callerPhone)

            // Exclude sender from push notification
            if (callerUid.isNotBlank() && targetUid.isNotBlank() && callerUid == targetUid) {
                Log.d(TAG, "Skipping FCM push: targetUid matches callerUid ($callerUid)")
                return
            }
            if (callerPhoneSuffix.isNotBlank() && targetPhoneSuffix.isNotBlank() && callerPhoneSuffix == targetPhoneSuffix) {
                Log.d(TAG, "Skipping FCM push: targetPhoneSuffix matches callerPhoneSuffix ($callerPhoneSuffix)")
                return
            }

            val firestore = FirebaseFirestore.getInstance()

            // Find target FCM token
            val onTokenFound: (String) -> Unit = { fcmToken ->
                if (fcmToken.isNotBlank()) {
                    dispatchFcmPushToToken(fcmToken, dataPayload)
                }
            }

            if (targetUid.isNotBlank()) {
                firestore.collection("users").document(targetUid).get()
                    .addOnSuccessListener { doc ->
                        val token = doc.getString("fcmToken") ?: ""
                        if (token.isNotBlank()) {
                            onTokenFound(token)
                        } else if (targetPhoneSuffix.isNotBlank()) {
                            lookupPhoneIndexToken(firestore, targetPhoneSuffix, onTokenFound)
                        }
                    }
                    .addOnFailureListener {
                        if (targetPhoneSuffix.isNotBlank()) {
                            lookupPhoneIndexToken(firestore, targetPhoneSuffix, onTokenFound)
                        }
                    }
            } else if (targetPhoneSuffix.isNotBlank()) {
                lookupPhoneIndexToken(firestore, targetPhoneSuffix, onTokenFound)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering FCM push: ${e.localizedMessage}")
        }
    }

    private fun lookupPhoneIndexToken(
        firestore: FirebaseFirestore,
        phoneSuffix: String,
        onTokenFound: (String) -> Unit
    ) {
        firestore.collection("users_phone_index").document(phoneSuffix).get()
            .addOnSuccessListener { doc ->
                val token = doc.getString("fcmToken") ?: ""
                if (token.isNotBlank()) {
                    onTokenFound(token)
                }
            }
    }

    private fun dispatchFcmPushToToken(targetFcmToken: String, dataPayload: Map<String, String>) {
        try {
            // Write to Firestore fcm_outbox trigger collection for backend FCM Cloud Functions
            val firestore = FirebaseFirestore.getInstance()
            val pushDoc = mapOf(
                "to" to targetFcmToken,
                "priority" to "high",
                "content_available" to true,
                "time_to_live" to 0,
                "direct_boot_ok" to true,
                "data" to dataPayload,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("fcm_outbox").add(pushDoc)
                .addOnSuccessListener {
                    Log.d(TAG, "Queued high-priority FCM push (time_to_live: 0) in fcm_outbox")
                }

            // Also send direct legacy HTTP payload for instant notification delivery
            val json = JSONObject().apply {
                put("to", targetFcmToken)
                put("priority", "high")
                put("content_available", true)
                put("time_to_live", 0)
                put("direct_boot_ok", true)

                val dataObj = JSONObject()
                dataPayload.forEach { (key, value) ->
                    dataObj.put(key, value)
                }
                put("data", dataObj)

                val title = dataPayload["senderName"] ?: dataPayload["callerName"] ?: dataPayload["title"] ?: "Talkly"
                val body = dataPayload["messageText"] ?: if (dataPayload["type"] == "INCOMING_CALL") "Incoming Call" else "New message"
                val notifObj = JSONObject().apply {
                    put("title", title)
                    put("body", body)
                    put("sound", "default")
                    put("priority", "high")
                    put("channel_id", if (dataPayload["type"] == "INCOMING_CALL") TalklyNotificationHelper.CHANNEL_CALLS_ID else TalklyNotificationHelper.CHANNEL_MESSAGES_ID)
                }
                put("notification", notifObj)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://fcm.googleapis.com/fcm/send")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "FCM HTTP direct push failed: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(TAG, "FCM HTTP direct push result code: ${response.code}")
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching FCM push payload: ${e.localizedMessage}")
        }
    }
}
