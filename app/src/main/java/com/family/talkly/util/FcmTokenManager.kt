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
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result ?: return@addOnCompleteListener
                Log.d(TAG, "FCM registration token obtained: $token")

                // Save locally
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncFcmToken: ${e.localizedMessage}")
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
            // Write to Firestore fcm_outbox trigger collection
            val firestore = FirebaseFirestore.getInstance()
            val pushDoc = mapOf(
                "to" to targetFcmToken,
                "priority" to "high",
                "data" to dataPayload,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("fcm_outbox").add(pushDoc)
                .addOnSuccessListener {
                    Log.d(TAG, "Queued FCM high priority push in Firestore fcm_outbox")
                }

            // Also send direct legacy HTTP payload if legacy server key is available or via background request
            val json = JSONObject().apply {
                put("to", targetFcmToken)
                put("priority", "high")
                put("content_available", true)
                val dataObj = JSONObject()
                dataPayload.forEach { (key, value) ->
                    dataObj.put(key, value)
                }
                put("data", dataObj)
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
