package com.family.talkly.ui.components

import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.models.MessageType
import com.family.talkly.ui.theme.WhatsappGreen
import com.family.talkly.util.PhoneUtils
import com.family.talkly.util.VideoCacheManager
import java.io.File
import java.util.Locale

private const val TAG = "FullMediaViewerDialog"

@Composable
fun FullMediaViewerDialog(
    message: ChatMessage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = message.messageType == MessageType.VIDEO
    val rawMediaUrl = message.mediaUrl

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!rawMediaUrl.isNullOrBlank()) {
                    if (isVideo) {
                        VideoPlayerComponent(
                            mediaUrl = rawMediaUrl,
                            context = context,
                            onDismiss = onDismiss
                        )
                    } else {
                        // Image Viewer
                        AsyncImage(
                            model = PhoneUtils.getCoilMediaModel(rawMediaUrl),
                            contentDescription = "Full Media Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.senderName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            text = message.formattedTime,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Download to Gallery Button
                        IconButton(
                            onClick = { downloadMediaToGallery(context, message) },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download to Gallery",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Caption Bar (if text present)
                if (message.textContent.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        Text(
                            text = message.textContent,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerComponent(
    mediaUrl: String,
    context: Context,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var activePlayableUri by remember(mediaUrl) { mutableStateOf<Uri?>(null) }
    var isPreparing by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var controlsAutoDismissTimer by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var durationMs by remember { mutableIntStateOf(0) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var isUserDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    // Download/Cache status state
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadStatusText by remember { mutableStateOf<String?>(null) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    var retryKey by remember { mutableIntStateOf(0) }

    // Resolve & Preload/Cache large video
    LaunchedEffect(mediaUrl, retryKey) {
        playerErrorMessage = null
        isPreparing = true
        isBuffering = false
        isPlaying = false

        withContext(Dispatchers.IO) {
            val cachedFile = VideoCacheManager.getCachedVideoFile(context, mediaUrl)
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                Log.d(TAG, "Playing video from existing local cache: ${cachedFile.absolutePath}")
                activePlayableUri = Uri.fromFile(cachedFile)
                downloadProgress = null
                downloadStatusText = null
            } else if (mediaUrl.startsWith("http://", ignoreCase = true) || mediaUrl.startsWith("https://", ignoreCase = true)) {
                // For remote URLs (e.g. Cloudinary 30MB-40MB+ files):
                // Start background high-speed caching with OkHttp 120s timeout and 64KB buffers
                downloadProgress = 0
                downloadStatusText = "Buffering high quality video..."

                launch {
                    try {
                        val downloaded = VideoCacheManager.cacheVideoFile(context, mediaUrl) { progress, bytesRead, totalBytes ->
                            downloadProgress = progress
                            val readMb = bytesRead / (1024.0 * 1024.0)
                            val totalMb = totalBytes / (1024.0 * 1024.0)
                            downloadStatusText = if (totalBytes > 0) {
                                String.format(Locale.US, "Buffering video (%.1f / %.1f MB) %d%%", readMb, totalMb, progress)
                            } else {
                                String.format(Locale.US, "Buffering video (%.1f MB)", readMb)
                            }
                        }

                        // If player is still preparing or had stream trouble, switch immediately to complete local file!
                        if (activePlayableUri == null || activePlayableUri.toString().startsWith("http")) {
                            Log.d(TAG, "Switching playback to cached file: ${downloaded.absolutePath}")
                            activePlayableUri = Uri.fromFile(downloaded)
                            downloadProgress = null
                            downloadStatusText = null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Background video cache job warning: ${e.localizedMessage}")
                        // If direct playback is also failing, show error
                        if (activePlayableUri == null) {
                            playerErrorMessage = "Unable to stream video. Please check connection and retry."
                        }
                    }
                }

                // Also initialize direct streaming URI so player can attempt progressive streaming immediately
                activePlayableUri = Uri.parse(mediaUrl)
            } else {
                activePlayableUri = PhoneUtils.getMediaUri(context, mediaUrl)
            }
        }
    }

    // Auto-update playback progress position timer
    LaunchedEffect(isPlaying, videoViewRef) {
        while (isActive) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying) {
                val cur = vv.currentPosition
                val dur = vv.duration
                if (dur > 0) {
                    durationMs = dur
                    currentPositionMs = cur
                    if (!isUserDraggingSlider) {
                        sliderPosition = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }
            delay(250)
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(showControls, controlsAutoDismissTimer, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            if (System.currentTimeMillis() - controlsAutoDismissTimer >= 3800) {
                showControls = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
                videoViewRef?.suspend()
                mediaPlayerRef?.release()
                mediaPlayerRef = null
                videoViewRef = null
            } catch (e: Exception) {
                Log.w(TAG, "VideoView cleanup exception: ${e.localizedMessage}")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
                controlsAutoDismissTimer = System.currentTimeMillis()
            },
        contentAlignment = Alignment.Center
    ) {
        // Native Video View
        if (activePlayableUri != null) {
            androidx.compose.runtime.key(activePlayableUri) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            setOnPreparedListener { mp ->
                                mediaPlayerRef = mp
                                videoViewRef = this
                                isPreparing = false
                                isBuffering = false
                                playerErrorMessage = null
                                durationMs = mp.duration
                                if (durationMs <= 0 && mp.duration > 0) {
                                    durationMs = mp.duration
                                }
                                if (isMuted) {
                                    mp.setVolume(0f, 0f)
                                } else {
                                    mp.setVolume(1f, 1f)
                                }
                                mp.isLooping = true
                                mp.setOnBufferingUpdateListener { _, _ -> }
                                start()
                                isPlaying = true
                                Log.d(TAG, "Video playback prepared and started (duration=${mp.duration}ms)")
                            }

                            setOnInfoListener { _, what, extra ->
                                when (what) {
                                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                                        isBuffering = true
                                        Log.d(TAG, "MediaPlayer buffering start (extra=$extra)")
                                    }
                                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                                        isBuffering = false
                                        Log.d(TAG, "MediaPlayer buffering end (extra=$extra)")
                                    }
                                    MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                                        isPreparing = false
                                        isBuffering = false
                                        isPlaying = true
                                        Log.d(TAG, "MediaPlayer video rendering started")
                                    }
                                }
                                false
                            }

                        setOnErrorListener { _, what, extra ->
                            val errorDesc = when (what) {
                                MediaPlayer.MEDIA_ERROR_IO -> "Network I/O timeout / connection reset (-1004)"
                                MediaPlayer.MEDIA_ERROR_MALFORMED -> "Media malformed (-1007)"
                                MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Unsupported format (-1010)"
                                MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Stream timed out (-110)"
                                MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> "Not valid for progressive playback (200)"
                                else -> "Code $what, extra $extra"
                            }
                            Log.e(TAG, "VideoView playback error: $errorDesc (URL=$mediaUrl)")

                            isPreparing = false
                            isBuffering = false
                            isPlaying = false

                            // Fallback: If direct streaming failed, check if download is in progress
                            val cachedFile = VideoCacheManager.getCachedVideoFile(ctx, mediaUrl)
                            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                                Log.i(TAG, "Retrying playback with cached file fallback")
                                activePlayableUri = Uri.fromFile(cachedFile)
                            } else {
                                playerErrorMessage = "Video streaming interrupted ($errorDesc). Please tap Retry to reload."
                            }
                            true // Handled safely
                        }

                        setOnCompletionListener {
                            isPlaying = false
                        }

                        // Apply streaming headers (Range, User-Agent) for smooth CDN / Cloudinary streaming
                        val headers = mapOf(
                            "User-Agent" to "Talkly/1.0 (Android; VideoPlayer)",
                            "Accept" to "*/*"
                        )
                        setVideoURI(activePlayableUri, headers)
                    }
                },
                update = { view ->
                    videoViewRef = view
                },
                modifier = Modifier.fillMaxSize()
            )
            }
        }

        // Preparing / Buffering / Download Progress Overlay
        if ((isPreparing || isBuffering) && playerErrorMessage == null) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = WhatsappGreen,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val currentDownloadStatus = downloadStatusText
                    val currentProgress = downloadProgress

                    val statusMsg = if (!currentDownloadStatus.isNullOrBlank()) {
                        currentDownloadStatus
                    } else if (isBuffering) {
                        "Buffering video..."
                    } else {
                        "Loading video..."
                    }

                    Text(
                        text = statusMsg,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    if (currentProgress != null && currentProgress in 0..99) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val progressFraction = (currentProgress.toFloat() / 100f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .width(200.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = WhatsappGreen,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        // Error State Card Overlay
        if (playerErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Playback Error",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Playback Failed",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playerErrorMessage ?: "Unable to stream video.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                retryKey++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsappGreen),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Center Play / Pause Floating Button (when tapped)
        AnimatedVisibility(
            visible = showControls && playerErrorMessage == null && !isPreparing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .clickable {
                        controlsAutoDismissTimer = System.currentTimeMillis()
                        val vv = videoViewRef
                        if (vv != null) {
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Bottom Custom Media Controller Bar
        AnimatedVisibility(
            visible = showControls && playerErrorMessage == null && !isPreparing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Slider Scrubber Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Slider(
                        value = sliderPosition,
                        onValueChange = { newPos ->
                            controlsAutoDismissTimer = System.currentTimeMillis()
                            isUserDraggingSlider = true
                            sliderPosition = newPos
                            if (durationMs > 0) {
                                currentPositionMs = (newPos * durationMs).toInt()
                            }
                        },
                        onValueChangeFinished = {
                            isUserDraggingSlider = false
                            if (durationMs > 0) {
                                val targetSeekMs = (sliderPosition * durationMs).toInt()
                                videoViewRef?.seekTo(targetSeekMs)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = WhatsappGreen,
                            activeTrackColor = WhatsappGreen,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )

                    Text(
                        text = formatTime(durationMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Controls Row: Play/Pause, Mute/Unmute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            controlsAutoDismissTimer = System.currentTimeMillis()
                            val vv = videoViewRef
                            if (vv != null) {
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Mute / Unmute Button
                    IconButton(
                        onClick = {
                            controlsAutoDismissTimer = System.currentTimeMillis()
                            isMuted = !isMuted
                            mediaPlayerRef?.let { mp ->
                                if (isMuted) {
                                    mp.setVolume(0f, 0f)
                                } else {
                                    mp.setVolume(1f, 1f)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun downloadMediaToGallery(context: Context, message: ChatMessage) {
    val rawUrl = message.mediaUrl ?: return
    val isVideo = message.messageType == MessageType.VIDEO
    val extension = if (isVideo) "mp4" else "jpg"
    val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
    val filename = "Talkly_${System.currentTimeMillis()}.$extension"

    Toast.makeText(context, "Saving ${if (isVideo) "video" else "photo"} to Gallery...", Toast.LENGTH_SHORT).show()

    CoroutineScope(Dispatchers.IO).launch {
        var success = false
        var errorMessage: String? = null
        try {
            val inputStream: InputStream? = when {
                rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> {
                    val conn = URL(rawUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.doInput = true
                    conn.connect()
                    if (conn.responseCode in 200..299) {
                        conn.inputStream
                    } else {
                        throw java.io.IOException("Server returned HTTP ${conn.responseCode}")
                    }
                }
                rawUrl.startsWith("data:", ignoreCase = true) -> {
                    val base64Data = rawUrl.substringAfter("base64,", "")
                    if (base64Data.isNotBlank()) {
                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                        java.io.ByteArrayInputStream(bytes)
                    } else {
                        null
                    }
                }
                rawUrl.startsWith("content://", ignoreCase = true) || rawUrl.startsWith("file://", ignoreCase = true) -> {
                    context.contentResolver.openInputStream(Uri.parse(rawUrl))
                }
                else -> {
                    val cached = VideoCacheManager.getCachedVideoFile(context, rawUrl)
                    if (cached != null && cached.exists()) {
                        cached.inputStream()
                    } else {
                        val file = File(rawUrl)
                        if (file.exists()) file.inputStream() else null
                    }
                }
            }

            if (inputStream == null) {
                throw java.io.IOException("Unable to open media stream")
            }

            inputStream.use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            if (isVideo) Environment.DIRECTORY_MOVIES + "/Talkly" else Environment.DIRECTORY_PICTURES + "/Talkly"
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val collection = if (isVideo) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }

                    val itemUri = context.contentResolver.insert(collection, values)
                    if (itemUri != null) {
                        context.contentResolver.openOutputStream(itemUri)?.use { output ->
                            input.copyTo(output)
                        }
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(itemUri, values, null, null)
                        success = true
                    }
                } else {
                    val targetDir = File(
                        Environment.getExternalStoragePublicDirectory(
                            if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                        ),
                        "Talkly"
                    )
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val targetFile = File(targetDir, filename)
                    java.io.FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                    success = true
                }
            }
        } catch (e: Exception) {
            Log.e("FullMediaViewer", "Failed to download media to gallery: ${e.message}", e)
            errorMessage = e.localizedMessage ?: "Unknown error"
        }

        withContext(Dispatchers.Main) {
            if (success) {
                Toast.makeText(context, "Saved to Gallery (Talkly album)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Save failed: ${errorMessage ?: "Could not write file"}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
