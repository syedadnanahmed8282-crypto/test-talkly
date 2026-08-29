package com.family.talkly.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object PhoneUtils {
    /**
     * Cleans a phone number by removing spaces, dashes, brackets, plus signs, and any non-digit character.
     */
    fun cleanPhoneNumber(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }

    /**
     * Extracts the LAST 10 DIGITS (phoneSuffix) from any given phone number.
     * E.g., '+8801712345678' -> '1712345678', '01712345678' -> '1712345678'.
     */
    fun extractPhoneSuffix(phone: String): String {
        val clean = cleanPhoneNumber(phone)
        return if (clean.length > 10) {
            clean.takeLast(10)
        } else {
            clean
        }
    }

    /**
     * Formats last seen timestamp into clean time string (e.g., '10:15 AM', 'Yesterday, 8:30 PM', 'Wednesday, 3:00 PM', '12 Oct, 5:45 PM') without any 'Last seen' or 'at' prefixes.
     */
    fun formatLastSeenTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Recently"
        val now = System.currentTimeMillis()
        val calNow = java.util.Calendar.getInstance()
        val calThen = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }

        val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        val timeStr = timeFormat.format(java.util.Date(timestamp))

        val isSameYear = calNow.get(java.util.Calendar.YEAR) == calThen.get(java.util.Calendar.YEAR)
        val isSameDay = isSameYear && calNow.get(java.util.Calendar.DAY_OF_YEAR) == calThen.get(java.util.Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            return timeStr
        }

        calNow.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val isYesterday = isSameYear && calNow.get(java.util.Calendar.DAY_OF_YEAR) == calThen.get(java.util.Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "Yesterday, $timeStr"
        }

        val daysDiff = (now - timestamp) / (24 * 60 * 60 * 1000L)
        if (daysDiff < 7 && daysDiff >= 0) {
            val dayFormat = java.text.SimpleDateFormat("EEEE, h:mm a", java.util.Locale.US)
            return dayFormat.format(java.util.Date(timestamp))
        }

        val pattern = if (isSameYear) "d MMM, h:mm a" else "d MMM yyyy, h:mm a"
        val dateFormat = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
        return dateFormat.format(java.util.Date(timestamp))
    }

    /**
     * Appends dynamic cache-busting query parameter to image URLs/Uris.
     * Guarantees client-side image loaders (Coil/Glide) treat updated photos as new resources and purge old caches.
     */
    fun appendCacheBuster(url: String?, timestamp: Long = System.currentTimeMillis()): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("data:") || url.startsWith("base64:")) return url
        if (url.contains("v=") || url.contains("cb=")) {
            return url.replace(Regex("([?&])(v|cb)=\\d+"), "$1$2=$timestamp")
        }
        val separator = if (url.contains("?")) "&" else "?"
        return "${url}${separator}v=${timestamp}"
    }

    /**
     * Decodes Base64 data strings or returns URL/Uri for Coil AsyncImage model.
     * Prevents blank images when media is sent as Base64 fallback or data URI across devices.
     */
    fun getCoilMediaModel(mediaUrl: String?): Any? {
        if (mediaUrl.isNullOrBlank()) return null
        if (mediaUrl.startsWith("data:") || mediaUrl.startsWith("base64:")) {
            val commaIndex = mediaUrl.indexOf(",")
            val base64Str = if (commaIndex != -1) mediaUrl.substring(commaIndex + 1) else mediaUrl.removePrefix("base64:")
            return try {
                android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                mediaUrl
            }
        }
        return appendCacheBuster(mediaUrl) ?: mediaUrl
    }

    /**
     * Extracts a frame thumbnail from video URL, Uri or base64 stream safely.
     */
    fun getVideoThumbnail(context: Context, videoUrl: String?): Bitmap? {
        if (videoUrl.isNullOrBlank()) return null
        val cachedFile = VideoCacheManager.getCachedVideoFile(context, videoUrl)
        if (cachedFile != null && cachedFile.exists()) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(cachedFile.absolutePath)
                retriever.frameAtTime
            } catch (e: Throwable) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        val retriever = MediaMetadataRetriever()
        var tempCreatedFile: File? = null
        return try {
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                retriever.setDataSource(videoUrl, HashMap<String, String>())
            } else if (videoUrl.startsWith("content://") || videoUrl.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoUrl))
            } else if (videoUrl.startsWith("data:") || videoUrl.startsWith("base64:")) {
                if (videoUrl.length > 800_000) return null
                val commaIndex = videoUrl.indexOf(",")
                val base64Str = if (commaIndex != -1) videoUrl.substring(commaIndex + 1) else videoUrl.removePrefix("base64:")
                val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val tempFile = File.createTempFile("v_thumb_${videoUrl.hashCode()}", ".mp4", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(bytes) }
                tempCreatedFile = tempFile
                retriever.setDataSource(tempFile.absolutePath)
            } else {
                retriever.setDataSource(videoUrl)
            }
            val frame = retriever.frameAtTime
            frame
        } catch (e: Throwable) {
            Log.w("PhoneUtils", "Error getting video thumbnail: ${e.localizedMessage}")
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            tempCreatedFile?.let {
                try { it.delete() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Converts a media URL, Uri or Base64 string into a playable Uri.
     */
    fun getMediaUri(context: Context, mediaUrl: String?): Uri? {
        if (mediaUrl.isNullOrBlank()) return null
        val cached = VideoCacheManager.getCachedVideoFile(context, mediaUrl)
        if (cached != null && cached.exists()) {
            return Uri.fromFile(cached)
        }
        return try {
            if (mediaUrl.startsWith("data:") || mediaUrl.startsWith("base64:")) {
                val commaIndex = mediaUrl.indexOf(",")
                val base64Str = if (commaIndex != -1) mediaUrl.substring(commaIndex + 1) else mediaUrl.removePrefix("base64:")
                val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val ext = if (mediaUrl.contains("video", ignoreCase = true)) ".mp4" else ".jpg"
                val tempFile = File(context.cacheDir, "temp_media_${mediaUrl.hashCode()}$ext")
                if (!tempFile.exists()) {
                    FileOutputStream(tempFile).use { it.write(bytes) }
                }
                Uri.fromFile(tempFile)
            } else if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://") || mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://")) {
                Uri.parse(mediaUrl)
            } else {
                Uri.fromFile(File(mediaUrl))
            }
        } catch (e: Exception) {
            null
        }
    }
}

