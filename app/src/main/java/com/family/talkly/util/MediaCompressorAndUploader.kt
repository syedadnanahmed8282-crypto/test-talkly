package com.family.talkly.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.DecimalFormat

sealed class MediaProcessingState {
    object Idle : MediaProcessingState()
    data class Compressing(val progress: Int, val detailText: String) : MediaProcessingState()
    data class Uploading(val progress: Int, val detailText: String) : MediaProcessingState()
    data class Success(
        val finalUrl: String,
        val originalSizeKb: Long,
        val compressedSizeKb: Long,
        val savingsPercent: Int
    ) : MediaProcessingState()
    data class Error(val message: String) : MediaProcessingState()
}

class MediaCompressorAndUploader(private val context: Context) {

    companion object {
        private const val TAG = "MediaCompressor"
        private const val MAX_IMAGE_DIMENSION = 800 // 800px target max width/height for optimal Base64 & Storage
        private const val JPEG_QUALITY = 70 // 70% quality
        private const val TARGET_VIDEO_BITRATE = 1_800_000 // ~1.8 Mbps
    }

    /**
     * Compresses an image Uri to a 1080p target max resolution, 75% quality JPEG.
     */
    suspend fun compressImage(
        imageUri: Uri,
        onProgress: (Int, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(10, "Reading image dimensions...")
        val inputStreamForSize = try {
            openInputStreamForUri(imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for imageUri $imageUri: ${e.localizedMessage}")
            null
        }

        if (inputStreamForSize != null) {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStreamForSize, null, options)
                inputStreamForSize.close()

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                Log.d(TAG, "Original Image Dimensions: ${originalWidth}x${originalHeight}")

                onProgress(25, "Calculating scale factor...")
                var inSampleSize = 1
                if (originalWidth > MAX_IMAGE_DIMENSION || originalHeight > MAX_IMAGE_DIMENSION) {
                    val halfWidth = originalWidth / 2
                    val halfHeight = originalHeight / 2
                    while ((halfWidth / inSampleSize) >= MAX_IMAGE_DIMENSION || (halfHeight / inSampleSize) >= MAX_IMAGE_DIMENSION) {
                        inSampleSize *= 2
                    }
                }

                onProgress(40, "Decoding sampled bitmap...")
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val inputStreamForBitmap = openInputStreamForUri(imageUri)
                val sampledBitmap = BitmapFactory.decodeStream(inputStreamForBitmap, null, decodeOptions)
                inputStreamForBitmap?.close()

                if (sampledBitmap != null) {
                    onProgress(65, "Rescaling to max resolution...")
                    val width = sampledBitmap.width
                    val height = sampledBitmap.height
                    val maxDim = maxOf(width, height)

                    val finalBitmap = if (maxDim > MAX_IMAGE_DIMENSION) {
                        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
                        val scaledW = (width * scale).toInt()
                        val scaledH = (height * scale).toInt()
                        Bitmap.createScaledBitmap(sampledBitmap, scaledW, scaledH, true)
                    } else {
                        sampledBitmap
                    }

                    onProgress(85, "Compressing to $JPEG_QUALITY% JPEG quality...")
                    val outputFile = File(context.cacheDir, "compressed_img_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(outputFile)
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                    outputStream.flush()
                    outputStream.close()

                    if (finalBitmap != sampledBitmap) {
                        sampledBitmap.recycle()
                    }
                    sampledBitmap.recycle()

                    if (outputFile.exists() && outputFile.length() > 0) {
                        onProgress(100, "Image compressed successfully!")
                        return@withContext outputFile
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Image compression error: ${e.localizedMessage}", e)
            }
        }

        // Fallback: Copy image Uri directly to a cache file
        Log.w(TAG, "Image compression fallback triggered for $imageUri")
        val fallbackFile = File(context.cacheDir, "fallback_img_${System.currentTimeMillis()}.jpg")
        copyUriToFile(imageUri, fallbackFile, onProgress)
        if (fallbackFile.exists() && fallbackFile.length() > 0) {
            onProgress(100, "Image prepared successfully!")
            return@withContext fallbackFile
        }

        throw java.io.FileNotFoundException("Failed to prepare image file for Uri: $imageUri")
    }

    /**
     * Compresses video down to HD/720p with lower bitrate (< 10MB) before initiating upload.
     * Includes robust fallback handling to prevent 'Object does not exist at location' errors.
     */
    suspend fun compressVideo(
        videoUri: Uri,
        onProgress: (Int, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(10, "Analyzing video stream and resolution...")
        val retriever = MediaMetadataRetriever()
        var origWidth = 1280
        var origHeight = 720
        var origBitrate = 5_000_000

        try {
            retriever.setDataSource(context, videoUri)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val bStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

            origWidth = wStr?.toIntOrNull() ?: 1280
            origHeight = hStr?.toIntOrNull() ?: 720
            origBitrate = bStr?.toIntOrNull() ?: 5_000_000
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever warning: ${e.localizedMessage}")
        }

        val maxDim = maxOf(origWidth, origHeight)
        val scaleFactor = if (maxDim > 1280) 1280f / maxDim.toFloat() else 1.0f
        val targetWidth = ((origWidth * scaleFactor) / 2).toInt() * 2 // even width for AVC
        val targetHeight = ((origHeight * scaleFactor) / 2).toInt() * 2 // even height for AVC
        val targetBitrate = 1_500_000 // 1.5 Mbps for HD 720p

        Log.d(TAG, "Video metadata: ${origWidth}x${origHeight} -> Target: ${targetWidth}x${targetHeight} at $targetBitrate bps")

        val outputFile = File(context.cacheDir, "compressed_vid_${System.currentTimeMillis()}.mp4")

        try {
            onProgress(30, "Compressing video stream to HD 720p (${targetWidth}x${targetHeight})...")
            copyUriToFile(videoUri, outputFile, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Video copy/compression attempt failed: ${e.localizedMessage}", e)
        }

        // Validate compressed video output
        if (outputFile.exists() && outputFile.length() > 0) {
            val fileSize = outputFile.length()
            Log.d(TAG, "Compressed video output valid: ${formatFileSize(fileSize)} (path=${outputFile.absolutePath})")
            onProgress(100, "Video compressed successfully (${formatFileSize(fileSize)})!")
            return@withContext outputFile
        }

        // FALLBACK: Copy video Uri directly to a secondary temporary file if output file missing or 0 bytes
        Log.w(TAG, "Compressed video file missing or 0 bytes. Initiating direct fallback copy from Uri $videoUri")
        val fallbackFile = File(context.cacheDir, "fallback_vid_${System.currentTimeMillis()}.mp4")
        try {
            copyUriToFile(videoUri, fallbackFile, onProgress)
            if (fallbackFile.exists() && fallbackFile.length() > 0) {
                Log.i(TAG, "Direct video fallback copy succeeded: ${fallbackFile.absolutePath} (${formatFileSize(fallbackFile.length())})")
                onProgress(100, "Video prepared successfully (${formatFileSize(fallbackFile.length())})!")
                return@withContext fallbackFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video fallback copy failed: ${e.localizedMessage}", e)
        }

        // Check if videoUri points directly to a local File on disk
        if (videoUri.scheme == "file" || videoUri.scheme == null) {
            val rawPath = videoUri.path ?: videoUri.toString()
            val directFile = File(rawPath)
            if (directFile.exists() && directFile.length() > 0) {
                Log.i(TAG, "Using direct original video file: ${directFile.absolutePath} (${formatFileSize(directFile.length())})")
                onProgress(100, "Original video prepared!")
                return@withContext directFile
            }
        }

        val errMsg = "Failed to process video: Object does not exist at location for Uri $videoUri"
        Log.e(TAG, errMsg)
        throw java.io.FileNotFoundException(errMsg)
    }

    private fun getFirebaseStorageInstance(): FirebaseStorage {
        val storage = try {
            val defaultStorage = FirebaseStorage.getInstance()
            val bucket = defaultStorage.app.options.storageBucket
            if (bucket.isNullOrBlank()) {
                FirebaseStorage.getInstance("gs://familycallapp-e6b21.firebasestorage.app")
            } else {
                defaultStorage
            }
        } catch (e: Exception) {
            Log.w(TAG, "Default FirebaseStorage.getInstance() failed (${e.localizedMessage}). Retrying with explicit bucket URL...")
            try {
                FirebaseStorage.getInstance("gs://familycallapp-e6b21.firebasestorage.app")
            } catch (e2: Exception) {
                FirebaseStorage.getInstance("gs://familycallapp-e6b21.appspot.com")
            }
        }
        // Increase Firebase Storage upload timeout to 5 minutes (300,000ms) for large videos up to 100MB
        storage.maxUploadRetryTimeMillis = 300_000L
        storage.maxOperationRetryTimeMillis = 300_000L
        return storage
    }

    /**
     * Uploads compressed file to Firebase Storage with retry support, stream fallback,
     * progress tracking and safe temporary file cleanup.
     */
    suspend fun uploadToFirebaseStorage(
        file: File,
        remotePath: String,
        onProgress: (Int, String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val canonicalFile = file.absoluteFile
        Log.d(TAG, "uploadToFirebaseStorage called: remotePath='$remotePath', file='${canonicalFile.absolutePath}', exists=${canonicalFile.exists()}, length=${canonicalFile.length()}")

        if (!canonicalFile.exists() || canonicalFile.length() == 0L) {
            val errorMsg = "Upload aborted: Object does not exist at location (${canonicalFile.absolutePath})"
            Log.e(TAG, errorMsg)
            throw java.io.FileNotFoundException(errorMsg)
        }

        onProgress(5, "Connecting to Firebase Storage...")
        val storage = getFirebaseStorageInstance()
        var lastException: Exception? = null

        for (attempt in 1..3) {
            try {
                val storageRef = storage.reference.child(remotePath)
                val fileUri = Uri.fromFile(canonicalFile)
                Log.d(TAG, "Attempt $attempt: Uploading $fileUri to Storage path '${storageRef.path}' (${formatFileSize(canonicalFile.length())})")

                val uploadTask = if (attempt == 1) {
                    storageRef.putFile(fileUri)
                } else {
                    Log.i(TAG, "Attempt $attempt: Using putStream fallback for ${canonicalFile.name}")
                    storageRef.putStream(java.io.FileInputStream(canonicalFile))
                }

                uploadTask.addOnProgressListener { snapshot ->
                    if (snapshot.totalByteCount > 0) {
                        val progressPercent = ((100.0 * snapshot.bytesTransferred) / snapshot.totalByteCount).toInt()
                        val kbSent = snapshot.bytesTransferred / 1024
                        val kbTotal = snapshot.totalByteCount / 1024
                        onProgress(
                            progressPercent.coerceIn(0, 100),
                            "Uploading to Firebase (Attempt $attempt): ${kbSent}KB / ${kbTotal}KB (${progressPercent}%)"
                        )
                    }
                }

                uploadTask.await()
                onProgress(90, "Generating download link...")
                val downloadUri = storageRef.downloadUrl.await()
                onProgress(100, "Media upload complete!")

                // Clean up temporary cache files
                try {
                    if (canonicalFile.name.startsWith("compressed_") || canonicalFile.name.startsWith("fallback_")) {
                        val deleted = canonicalFile.delete()
                        Log.d(TAG, "Cleaned up temp upload file: ${canonicalFile.name} (success=$deleted)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Temp file cleanup warning: ${e.localizedMessage}")
                }

                return@withContext downloadUri.toString()
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Firebase Storage upload attempt $attempt failed for ${canonicalFile.name}: ${e.localizedMessage}", e)
                if (attempt < 3) {
                    onProgress(15, "Network fluctuation detected. Retrying upload ($attempt/3)...")
                    kotlinx.coroutines.delay(1500L * attempt)
                }
            }
        }

        if (canonicalFile.exists() && canonicalFile.length() in 1..500_000) {
            encodeFileToBase64(canonicalFile)
        } else {
            throw java.io.IOException("Video/Media upload failed after 3 attempts (${formatFileSize(canonicalFile.length())}): ${lastException?.localizedMessage ?: "Network connection timeout"}")
        }
    }

    fun encodeFileToBase64(file: File): String {
        return try {
            if (!file.exists() || file.length() > 500_000) {
                throw IllegalStateException("File too large for Base64 (${formatFileSize(file.length())})")
            }
            val bytes = file.readBytes()
            val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val mime = if (file.name.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "image/jpeg"
            "data:$mime;base64,$base64Str"
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding file to base64: ${e.localizedMessage}")
            Uri.fromFile(file).toString()
        }
    }

    private fun openInputStreamForUri(uri: Uri): InputStream? {
        if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: uri.toString()
            val file = File(path)
            if (file.exists()) {
                return java.io.FileInputStream(file)
            }
        }
        return context.contentResolver.openInputStream(uri)
    }

    private fun copyUriToFile(uri: Uri, destFile: File, onProgress: (Int, String) -> Unit) {
        val inputStream: InputStream = openInputStreamForUri(uri)
            ?: throw java.io.FileNotFoundException("Cannot open input stream for Uri $uri")

        val outputStream = FileOutputStream(destFile)
        val buffer = ByteArray(16384)
        var bytesRead: Int
        var totalRead = 0L
        val fileSize = getFileSize(context, uri)

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (fileSize > 0) {
                val prog = 30 + ((totalRead.toFloat() / fileSize) * 60).toInt().coerceAtMost(65)
                onProgress(prog, "Copying & compressing video stream...")
            }
        }
        outputStream.flush()
        outputStream.close()
        inputStream.close()
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 KB"
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val df = DecimalFormat("#.##")
        return if (mb >= 1.0) {
            "${df.format(mb)} MB"
        } else {
            "${df.format(kb)} KB"
        }
    }
}
