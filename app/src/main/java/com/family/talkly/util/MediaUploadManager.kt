package com.family.talkly.util

import android.content.Context
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

            val existing = dao.getMessageById(messageId)
            if (existing == null) {
                val entity = ChatMessageEntity(
                    id = messageId,
                    chatKey = chatKey,
                    senderId = effectiveSenderUid,
                    senderName = effectiveSenderName,
                    receiverId = recipientId,
                    messageType = messageType.name,
                    textContent = textContent,
                    mediaUrl = localMediaUrl,
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
                    messageId = messageId,
                    isPending = true,
                    isUploading = true,
                    isFailed = false,
                    uploadProgress = 0,
                    mediaUrl = localMediaUrl
                )
            }

            val inputData = Data.Builder()
                .putString("message_id", messageId)
                .putString("chat_key", chatKey)
                .putString("recipient_id", recipientId)
                .putString("sender_uid", effectiveSenderUid)
                .putString("sender_name", effectiveSenderName)
                .putString("message_type", messageType.name)
                .putString("local_media_url", localMediaUrl)
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
