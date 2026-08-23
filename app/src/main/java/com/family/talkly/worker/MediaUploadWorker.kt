package com.family.talkly.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.family.talkly.data.firebase.FirebaseChatRepository
import com.family.talkly.data.local.TalklyDatabase
import com.family.talkly.data.local.entity.ChatMessageEntity
import com.family.talkly.data.models.MessageType
import com.family.talkly.util.MediaCompressorAndUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaUploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MediaUploadWorker"
        const val CHANNEL_ID = "talkly_media_upload_channel"
        const val NOTIFICATION_ID_BASE = 88000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val messageId = inputData.getString("message_id") ?: return@withContext Result.failure()
        val chatKey = inputData.getString("chat_key") ?: return@withContext Result.failure()
        val recipientId = inputData.getString("recipient_id") ?: ""
        val senderUid = inputData.getString("sender_uid") ?: ""
        val senderName = inputData.getString("sender_name") ?: ""
        val typeStr = inputData.getString("message_type") ?: MessageType.VIDEO.name
        val localMediaUrl = inputData.getString("local_media_url") ?: return@withContext Result.failure()
        val textContent = inputData.getString("text_content") ?: ""
        val replyToId = inputData.getString("reply_to_id")
        val replyToName = inputData.getString("reply_to_name")
        val replyToText = inputData.getString("reply_to_text")

        Log.e(TAG, "MediaUploadWorker START doWork() messageId=$messageId type=$typeStr chatKey=$chatKey recipientId=$recipientId senderUid=$senderUid senderName=$senderName localMediaUrl=$localMediaUrl")

        val notificationId = NOTIFICATION_ID_BASE + (messageId.hashCode() and 0x7FFFFFFF) % 10000
        createNotificationChannel()

        val messageType = try {
            MessageType.valueOf(typeStr)
        } catch (e: Exception) {
            MessageType.VIDEO
        }

        val db = TalklyDatabase.getInstance(appContext)
        val dao = db.chatMessageDao()
        val uploader = MediaCompressorAndUploader(appContext)

        // Set foreground info for persistent background execution even if app is killed
        try {
            val initialNotification = createForegroundInfo(notificationId, "Compressing & uploading video...", 5)
            setForeground(initialNotification)
        } catch (e: Exception) {
            Log.w(TAG, "Foreground notification start exception: ${e.localizedMessage}")
        }

        try {
            // 1. Ensure local DB has initial pending/uploading entity
            val existing = dao.getMessageById(messageId)
            if (existing == null) {
                val entity = ChatMessageEntity(
                    id = messageId,
                    chatKey = chatKey,
                    senderId = senderUid.ifBlank { "self" },
                    senderName = senderName.ifBlank { "You" },
                    receiverId = recipientId,
                    messageType = messageType.name,
                    textContent = textContent,
                    mediaUrl = localMediaUrl,
                    timestamp = System.currentTimeMillis(),
                    isPending = true,
                    isUploading = true,
                    isFailed = false,
                    uploadProgress = 5,
                    replyToMessageId = replyToId,
                    replyToSenderName = replyToName,
                    replyToText = replyToText
                )
                dao.insertMessage(entity)
            } else {
                dao.updateUploadState(
                    messageId = messageId,
                    isPending = true,
                    isUploading = true,
                    isFailed = false,
                    uploadProgress = 5,
                    mediaUrl = localMediaUrl
                )
            }

            // 2. Perform client-side video/media compression & resumable background upload
            var finalRemoteUrl = localMediaUrl
            val uri = if (localMediaUrl.startsWith("/")) Uri.fromFile(File(localMediaUrl)) else Uri.parse(localMediaUrl)

            if (messageType == MessageType.VIDEO) {
                val compressedFile = uploader.compressVideo(uri) { progress, statusText ->
                    val overallProgress = ((progress / 100.0) * 30).toInt().coerceIn(0, 30)
                    updateProgressState(dao, messageId, overallProgress, notificationId, statusText)
                }

                val compressedPath = compressedFile.absolutePath
                dao.updateUploadState(
                    messageId = messageId,
                    isPending = true,
                    isUploading = true,
                    isFailed = false,
                    uploadProgress = 30,
                    mediaUrl = compressedPath
                )

                val remotePath = "chats/media/${System.currentTimeMillis()}_vid.mp4"
                finalRemoteUrl = uploader.uploadMediaFile(compressedFile, remotePath) { progress, statusText ->
                    val overallProgress = (30 + ((progress / 100.0) * 70)).toInt().coerceIn(30, 100)
                    updateProgressState(dao, messageId, overallProgress, notificationId, statusText)
                }
            } else if (messageType == MessageType.IMAGE) {
                val compressedFile = uploader.compressImage(uri) { progress, statusText ->
                    val overallProgress = ((progress / 100.0) * 30).toInt().coerceIn(0, 30)
                    updateProgressState(dao, messageId, overallProgress, notificationId, statusText)
                }

                val compressedPath = compressedFile.absolutePath
                dao.updateUploadState(
                    messageId = messageId,
                    isPending = true,
                    isUploading = true,
                    isFailed = false,
                    uploadProgress = 30,
                    mediaUrl = compressedPath
                )

                val remotePath = "chats/media/${System.currentTimeMillis()}_img.jpg"
                finalRemoteUrl = try {
                    uploader.uploadMediaFile(compressedFile, remotePath) { progress, statusText ->
                        val overallProgress = (30 + ((progress / 100.0) * 70)).toInt().coerceIn(30, 100)
                        updateProgressState(dao, messageId, overallProgress, notificationId, statusText)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cloudinary upload failed for image, using fallback: ${e.localizedMessage}")
                    if (compressedFile.exists() && compressedFile.length() > 0) uploader.encodeFileToBase64(compressedFile) else localMediaUrl
                }
            } else if (messageType == MessageType.VOICE_NOTE) {
                val filePath = if (localMediaUrl.startsWith("file://")) Uri.parse(localMediaUrl).path ?: "" else localMediaUrl
                val file = File(filePath)
                val remotePath = "family_chats/${recipientId}/voice_notes/vn_${System.currentTimeMillis()}.m4a"
                finalRemoteUrl = try {
                    uploader.uploadMediaFile(file, remotePath) { progress, statusText ->
                        updateProgressState(dao, messageId, progress, notificationId, statusText)
                    }
                } catch (e: Exception) {
                    if (file.exists() && file.length() > 0) uploader.encodeFileToBase64(file) else localMediaUrl
                }
            }

            // 3. Complete process & sync message to Firebase Repository
            updateProgressState(dao, messageId, 100, notificationId, "Upload complete!")

            val repository = FirebaseChatRepository(appContext)
            repository.sendMessage(
                memberId = recipientId.ifBlank { chatKey },
                textContent = textContent,
                type = messageType,
                mediaUrl = finalRemoteUrl,
                replyToMessageId = replyToId,
                replyToSenderName = replyToName,
                replyToText = replyToText,
                forcedTimestamp = System.currentTimeMillis(),
                explicitSenderUid = senderUid.ifBlank { null }
            )

            val updatedEntity = dao.getMessageById(messageId)?.copy(
                mediaUrl = finalRemoteUrl,
                isPending = false,
                isUploading = false,
                isFailed = false,
                uploadProgress = 100
            )
            if (updatedEntity != null) {
                dao.insertMessage(updatedEntity)
            } else {
                dao.updateUploadState(
                    messageId = messageId,
                    isPending = false,
                    isUploading = false,
                    isFailed = false,
                    uploadProgress = 100,
                    mediaUrl = finalRemoteUrl
                )
            }

            cancelNotification(notificationId)
            Log.i(TAG, "Video/Media upload worker successfully completed for $messageId")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "MediaUploadWorker error for $messageId: ${e.localizedMessage}", e)
            val currentAttempt = runAttemptCount
            if (currentAttempt < 3) {
                Log.w(TAG, "Retrying MediaUploadWorker for $messageId (attempt ${currentAttempt + 1}/3)...")
                Result.retry()
            } else {
                dao.updateUploadState(
                    messageId = messageId,
                    isPending = false,
                    isUploading = false,
                    isFailed = true,
                    uploadProgress = 0,
                    mediaUrl = localMediaUrl
                )
                showErrorNotification(notificationId, "Video upload failed. Tap to retry in chat.")
                Result.failure()
            }
        }
    }

    private fun updateProgressState(
        dao: com.family.talkly.data.local.dao.ChatMessageDao,
        messageId: String,
        progress: Int,
        notificationId: Int,
        statusText: String
    ) {
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            dao.updateUploadState(
                messageId = messageId,
                isPending = true,
                isUploading = true,
                isFailed = false,
                uploadProgress = progress
            )
            try {
                setForeground(createForegroundInfo(notificationId, statusText, progress))
            } catch (e: Exception) {
                // Ignore foreground exception
            }
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        statusText: String,
        progress: Int
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Talkly Video Upload")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun showErrorNotification(notificationId: Int, text: String) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Talkly Video Upload Failed")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }

    private fun cancelNotification(notificationId: Int) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Upload Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for video and media background uploads"
            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
