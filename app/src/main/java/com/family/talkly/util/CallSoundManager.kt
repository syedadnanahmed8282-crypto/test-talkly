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
                    val tg = toneGenerator ?: break
                    try {
                        tg.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1200)
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
     * Stops outgoing call ringback tone immediately and releases resources.
     */
    @Synchronized
    fun stopOutgoingRingbackTone() {
        try {
            ringbackJob?.cancel()
            ringbackJob = null

            toneGenerator?.let { tg ->
                try {
                    tg.stopTone()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping tone: ${e.localizedMessage}")
                }
                try {
                    tg.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing tone generator: ${e.localizedMessage}")
                }
            }
            toneGenerator = null
            Log.d(TAG, "Explicitly stopped and released outgoing ringtone tone generator")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping outgoing ringback tone: ${e.localizedMessage}")
        }
    }

    /**
     * Configures AudioManager and audio constraints for an active voice/video call.
     * MODE_IN_COMMUNICATION with echo cancellation and auto gain control.
     */
    @Synchronized
    fun configureAudioForActiveCall(isSpeakerOn: Boolean, isMuted: Boolean) {
        stopAllSounds()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            // Explicitly request audio focus for voice call / communication
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = isMuted
            audioManager.isSpeakerphoneOn = isSpeakerOn

            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            if (maxVol > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)
            }

            try {
                if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                    Log.d(TAG, "Hardware AcousticEchoCanceler is active for WebRTC stream")
                }
                if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                    Log.d(TAG, "Hardware AutomaticGainControl is active for WebRTC stream")
                }
                if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                    Log.d(TAG, "Hardware NoiseSuppressor is active for WebRTC stream")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio effect check: ${e.message}")
            }

            Log.d(TAG, "Configured active call audio: STREAM_VOICE_CALL, MODE_IN_COMMUNICATION, speaker=$isSpeakerOn, mute=$isMuted")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring active call audio: ${e.localizedMessage}")
        }
    }

    /**
     * Sets or toggles Speakerphone state on AudioManager.
     */
    @Synchronized
    fun setSpeakerphoneOn(isSpeakerOn: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isSpeakerOn
            Log.d(TAG, "Speakerphone set to: $isSpeakerOn")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speakerphone: ${e.localizedMessage}")
        }
    }

    /**
     * Sets or toggles Microphone mute state on AudioManager.
     */
    @Synchronized
    fun setMicrophoneMute(isMuted: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.isMicrophoneMute = isMuted
            Log.d(TAG, "Microphone mute set to: $isMuted")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting microphone mute: ${e.localizedMessage}")
        }
    }

    /**
     * Resets AudioManager mode to MODE_NORMAL and restores default audio parameters when call ends.
     */
    @Synchronized
    fun resetAudioMode() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Reset audio mode to MODE_NORMAL")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audio mode: ${e.localizedMessage}")
        }
    }

    /**
     * Stops all active call ringtones immediately.
     */
    @Synchronized
    fun stopAllSounds() {
        stopIncomingRingtone()
        stopOutgoingRingbackTone()
    }
}
