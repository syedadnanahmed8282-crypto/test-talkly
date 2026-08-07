package com.family.talkly.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
     * Compresses an image Uri safely handling EXIF rotation, downsampling, and recycling bitmaps to prevent memory leaks.
     */
    suspend fun compressImage(
        imageUri: Uri,
        onProgress: (Int, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        try {
            onProgress(10, "Reading image properties...")

            // 1. Read EXIF Orientation
            val rotationDegrees = getRotationDegrees(imageUri)

            // 2. Decode image dimensions safely
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInputStreamForUri(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                throw IllegalArgumentException("Invalid image dimensions")
            }

            // 3. Downsample ratio calculation
            var inSampleSize = 1
            if (originalWidth > MAX_IMAGE_DIMENSION || originalHeight > MAX_IMAGE_DIMENSION) {
                val halfWidth = originalWidth / 2
                val halfHeight = originalHeight / 2
                while ((halfWidth / inSampleSize) >= MAX_IMAGE_DIMENSION || (halfHeight / inSampleSize) >= MAX_IMAGE_DIMENSION) {
                    inSampleSize *= 2
                }
            }

            onProgress(30, "Decoding image...")
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            var sampledBitmap: Bitmap? = null
            openInputStreamForUri(imageUri)?.use { stream ->
                sampledBitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
            }

            sampledBitmap?.let { bitmap ->
                onProgress(60, "Resizing and Rotating...")

                // 4. Handle Rotation & Resizing atomically
                val rotatedAndScaledBitmap = transformBitmap(bitmap, rotationDegrees)

                // Recycle intermediate bitmap if a new transformed bitmap was created
                if (rotatedAndScaledBitmap != bitmap) {
                    bitmap.recycle()
                }

                onProgress(80, "Saving compressed image...")
                val outputFile = File(context.cacheDir, "compressed_img_${System.currentTimeMillis()}.jpg")

                FileOutputStream(outputFile).use { outputStream ->
                    rotatedAndScaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                    outputStream.flush()
                }

                // Recycle final bitmap memory
                rotatedAndScaledBitmap.recycle()

                onProgress(100, "Done")
                return@withContext outputFile
            }

        } catch (e: Exception) {
            Log.e(TAG, "Image compression error: ${e.localizedMessage}", e)
        }

        // Fallback if decoding fails
        Log.w(TAG, "Image compression fallback triggered for $imageUri")
        onProgress(50, "Falling back to direct copy...")
        val fallbackFile = File(context.cacheDir, "fallback_img_${System.currentTimeMillis()}.jpg")
        copyUriToFile(imageUri, fallbackFile, onProgress)
        return@withContext fallbackFile
    }

    // Helper to extract EXIF rotation degrees
    private fun getRotationDegrees(imageUri: Uri): Float {
        return try {
            openInputStreamForUri(imageUri)?.use { stream ->
                val exifInterface = ExifInterface(stream)
                when (exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
    }

    // Helper for matrix scaling and rotation
    private fun transformBitmap(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = maxOf(width, height)

        val matrix = Matrix()

        if (maxDim > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
            matrix.postScale(scale, scale)
        }

        if (rotationDegrees != 0f) {
            matrix.postRotate(rotationDegrees)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    /**
     * Compresses video down to WhatsApp/Imo standard HD 720p (H.264 AVC + AAC audio) with 2.0 Mbps target bitrate.
     * Operates asynchronously on background thread with hardware encoder transcoding and fallback direct copy.
     */
    suspend fun compressVideo(
        videoUri: Uri,
        onProgress: (Int, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(5, "Analyzing video stream...")

        val retriever = MediaMetadataRetriever()
        var origWidth = 1280
        var origHeight = 720
        var rotation = 0

        try {
            retriever.setDataSource(context, videoUri)
            origWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            origHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Metadata retrieval error: ${e.localizedMessage}")
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        // 1. Calculate WhatsApp Standard Target Dimensions (720p Max)
        val maxDim = maxOf(origWidth, origHeight)
        val scaleFactor = if (maxDim > 1280) 1280f / maxDim.toFloat() else 1.0f
        val targetWidth = ((origWidth * scaleFactor) / 2).toInt() * 2 // Must be even integer for H.264
        val targetHeight = ((origHeight * scaleFactor) / 2).toInt() * 2

        val outputFile = File(context.cacheDir, "compressed_vid_${System.currentTimeMillis()}.mp4")

        onProgress(20, "Compressing video (H.264 / AAC)...")

        try {
            // 2. Perform Real Video Transcoding using Hardware Encoder
            val success = executeVideoTranscoding(
                inputUri = videoUri,
                outputFile = outputFile,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                bitrate = 2_000_000, // 2 Mbps target bitrate for crisp quality & small size
                onProgressCallback = { percent ->
                    onProgress(20 + (percent * 0.75).toInt(), "Compressing: $percent%")
                }
            )

            if (success && outputFile.exists() && outputFile.length() > 0) {
                onProgress(100, "Compression complete")
                return@withContext outputFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hardware compression failed, falling back: ${e.localizedMessage}")
        }

        // Fallback if hardware transcoding fails
        onProgress(50, "Direct copying fallback...")
        copyUriToFile(videoUri, outputFile, onProgress)
        return@withContext outputFile
    }

    private suspend fun executeVideoTranscoding(
        inputUri: Uri,
        outputFile: File,
        targetWidth: Int,
        targetHeight: Int,
        bitrate: Int,
        onProgressCallback: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            if (inputUri.scheme == "file" || inputUri.scheme == null) {
                extractor.setDataSource(inputUri.path ?: inputUri.toString())
            } else {
                context.contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: run {
                    extractor.setDataSource(context, inputUri, null)
                }
            }

            val trackCount = extractor.trackCount
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            var durationUs = 0L

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    videoFormat = format
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null) {
                Log.w(TAG, "No video track found in Uri: $inputUri")
                return@withContext false
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val videoCodec = try {
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            } catch (e: Exception) {
                Log.w(TAG, "Hardware AVC encoder creation failed: ${e.localizedMessage}")
                null
            }

            if (videoCodec != null && durationUs > 0) {
                videoCodec.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = videoCodec.createInputSurface()
                videoCodec.start()

                val mimeType = videoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
                val decoder = MediaCodec.createDecoderByType(mimeType)
                decoder.configure(videoFormat, inputSurface, null, 0)
                decoder.start()

                extractor.selectTrack(videoTrackIndex)

                val bufferInfo = MediaCodec.BufferInfo()
                var isEncoderEOS = false
                var isDecoderEOS = false
                var muxerStarted = false
                var muxerVideoIndex = -1
                var muxerAudioIndex = -1

                if (audioTrackIndex >= 0 && audioFormat != null) {
                    muxerAudioIndex = muxer.addTrack(audioFormat)
                }

                var lastProgress = 0
                val timeoutUs = 10000L

                while (!isEncoderEOS) {
                    if (!isDecoderEOS) {
                        val inBufIdx = decoder.dequeueInputBuffer(timeoutUs)
                        if (inBufIdx >= 0) {
                            val inputBuf = decoder.getInputBuffer(inBufIdx)
                            if (inputBuf != null) {
                                val sampleSize = extractor.readSampleData(inputBuf, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(inBufIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    isDecoderEOS = true
                                } else {
                                    val presentationTimeUs = extractor.sampleTime
                                    decoder.queueInputBuffer(inBufIdx, 0, sampleSize, presentationTimeUs, 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val decBufIdx = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (decBufIdx >= 0) {
                        val doRender = bufferInfo.size != 0
                        decoder.releaseOutputBuffer(decBufIdx, doRender)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            videoCodec.signalEndOfInputStream()
                        }
                    }

                    val encBufIdx = videoCodec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (encBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            muxerVideoIndex = muxer.addTrack(videoCodec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (encBufIdx >= 0) {
                        val encodedData = videoCodec.getOutputBuffer(encBufIdx)
                        if (encodedData != null && muxerStarted) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerVideoIndex, encodedData, bufferInfo)

                                val progress = ((bufferInfo.presentationTimeUs.toDouble() / durationUs.toDouble()) * 100).toInt().coerceIn(0, 99)
                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    onProgressCallback(progress)
                                }
                            }
                        }
                        videoCodec.releaseOutputBuffer(encBufIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEncoderEOS = true
                        }
                    }
                }

                if (audioTrackIndex >= 0 && muxerAudioIndex >= 0 && muxerStarted) {
                    extractor.unselectTrack(videoTrackIndex)
                    extractor.selectTrack(audioTrackIndex)
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val audioBuf = ByteBuffer.allocate(256 * 1024)
                    val audioBufferInfo = MediaCodec.BufferInfo()
                    while (true) {
                        val sampleSize = extractor.readSampleData(audioBuf, 0)
                        if (sampleSize < 0) break
                        audioBufferInfo.offset = 0
                        audioBufferInfo.size = sampleSize
                        audioBufferInfo.presentationTimeUs = extractor.sampleTime
                        audioBufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerAudioIndex, audioBuf, audioBufferInfo)
                        extractor.advance()
                    }
                }

                try { decoder.stop(); decoder.release() } catch (e: Exception) {}
                try { videoCodec.stop(); videoCodec.release() } catch (e: Exception) {}

                onProgressCallback(100)
                return@withContext true
            } else {
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeVideoTranscoding error: ${e.localizedMessage}", e)
            return@withContext false
        } finally {
            try { extractor?.release() } catch (e: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
        }
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
            FirebaseStorage.getInstance("gs://familycallapp-e6b21.firebasestorage.app")
        }

        // Allow extended network retry durations for high-payload media streams
        storage.maxUploadRetryTimeMillis = 600_000L // 10 minutes total retry limit
        storage.maxOperationRetryTimeMillis = 300_000L // 5 minutes max per operation
        return storage
    }

    /**
     * Uploads compressed file to Firebase Storage with native resumable putFile upload,
     * smooth snapshot progress reporting, and coroutine retry handling.
     */
    suspend fun uploadToFirebaseStorage(
        file: File,
        remotePath: String,
        onProgress: (Int, String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val canonicalFile = file.absoluteFile
        Log.d(TAG, "uploadToFirebaseStorage called: remotePath='$remotePath', file='${canonicalFile.absolutePath}', exists=${canonicalFile.exists()}, length=${canonicalFile.length()}")

        if (!canonicalFile.exists() || canonicalFile.length() == 0L) {
            val errorMsg = "Upload aborted: File does not exist at location (${canonicalFile.absolutePath})"
            Log.e(TAG, errorMsg)
            throw java.io.FileNotFoundException(errorMsg)
        }

        val storage = getFirebaseStorageInstance()
        val storageRef = storage.reference.child(remotePath)
        val fileUri = Uri.fromFile(canonicalFile)
        val totalBytes = canonicalFile.length()

        var lastException: Exception? = null

        // Perform native resumable upload using standard putFile (do NOT use putStream)
        for (attempt in 1..3) {
            try {
                onProgress(0, "Initiating upload session...")

                val uploadTask = storageRef.putFile(fileUri)

                uploadTask.addOnProgressListener { snapshot ->
                    val bytesSent = snapshot.bytesTransferred
                    val total = if (snapshot.totalByteCount > 0) snapshot.totalByteCount else totalBytes
                    if (total > 0) {
                        val progressPercent = ((100.0 * bytesSent) / total).toInt().coerceIn(0, 100)
                        onProgress(
                            progressPercent,
                            "Uploading (${formatFileSize(bytesSent)} / ${formatFileSize(total)})$progressPercent%"
                        )
                    }
                }.addOnFailureListener { exception ->
                    Log.e("FirebaseStorage", "Upload failed: ${exception.localizedMessage}")
                }

                // Await upload completion safely
                uploadTask.await()

                // Fetch final download URL
                val downloadUri = storageRef.downloadUrl.await()
                onProgress(100, "Media upload complete!")

                // Clean up temporary compressed file ONLY after successful download URL generation
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

                // Check if it's a recoverable storage exception
                if (e is StorageException || e is IOException) {
                    if (attempt < 3) {
                        val backoffMs = 2000L * attempt
                        onProgress(10, "Connection dropped. Re-establishing session ($attempt/3)...")
                        kotlinx.coroutines.delay(backoffMs)
                        continue
                    }
                } else {
                    throw e // Non-network failure, break immediately
                }
            }
        }

        throw IOException("Video upload failed after retries: ${lastException?.localizedMessage}")
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
