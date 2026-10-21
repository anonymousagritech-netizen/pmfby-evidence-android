package com.pmfby.evidence.app.work

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pmfby.evidence.app.PmfbyEvidenceApp
import com.pmfby.evidence.app.telephony.SystemCallRecordingFinder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * After a call: find the phone's own call recording, copy it to app storage and upload it to
 * the backend linked to the case + call event. Runs in WorkManager so it survives the call
 * screen closing, the app being killed, and a missing network (retries with backoff).
 */
class CallRecordingPickupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PmfbyEvidenceApp
        val caseId = inputData.getString(KEY_CASE_ID)
        val sessionId = inputData.getString(KEY_SESSION_ID)
        if (caseId == null && sessionId == null) return done("failed", "missing case/session")
        val phone = inputData.getString(KEY_PHONE) ?: return done("failed", "missing phone")
        val start = inputData.getLong(KEY_START, 0L)
        val connected = inputData.getBoolean(KEY_CONNECTED, true)
        val callEventId = inputData.getString(KEY_CALL_EVENT_ID)

        val pendingDir = File(applicationContext.filesDir, "pending_uploads").apply { mkdirs() }
        val copy = pendingDir.listFiles()?.firstOrNull { it.name.startsWith("rec_$start.") }
            ?: run {
                if (!SystemCallRecordingFinder.hasAccess()) return done("no_access", "All files access not granted")
                val found = SystemCallRecordingFinder.waitForFinishedRecording(phone, start, if (connected) 40_000 else 8_000)
                    ?: return done("not_found", "No call recording found")
                File(pendingDir, "rec_$start.${found.extension}").also { found.copyTo(it, overwrite = true) }
            }

        val auth = app.sessionManager.authHeader() ?: return Result.retry()
        val fields = buildMap {
            caseId?.let { put("caseId", it) }
            sessionId?.let { put("sessionId", it) }
            put("kind", "phone_call_recording")
            put("consentAcknowledged", "true")
            callEventId?.let { put("callEventId", it) }
            durationSeconds(copy)?.let { put("durationSeconds", it.toString()) }
        }
        return try {
            app.fileUploadApi.uploadCallRecording(auth, copy, SystemCallRecordingFinder.mimeFor(copy), fields)
            copy.delete()
            Log.i(TAG, "Uploaded call recording for ${caseId ?: sessionId}")
            done("uploaded", null)
        } catch (e: Exception) {
            Log.w(TAG, "Upload failed: ${e.message}")
            val terminal = e.message?.contains("HTTP 4") == true
            if (terminal || runAttemptCount >= MAX_ATTEMPTS) done("rejected", e.message) else Result.retry()
        }
    }

    private fun durationSeconds(file: File): Int? = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        r.release()
        ms?.let { (it / 1000).toInt() }
    } catch (_: Exception) {
        null
    }

    private fun done(result: String, detail: String?): Result =
        Result.success(workDataOf(OUT_RESULT to result, OUT_DETAIL to (detail ?: "")))

    companion object {
        private const val TAG = "CallRecPickup"
        private const val MAX_ATTEMPTS = 8
        const val KEY_CASE_ID = "caseId"
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_PHONE = "phone"
        const val KEY_START = "start"
        const val KEY_CONNECTED = "connected"
        const val KEY_CALL_EVENT_ID = "callEventId"
        const val OUT_RESULT = "result"
        const val OUT_DETAIL = "detail"

        fun workName(startMillis: Long) = "call_rec_$startMillis"

        private fun request(caseId: String?, sessionId: String?, phone: String, start: Long, connected: Boolean, callEventId: String?, delayMinutes: Long) =
            OneTimeWorkRequestBuilder<CallRecordingPickupWorker>()
                .setInputData(
                    Data.Builder()
                        .also { b -> caseId?.let { b.putString(KEY_CASE_ID, it) }; sessionId?.let { b.putString(KEY_SESSION_ID, it) } }
                        .putString(KEY_PHONE, phone)
                        .putLong(KEY_START, start)
                        .putBoolean(KEY_CONNECTED, connected)
                        .also { b -> callEventId?.let { b.putString(KEY_CALL_EVENT_ID, it) } }
                        .build(),
                )
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        /** Safety net at dial time: if the call-end signal never arrives, still pick the file up later. */
        fun scheduleFallback(context: Context, caseId: String?, sessionId: String?, phone: String, start: Long, callEventId: String?) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(start), ExistingWorkPolicy.REPLACE, request(caseId, sessionId, phone, start, true, callEventId, 20),
            )
        }

        /** Immediate pickup once the call has ended. */
        fun runNow(context: Context, caseId: String?, sessionId: String?, phone: String, start: Long, connected: Boolean, callEventId: String?) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(start), ExistingWorkPolicy.REPLACE, request(caseId, sessionId, phone, start, connected, callEventId, 0),
            )
        }
    }
}
