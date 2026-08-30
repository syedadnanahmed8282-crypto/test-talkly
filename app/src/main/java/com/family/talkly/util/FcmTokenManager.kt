package com.family.talkly.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.family.talkly.data.supabase.SupabaseClientProvider
import com.family.talkly.data.supabase.SupabaseFcmToken
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun isGooglePlayServicesAvailableSafely(context: Context): Boolean {
        return try {
            val clazz = Class.forName("com.google.android.gms.common.GoogleApiAvailability")
            val getInstanceMethod = clazz.getMethod("getInstance")
            val instance = getInstanceMethod.invoke(null)
            val isAvailableMethod = clazz.getMethod("isGooglePlayServicesAvailable", Context::class.java)
            val resultCode = isAvailableMethod.invoke(instance, context) as? Int ?: -1
            resultCode == 0
        } catch (e: Throwable) {
            false
        }
    }

    private fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: Build.MODEL
        } catch (e: Exception) {
            Build.MODEL ?: "android_device"
        }
    }

    /**
     * Fetches current FCM token and registers it in Supabase for the authenticated user.
     */
    fun syncFcmToken(context: Context) {
        try {
            if (!isGooglePlayServicesAvailableSafely(context)) {
                Log.i(TAG, "Google Play Services unavailable on this device. Using cached token if present.")
                syncExistingCachedToken(context)
                return
            }

            val fcmInstance = try {
                FirebaseMessaging.getInstance().apply {
                    isAutoInitEnabled = false
                }
            } catch (e: Throwable) {
                Log.i(TAG, "FirebaseMessaging initialization note: ${e.localizedMessage}")
                syncExistingCachedToken(context)
                return
            }

            try {
                fcmInstance.token.addOnCompleteListener { task ->
                    try {
                        if (!task.isSuccessful) {
                            Log.i(TAG, "FCM token retrieval skipped: ${task.exception?.localizedMessage}")
                            syncExistingCachedToken(context)
                            return@addOnCompleteListener
                        }

                        val token = task.result
                        if (token.isNullOrBlank()) {
                            syncExistingCachedToken(context)
                            return@addOnCompleteListener
                        }
                        Log.d(TAG, "FCM registration token obtained: ${token.take(12)}...")

                        // Save locally
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

                        updateTokenInSupabase(context, token)
                    } catch (e: Throwable) {
                        Log.i(TAG, "FCM token processing error: ${e.localizedMessage}")
                        syncExistingCachedToken(context)
                    }
                }
            } catch (e: SecurityException) {
                Log.i(TAG, "SecurityException connecting to GMS broker: ${e.localizedMessage}")
                syncExistingCachedToken(context)
            } catch (e: Throwable) {
                Log.i(TAG, "Error requesting FCM token: ${e.localizedMessage}")
                syncExistingCachedToken(context)
            }
        } catch (e: Throwable) {
            Log.i(TAG, "FCM service initialization skipped: ${e.localizedMessage}")
            syncExistingCachedToken(context)
        }
    }

    private fun syncExistingCachedToken(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedToken = prefs.getString(KEY_FCM_TOKEN, null)
            if (!cachedToken.isNullOrBlank()) {
                updateTokenInSupabase(context, cachedToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing cached FCM token: ${e.localizedMessage}")
        }
    }

    private fun updateTokenInSupabase(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fallbackPrefs = context.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
        
        val uid = SupabaseClientProvider.auth.currentUserOrNull()?.id
            ?: prefs.getString("user_uid", null)
            ?: fallbackPrefs.getString("user_uid", null)

        if (uid.isNullOrBlank() || uid == "self") {
            Log.d(TAG, "Skipping Supabase FCM token registration: No authenticated user session yet.")
            return
        }

        val deviceId = getDeviceId(context)

        scope.launch {
            try {
                Log.e(TAG, "DIAGNOSTIC_FCM_IDS: userUid='$uid', deviceId='$deviceId'")
                val tokenPayload = buildJsonObject {
                    put("user_id", uid)
                    put("token", token)
                    put("device_id", deviceId)
                    put("platform", "android")
                }

                SupabaseClientProvider.client.postgrest["fcm_tokens"]
                    .upsert(tokenPayload)

                Log.d(TAG, "Successfully registered FCM token in Supabase for user: $uid (device: $deviceId)")
            } catch (e: Exception) {
                Log.w(TAG, "Error registering FCM token in Supabase: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Removes the current device's FCM token from Supabase upon logout.
     */
    fun unregisterToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedToken = prefs.getString(KEY_FCM_TOKEN, null) ?: return

        scope.launch {
            try {
                SupabaseClientProvider.client.postgrest["fcm_tokens"]
                    .delete {
                        filter {
                            eq("token", cachedToken)
                        }
                    }
                Log.d(TAG, "Successfully removed FCM token from Supabase upon logout")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering FCM token from Supabase: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Triggers high priority FCM push notification for incoming calls or chat messages
     * via the secure Supabase server-side notification bridge (Edge Function).
     */
    fun sendHighPriorityPush(
        targetUid: String,
        targetPhoneSuffix: String,
        dataPayload: Map<String, String>
    ) {
        try {
            val callerUid = dataPayload["callerUid"] ?: dataPayload["caller_id"] ?: dataPayload["senderUid"] ?: ""
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

            dispatchPushViaSupabaseBridge(
                targetUid = targetUid,
                targetPhoneSuffix = targetPhoneSuffix,
                dataPayload = dataPayload
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering FCM push via Supabase: ${e.localizedMessage}")
        }
    }

    private fun dispatchPushViaSupabaseBridge(
        targetUid: String,
        targetPhoneSuffix: String,
        dataPayload: Map<String, String>
    ) {
        scope.launch {
            try {
                val supabaseUrl = SupabaseClientProvider.supabaseUrl
                val publishableKey = SupabaseClientProvider.supabasePublishableKey
                val currentSessionToken = try {
                    SupabaseClientProvider.auth.currentAccessTokenOrNull()
                } catch (e: Exception) {
                    null
                }

                if (currentSessionToken.isNullOrBlank()) {
                    Log.w(TAG, "Skipping push notification dispatch: No active authenticated Supabase user session.")
                    return@launch
                }

                val payloadType = dataPayload["type"] ?: "CHAT_MESSAGE"
                val title = dataPayload["senderName"] ?: dataPayload["callerName"] ?: dataPayload["title"] ?: "Talkly"
                val body = dataPayload["messageText"] ?: if (payloadType == "INCOMING_CALL") "Incoming Call" else "New message"

                val json = JSONObject().apply {
                    put("recipient_id", targetUid)
                    put("recipient_phone_suffix", targetPhoneSuffix)
                    put("type", payloadType)
                    put("title", title)
                    put("body", body)

                    val dataObj = JSONObject()
                    dataPayload.forEach { (key, value) ->
                        dataObj.put(key, value)
                    }
                    put("data", dataObj)
                }

                val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val edgeFunctionUrl = "$supabaseUrl/functions/v1/send-push-notification"

                val request = Request.Builder()
                    .url(edgeFunctionUrl)
                    .addHeader("apikey", publishableKey)
                    .addHeader("Authorization", "Bearer $currentSessionToken")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.w(TAG, "Supabase Push Bridge network request note: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Log.d(TAG, "Supabase Push Bridge response code: ${response.code}")
                        response.close()
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Error invoking Supabase Push notification bridge: ${e.localizedMessage}")
            }
        }
    }
}
