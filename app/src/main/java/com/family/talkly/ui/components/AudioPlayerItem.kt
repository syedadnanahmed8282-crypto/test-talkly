package com.family.talkly.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.ui.theme.LocalIsDarkTheme
import com.family.talkly.ui.theme.WhatsappDarkBubble
import com.family.talkly.ui.theme.WhatsappDarkSurface
import com.family.talkly.ui.theme.WhatsappGreen
import com.family.talkly.ui.theme.WhatsappLightGreen
import com.family.talkly.ui.theme.WhatsappTeal
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import java.io.File
import java.util.Locale

@Composable
fun AudioWaveformBar(
    progress: Float,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    barCount: Int = 30,
    seed: Int = 0,
    activeColor: Color = WhatsappGreen,
    inactiveColor: Color = Color.Gray.copy(alpha = 0.35f),
    onSeek: ((Float) -> Unit)? = null
) {
    val barHeights = remember(seed, barCount) {
        val random = java.util.Random(seed.toLong())
        List(barCount) {
            val base = 0.25f + random.nextFloat() * 0.75f
            base.coerceIn(0.25f, 1.0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val clickedRatio = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek(clickedRatio)
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            barHeights.forEachIndexed { index, heightFactor ->
                val barProgress = index.toFloat() / barCount.toFloat()
                val isFilled = barProgress <= progress

                val color = if (isFilled) activeColor else inactiveColor
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .fillMaxHeight(heightFactor)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

@Composable
fun AudioPlayerItem(
    message: ChatMessage,
    isSelf: Boolean = message.senderId == "self",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPrepared by remember { mutableStateOf(false) }

    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(message.mediaUrl) {
        onDispose {
            try {
                try {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.stop()
                    }
                } catch (ignored: Exception) {}
                mediaPlayer.release()
            } catch (e: Exception) {
                Log.w("AudioPlayerItem", "Error disposing mediaPlayer: ${e.localizedMessage}")
            }
        }
    }

    LaunchedEffect(message.mediaUrl) {
        val url = message.mediaUrl
        if (!url.isNullOrEmpty()) {
            try {
                mediaPlayer.reset()
                mediaPlayer.setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayerItem", "MediaPlayer onError: what=$what, extra=$extra")
                    isPlaying = false
                    isPrepared = false
                    true // Return true so Android doesn't trigger crash popup
                }

                val uri: Uri = if (url.startsWith("data:")) {
                    // Base64 encoded voice note - write to temp cache file for MediaPlayer
                    val cacheFile = File(context.cacheDir, "temp_vn_${message.id.hashCode()}.m4a")
                    if (!cacheFile.exists() || cacheFile.length() == 0L) {
                        val base64Data = url.substringAfter(",")
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        cacheFile.writeBytes(bytes)
                    }
                    Uri.fromFile(cacheFile)
                } else if (url.startsWith("/")) {
                    Uri.fromFile(File(url))
                } else if (url.startsWith("file://")) {
                    val path = Uri.parse(url).path
                    if (path != null) Uri.fromFile(File(path)) else Uri.parse(url)
                } else {
                    Uri.parse(url)
                }

                mediaPlayer.setDataSource(context, uri)
                mediaPlayer.setOnPreparedListener { mp ->
                    isPrepared = true
                    durationMs = mp.duration.toLong()
                }
                mediaPlayer.setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = 0L
                }
                mediaPlayer.prepareAsync()
            } catch (e: Exception) {
                Log.e("AudioPlayerItem", "Error setting data source: ${e.localizedMessage}", e)
                isPrepared = false
                isPlaying = false
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                if (mediaPlayer.isPlaying) {
                    currentPositionMs = mediaPlayer.currentPosition.toLong()
                } else {
                    isPlaying = false
                }
            } catch (e: Exception) {
                isPlaying = false
            }
            delay(200)
        }
    }

    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isDarkTheme = LocalIsDarkTheme.current

    // Sage Green / Laurel Green theme color
    val sageGreen = Color(0xFF6B8766)

    // Pure white background for voice note container as requested
    val containerColor = Color.White
    val titleColor = sageGreen
    val subTextColor = Color(0xFF6B7280)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFFE2E8F0)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic Avatar Badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        sageGreen.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Note",
                    tint = sageGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play / Pause Button in Sage Green / Laurel Green
            Surface(
                shape = CircleShape,
                color = sageGreen,
                modifier = Modifier
                    .size(38.dp)
                    .clickable {
                        if (!isPrepared) return@clickable
                        try {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        } catch (e: Exception) {
                            Log.e("AudioPlayerItem", "Error toggling playback: ${e.localizedMessage}")
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Waveform / Progress bar & duration
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice Message",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Text(
                        text = formatTimeMs(if (isPlaying || currentPositionMs > 0) currentPositionMs else durationMs),
                        fontSize = 11.sp,
                        color = subTextColor
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                AudioWaveformBar(
                    progress = progress,
                    isPlaying = isPlaying,
                    seed = message.id.hashCode(),
                    activeColor = sageGreen,
                    inactiveColor = sageGreen.copy(alpha = 0.22f),
                    onSeek = { clickedRatio ->
                        if (isPrepared && durationMs > 0) {
                            val seekMs = (clickedRatio * durationMs).toLong()
                            currentPositionMs = seekMs
                            try {
                                mediaPlayer.seekTo(seekMs.toInt())
                            } catch (e: Exception) {
                                Log.e("AudioPlayerItem", "Error seeking: ${e.localizedMessage}")
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun formatTimeMs(timeMs: Long): String {
    if (timeMs <= 0) return "0:00"
    val totalSeconds = (timeMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
