package com.family.talkly.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages system default incoming call ringtones and outgoing call ringback tones.
 */
class CallSoundManager(private val context: Context) {

    private var incomingRingtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var ringbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "Talkly_CallSound"
    }

    /**
     * Dynamically fetches and plays the system default phone ringtone set on the device.
     */
    @Synchronized
    fun startIncomingRingtone() {
        stopAllSounds()
        try {
            val systemRingtoneUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (systemRingtoneUri != null) {
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, systemRingtoneUri)
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.isLooping = true
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    ringtone.play()
                    incomingRingtone = ringtone
                    Log.d(TAG, "Started system default incoming call ringtone successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing system default incoming ringtone: ${e.localizedMessage}")
        }
    }

    /**
     * Stops the incoming call ringtone immediately when call is answered, rejected, or missed.
     */
    @Synchronized
    fun stopIncomingRingtone() {
        try {
            incomingRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            incomingRingtone = null
            Log.d(TAG, "Stopped incoming call ringtone")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping incoming ringtone: ${e.localizedMessage}")
        }
    }

    /**
     * Plays the outgoing call ringback tone (cring-cring) during DIALING state using system audio manager until connected.
     */
    @Synchronized
    fun startOutgoingRingbackTone() {
        stopAllSounds()
        try {
            Log.d(TAG, "Starting outgoing call ringback tone")
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            } catch (e: Exception) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_RING, 80)
            }

            ringbackJob?.cancel()
            ringbackJob = scope.launch {
                while (isActive) {
                    try {
                        toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1200)
                    } catch (e: Exception) {
                        Log.w(TAG, "ToneGenerator play error: ${e.localizedMessage}")
                    }
                    delay(3000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing outgoing ringback tone: ${e.localizedMessage}")
        }
    }

    /**
     * Stops outgoing call ringback tone and restores normal audio mode.
     */
    @Synchronized
    fun stopOutgoingRingbackTone() {
        try {
            ringbackJob?.cancel()
            ringbackJob = null

            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Stopped outgoing call ringback tone")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping outgoing ringback tone: ${e.localizedMessage}")
        }
    }

    /**
     * Stops all active call sounds immediately.
     */
    @Synchronized
    fun stopAllSounds() {
        stopIncomingRingtone()
        stopOutgoingRingbackTone()
    }
}
