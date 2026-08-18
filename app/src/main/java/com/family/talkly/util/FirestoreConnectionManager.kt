package com.family.talkly.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages Firestore connection resilience to prevent silent disconnects (zombie connections)
 * on idle network, app foreground/resume events, and network transitions (WiFi <-> Mobile Data).
 */
object FirestoreConnectionManager {

    private const val TAG = "FirestoreConnManager"
    private const val HEALTH_CHECK_INTERVAL_MS = 40_000L // 40 seconds
    private const val MIN_RECONNECT_INTERVAL_MS = 4_000L // Min 4s debounce between resets

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var healthCheckJob: Job? = null
    private var isInitialized = false

    private val isForeground = AtomicBoolean(false)
    private val isReconnecting = AtomicBoolean(false)
    private val lastReconnectTime = AtomicLong(0L)

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val reconnectListeners = mutableListOf<() -> Unit>()

    /**
     * Initializes network callback and lifecycle monitoring for Firestore connection resilience.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        try {
            connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val builder = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Network became AVAILABLE. Triggering Firestore reconnect.")
                    forceReconnectFirestore("NETWORK_AVAILABLE")
                }

                override fun onLost(network: Network) {
                    Log.w(TAG, "Network LOST. Awaiting new network connection.")
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (hasInternet && isValidated) {
                        Log.d(TAG, "Network capabilities VALIDATED with Internet.")
                        forceReconnectFirestore("NETWORK_CAPABILITIES_VALIDATED")
                    }
                }
            }

            networkCallback = callback
            connectivityManager?.registerNetworkCallback(builder.build(), callback)
            Log.i(TAG, "FirestoreConnectionManager initialized and NetworkCallback registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering ConnectivityManager NetworkCallback: ${e.localizedMessage}")
        }
    }

    /**
     * Call this when the app or activity moves to foreground (onResume).
     */
    fun onAppForegrounded() {
        val wasBackground = !isForeground.getAndSet(true)
        Log.d(TAG, "App foregrounded (wasBackground=$wasBackground). Reconnecting Firestore...")
        forceReconnectFirestore("APP_FOREGROUND_RESUME")
        startHealthCheckLoop()
    }

    /**
     * Call this when the app or activity moves to background (onPause/onStop).
     */
    fun onAppBackgrounded() {
        isForeground.set(false)
        stopHealthCheckLoop()
        Log.d(TAG, "App backgrounded. Paused periodic Firestore health check.")
    }

    /**
     * Registers a callback to be notified after a successful Firestore network reconnect.
     */
    fun addReconnectListener(listener: () -> Unit) {
        synchronized(reconnectListeners) {
            if (!reconnectListeners.contains(listener)) {
                reconnectListeners.add(listener)
            }
        }
    }

    fun removeReconnectListener(listener: () -> Unit) {
        synchronized(reconnectListeners) {
            reconnectListeners.remove(listener)
        }
    }

    /**
     * Forces Firestore to reset its persistent gRPC connection by cycling disableNetwork() -> enableNetwork().
     * This flushes pending incoming updates without requiring outgoing writes.
     */
    fun forceReconnectFirestore(reason: String, onComplete: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastReconnectTime.get()

        // Debounce if called too frequently (unless it's an explicit foreground resume)
        if (elapsed < MIN_RECONNECT_INTERVAL_MS && reason != "APP_FOREGROUND_RESUME" && reason != "MANUAL_FORCE") {
            Log.d(TAG, "Skipping reconnect ($reason): Debounced (last ran ${elapsed}ms ago)")
            onComplete?.invoke()
            return
        }

        if (!isReconnecting.compareAndSet(false, true)) {
            Log.d(TAG, "Reconnect already in progress, skipping duplicate trigger ($reason)")
            return
        }

        lastReconnectTime.set(now)

        scope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                Log.i(TAG, "Initiating Firestore disableNetwork() -> enableNetwork() cycle (Reason: $reason)...")

                firestore.disableNetwork().addOnCompleteListener { disableTask ->
                    if (!disableTask.isSuccessful) {
                        Log.w(TAG, "disableNetwork warning: ${disableTask.exception?.localizedMessage}")
                    }

                    scope.launch {
                        // Short delay to ensure gRPC channel is cleanly closed
                        delay(120)

                        firestore.enableNetwork().addOnCompleteListener { enableTask ->
                            isReconnecting.set(false)
                            if (enableTask.isSuccessful) {
                                Log.i(TAG, "Firestore enableNetwork() SUCCESS! Active listeners re-established ($reason).")
                            } else {
                                Log.e(TAG, "enableNetwork failed: ${enableTask.exception?.localizedMessage}")
                            }

                            // Notify listeners
                            synchronized(reconnectListeners) {
                                reconnectListeners.forEach { listener ->
                                    try { listener.invoke() } catch (e: Exception) { Log.w(TAG, "Error in reconnectListener: ${e.localizedMessage}") }
                                }
                            }
                            onComplete?.invoke()
                        }
                    }
                }
            } catch (e: Exception) {
                isReconnecting.set(false)
                Log.e(TAG, "Error during Firestore network reset ($reason): ${e.localizedMessage}")
                onComplete?.invoke()
            }
        }
    }

    private fun startHealthCheckLoop() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive && isForeground.get()) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                if (isForeground.get()) {
                    Log.d(TAG, "Periodic health check: Re-verifying Firestore active connection...")
                    forceReconnectFirestore("PERIODIC_HEALTH_CHECK")
                }
            }
        }
    }

    private fun stopHealthCheckLoop() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }
}
