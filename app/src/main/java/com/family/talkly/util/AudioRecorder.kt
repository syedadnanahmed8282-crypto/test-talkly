package com.family.talkly.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File? {
        val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
        outputFile = file

        return try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder = mr
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            Log.d("AudioRecorder", "Recording started successfully to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording: ${e.localizedMessage}", e)
            try {
                recorder?.reset()
                recorder?.release()
            } catch (ignored: Exception) {}
            recorder = null
            outputFile?.delete()
            outputFile = null
            null
        }
    }

    fun stopRecording(): File? {
        val rec = recorder
        recorder = null
        val targetFile = outputFile
        return try {
            rec?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w("AudioRecorder", "MediaRecorder.stop() failed: ${e.localizedMessage}")
                }
                release()
            }
            if (targetFile != null && targetFile.exists() && targetFile.length() > 0) {
                Log.d("AudioRecorder", "Recording stopped. File size: ${targetFile.length()} bytes")
                targetFile
            } else {
                targetFile?.delete()
                outputFile = null
                null
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording cleanly: ${e.localizedMessage}", e)
            try {
                rec?.release()
            } catch (ignored: Exception) {}
            outputFile
        }
    }

    fun cancelRecording() {
        val rec = recorder
        recorder = null
        try {
            rec?.apply {
                try {
                    stop()
                } catch (ignored: Exception) {}
                release()
            }
        } catch (e: Exception) {
            Log.w("AudioRecorder", "Error releasing recorder on cancel: ${e.localizedMessage}")
        } finally {
            outputFile?.delete()
            outputFile = null
        }
    }
}
