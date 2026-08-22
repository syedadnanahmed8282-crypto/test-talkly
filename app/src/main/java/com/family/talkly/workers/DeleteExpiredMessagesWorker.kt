package com.family.talkly.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.family.talkly.data.models.ChatMessage
import com.family.talkly.data.supabase.SupabaseClientProvider
import com.family.talkly.data.supabase.SupabaseMessage
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DeleteExpiredMessagesWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "DeleteExpiredWorker"
        const val WORK_NAME = "periodic_delete_expired_messages_work"

        /**
         * Schedules periodic WorkManager job to delete messages older than 48 hours
         * every hour, and runs an immediate one-time cleanup job.
         */
        fun schedulePeriodicCleanup(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicWork = PeriodicWorkRequestBuilder<DeleteExpiredMessagesWorker>(
                    1, TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWork
                )

                // Also trigger immediate one-time cleanup on launch
                val immediateWork = OneTimeWorkRequestBuilder<DeleteExpiredMessagesWorker>().build()
                WorkManager.getInstance(context).enqueue(immediateWork)

                Log.i(TAG, "Successfully scheduled WorkManager job for expiring messages (>48h)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule WorkManager job: ${e.localizedMessage}")
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting background scan to clean expired messages older than 48 hours...")

        val cutoffTimestamp = System.currentTimeMillis() - ChatMessage.EXPIRATION_48_HOURS_MS
        val cutoffIso = SupabaseMessage.millisToIsoTimestamp(cutoffTimestamp)

        try {
            // Clean up old messages from Supabase messages table if older than 48 hours
            SupabaseClientProvider.client.postgrest["messages"]
                .delete {
                    filter {
                        lt("created_at", cutoffIso)
                    }
                }

            Log.i(TAG, "WorkManager Cleanup Completed: Successfully purged expired messages (>48h)")
            Result.success()
        } catch (e: Exception) {
            Log.d(TAG, "Note during expired messages cleanup: ${e.localizedMessage}")
            Result.success()
        }
    }
}
