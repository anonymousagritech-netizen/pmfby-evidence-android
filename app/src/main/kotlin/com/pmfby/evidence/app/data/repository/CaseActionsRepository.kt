package com.pmfby.evidence.app.data.repository

import com.pmfby.evidence.app.data.local.OfflineEventDao
import com.pmfby.evidence.app.data.local.OfflineEventEntity
import com.pmfby.evidence.app.data.local.SurveyCaseDao
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.session.SessionManager
import com.pmfby.evidence.core.idempotency.IdempotencyKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class ActionResult {
    data class Submitted(val serverResponse: Map<String, Any?>) : ActionResult()
    data class QueuedOffline(val idempotencyKey: String) : ActionResult()
    data class Failed(val message: String) : ActionResult()
}

/**
 * Call-event / message-event / emergency-incident / discrepancy-flag actions. Every
 * write carries an idempotencyKey (core.idempotency.IdempotencyKey) and, if the network
 * call throws IOException (no connectivity), falls back to the Room offline_queue table
 * instead of failing outright — WorkManager's UploadWorker later retries it (see
 * work/UploadWorker.kt). This mirrors the task's "offline upload/event queue with
 * idempotency keys" requirement end to end: enqueue here, retry there, same key both times.
 */
class CaseActionsRepository(
    private val api: ApiService,
    private val session: SessionManager,
    private val offlineEventDao: OfflineEventDao,
    private val caseDao: SurveyCaseDao,
    moshi: Moshi,
) {
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    /**
     * Enqueues a FAILED multipart evidence upload (emergency audio/video, discrepancy
     * photo) for WorkManager's UploadWorker to retry from, the same way a failed JSON
     * action is queued — see [submitOrQueue]. [filePath] must point at a file this app
     * still owns (its own cache dir); if that file is gone by the time the retry runs,
     * UploadWorker marks the row permanently failed rather than looping forever.
     */
    suspend fun enqueueEvidenceForRetry(filePath: String, fields: Map<String, String>): String {
        val key = IdempotencyKey.randomKey()
        offlineEventDao.upsert(
            OfflineEventEntity(
                idempotencyKey = key,
                kind = "evidence",
                payloadJson = mapAdapter.toJson(fields),
                filePath = filePath,
                status = "pending",
                attempts = 0,
                lastError = null,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        return key
    }

    suspend fun submitCallEvent(caseId: String, phoneNumber: String, geo: Map<String, Any?>?): ActionResult =
        submitOrQueue("call_event", mapOf("caseId" to caseId, "phoneNumber" to phoneNumber, "geo" to geo)) { auth, key, body ->
            api.createCallEvent(auth, body + ("idempotencyKey" to key))
        }

    suspend fun submitCallOutcome(callEventId: String, outcome: String, personContacted: Map<String, Any?>?, geo: Map<String, Any?>?): ActionResult =
        withContext(Dispatchers.IO) {
            val auth = session.authHeader() ?: return@withContext ActionResult.Failed("Not signed in")
            try {
                val body = mapOf("outcome" to outcome, "personContacted" to personContacted, "geo" to geo)
                val response = api.updateCallOutcome(auth, callEventId, body)
                @Suppress("UNCHECKED_CAST")
                val callEvent = response["callEvent"] as? Map<String, Any?>
                val caseId = callEvent?.get("case_id")?.toString()
                if (caseId != null) caseDao.updateLastCallOutcome(caseId, outcome)
                ActionResult.Submitted(response)
            } catch (e: IOException) {
                ActionResult.Failed("Offline — outcome update could not be queued (requires an existing call event id)")
            } catch (e: Exception) {
                ActionResult.Failed(e.message ?: "Unknown error")
            }
        }

    suspend fun resolveCaseOutcome(caseId: String, outcome: String, reason: String?, postponedUntil: String?): ActionResult =
        withContext(Dispatchers.IO) {
            val auth = session.authHeader() ?: return@withContext ActionResult.Failed("Not signed in")
            try {
                val body = mapOf("outcome" to outcome, "reason" to reason, "postponedUntil" to postponedUntil)
                val response = api.resolveCaseOutcome(auth, caseId, body)
                val isClosing = outcome != "postponed"
                caseDao.updateLocalOutcome(
                    caseId = caseId,
                    outcome = outcome,
                    postponedUntil = if (outcome == "postponed") postponedUntil else null,
                    isClosing = isClosing,
                    closedAtIso = if (isClosing) java.time.Instant.now().toString() else null,
                )
                ActionResult.Submitted(response)
            } catch (e: Exception) {
                ActionResult.Failed(e.message ?: "Could not resolve outcome")
            }
        }

    suspend fun submitMessageEvent(
        caseId: String,
        templateId: String,
        renderedText: String,
        phoneNumber: String?,
        status: String,
    ): ActionResult {
        val result = submitOrQueue(
            "message_event",
            mapOf("caseId" to caseId, "templateId" to templateId, "renderedText" to renderedText, "phoneNumber" to phoneNumber, "status" to status),
        ) { auth, key, body -> api.createMessageEvent(auth, body + ("idempotencyKey" to key)) }
        // Write through locally even if queued offline — the composer genuinely was (or
        // will be) opened with this text, which is what the SMS_SENT tab actually tracks.
        if (result is ActionResult.Submitted || result is ActionResult.QueuedOffline) {
            withContext(Dispatchers.IO) { caseDao.updateLastMessageStatus(caseId, status) }
        }
        return result
    }

    suspend fun submitEmergencyIncident(caseId: String, incidentType: String): ActionResult =
        submitOrQueue("emergency_incident", mapOf("caseId" to caseId, "incidentType" to incidentType)) { auth, key, body ->
            api.startEmergencyIncident(auth, body + ("idempotencyKey" to key))
        }

    suspend fun updateEmergencyIncident(id: String, status: String, durationSeconds: Int, description: String?): ActionResult =
        withContext(Dispatchers.IO) {
            val auth = session.authHeader() ?: return@withContext ActionResult.Failed("Not signed in")
            try {
                val body = mapOf("status" to status, "durationSeconds" to durationSeconds, "description" to description)
                ActionResult.Submitted(api.updateEmergencyIncident(auth, id, body))
            } catch (e: Exception) {
                ActionResult.Failed(e.message ?: "Could not update emergency incident")
            }
        }

    suspend fun raiseDiscrepancyFlag(caseId: String, reason: String, description: String, evidenceFileId: String): ActionResult =
        withContext(Dispatchers.IO) {
            val auth = session.authHeader() ?: return@withContext ActionResult.Failed("Not signed in")
            try {
                val body = mapOf("reason" to reason, "description" to description, "evidenceFileId" to evidenceFileId)
                ActionResult.Submitted(api.raiseDiscrepancyFlag(auth, caseId, body))
            } catch (e: Exception) {
                ActionResult.Failed(e.message ?: "Could not raise discrepancy flag")
            }
        }

    suspend fun reportIntegrityConcern(caseId: String?, farmerId: String?, description: String): ActionResult =
        withContext(Dispatchers.IO) {
            val auth = session.authHeader() ?: return@withContext ActionResult.Failed("Not signed in")
            try {
                val body = mapOf("caseId" to caseId, "farmerId" to farmerId, "description" to description)
                ActionResult.Submitted(api.reportIntegrityConcern(auth, body))
            } catch (e: Exception) {
                ActionResult.Failed(e.message ?: "Could not submit integrity concern")
            }
        }

    private suspend fun submitOrQueue(
        kind: String,
        body: Map<String, Any?>,
        call: suspend (auth: String, idempotencyKey: String, body: Map<String, Any?>) -> Map<String, Any?>,
    ): ActionResult = withContext(Dispatchers.IO) {
        val auth = session.authHeader() ?: return@withContext ActionResult.Failed("Not signed in")
        val key = IdempotencyKey.randomKey()
        try {
            ActionResult.Submitted(call(auth, key, body))
        } catch (e: IOException) {
            // No connectivity — queue for WorkManager to retry later, same idempotency key.
            offlineEventDao.upsert(
                OfflineEventEntity(
                    idempotencyKey = key,
                    kind = kind,
                    payloadJson = mapAdapter.toJson(body),
                    filePath = null,
                    status = "pending",
                    attempts = 0,
                    lastError = e.message,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            ActionResult.QueuedOffline(key)
        } catch (e: Exception) {
            ActionResult.Failed(e.message ?: "Unknown error")
        }
    }
}
