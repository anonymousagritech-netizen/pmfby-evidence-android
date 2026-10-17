package com.pmfby.evidence.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pmfby.evidence.app.PmfbyEvidenceApp
import com.pmfby.evidence.app.data.local.OfflineEventEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.concurrent.TimeUnit

/**
 * Retries every queued offline_queue row whenever connectivity is available (WorkManager
 * network constraint), each attempt reusing the SAME idempotencyKey stored with the row
 * — so a row that actually succeeded on a previous attempt, but the app never heard the
 * response (e.g. crashed mid-request), cannot be double-submitted: the backend's
 * ON CONFLICT (idempotency_key) handling (see backend-and-web/backend/src/routes) returns
 * the original row instead of creating a new one.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PmfbyEvidenceApp
        val dao = app.database.offlineEventDao()
        val api = app.apiService
        val session = app.sessionManager
        val moshi: Moshi = app.moshi
        val mapAdapter = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
        )

        val auth = session.authHeader() ?: return Result.retry()
        val pending = dao.getPending()
        var anyFailure = false

        for (event in pending) {
            try {
                val body = mapAdapter.fromJson(event.payloadJson) ?: emptyMap()
                val bodyWithKey = body + ("idempotencyKey" to event.idempotencyKey)
                when (event.kind) {
                    "call_event" -> api.createCallEvent(auth, bodyWithKey)
                    "message_event" -> api.createMessageEvent(auth, bodyWithKey)
                    "emergency_incident" -> api.startEmergencyIncident(auth, bodyWithKey)
                    else -> {
                        // Unknown kind — don't loop forever retrying something we can't send.
                        dao.update(event.copy(status = "failed", lastError = "Unknown queued event kind: ${event.kind}"))
                        continue
                    }
                }
                dao.delete(event.idempotencyKey)
            } catch (e: Exception) {
                anyFailure = true
                dao.update(
                    event.copy(
                        status = "failed",
                        attempts = event.attempts + 1,
                        lastError = e.message ?: "Unknown error",
                    ),
                )
            }
        }

        return if (anyFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pmfby_offline_upload_retry"

        /** Call once from Application.onCreate — schedules a periodic retry, network-gated. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Call right after regaining connectivity / on pull-to-refresh for an immediate attempt. */
        fun runOnce(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(workDataOf("trigger" to "manual"))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
