package com.family.talkly.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.family.talkly.data.local.TalklyDatabase
import com.family.talkly.data.local.entity.ChatMessageEntity
import com.family.talkly.data.models.MessageType
import com.family.talkly.worker.MediaUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object MediaUploadManager {

    fun enqueueMediaUpload(
        context: Context,
        messageId: String,
        chatKey: String,
        recipientId: String,
        messageType: MessageType,
        localMediaUrl: String,
        textContent: String = "",
        senderUid: String? = null,
        senderName: String? = null,
        replyToId: String? = null,
        replyToName: String? = null,
        replyToText: String? = null
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            val canonicalMessageId = try {
                java.util.UUID.fromString(messageId)
                messageId
            } catch (e: Exception) {
                java.util.UUID.nameUUIDFromBytes(messageId.toByteArray()).toString()
            }

            val sessionPrefs = appContext.getSharedPreferences("talkly_auth_session", Context.MODE_PRIVATE)
            val fallbackPrefs = appContext.getSharedPreferences("talkly_user_session", Context.MODE_PRIVATE)
            val effectiveSenderUid = senderUid
                ?: sessionPrefs.getString("user_uid", null)
                ?: fallbackPrefs.getString("user_uid", null)
                ?: "self"
            val effectiveSenderName = senderName
                ?: sessionPrefs.getString("user_name", null)
                ?: fallbackPrefs.getString("user_name", null)
                ?: "You"

            val db = TalklyDatabase.getInstance(appContext)
            val dao = db.chatMessageDao()

            // If localMediaUrl is a content:// URI, stage it immediately to app cache
            // so background WorkManager never suffers from SecurityException / URI expiration
            var stagingPath = localMediaUrl
            if (localMediaUrl.startsWith("content://")) {
                try {
                    val uri = Uri.parse(localMediaUrl)
                    val ext = if (messageType == MessageType.VIDEO) "mp4" else "jpg"
                    val stagedFile = File(appContext.cacheDir, "staged_${canonicalMessageId}.${ext}")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(stagedFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (stagedFile.exists() && stagedFile.length() > 0) {
                        stagingPath = stagedFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.w("MediaUploadManager", "Could not stage content URI, passing original: ${e.localizedMessage}")
                }
            }

            val existing = dao.getMessageById(canonicalMessageId)
            if (existing == null) {
                val entity = ChatMessageEntity(
                    id = canonicalMessageId,
                    chatKey = chatKey,
                    senderId = effectiveSenderUid,
                    senderName = effectiveSenderName,
                    receiverId = recipientId,
                    messageType = messageType.name,
                    textContent = textContent,
                    mediaUrl = stagingPath,
                    timestamp = System.currentTimeMillis(),
                    isPending = true,
                    isUploading = true,
                    isFailed = false,
                    uploadProgress = 0,
                    replyToMessageId = replyToId,
                    replyToSenderName = replyToName,
                    replyToText = replyToText
                )
                dao.insertMessage(entity)
            } else {
                dao.updateUploadState(
                    messageId = canonicalMessageId,
                    isPending = true,
                    isUploading = true,
                    isFailed = false,
                    uploadProgress = 0,
                    mediaUrl = stagingPath
                )
            }

            val inputData = Data.Builder()
                .putString("message_id", canonicalMessageId)
                .putString("chat_key", chatKey)
                .putString("recipient_id", recipientId)
                .putString("sender_uid", effectiveSenderUid)
                .putString("sender_name", effectiveSenderName)
                .putString("message_type", messageType.name)
                .putString("local_media_url", stagingPath)
                .putString("text_content", textContent)
                .putString("reply_to_id", replyToId ?: "")
                .putString("reply_to_name", replyToName ?: "")
                .putString("reply_to_text", replyToText ?: "")
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MediaUploadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "work_upload_${messageId}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
