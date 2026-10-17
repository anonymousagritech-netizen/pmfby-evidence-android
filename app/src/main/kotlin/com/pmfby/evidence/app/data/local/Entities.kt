package com.pmfby.evidence.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local offline cache of ONLY the signed-in surveyor's own assigned farmers/cases (per
 * task requirement — this is not a general-purpose local mirror of the whole dataset,
 * and RbacFilter in :core is what decides which rows are even worth caching/showing).
 */
@Entity(tableName = "cached_farmers")
data class FarmerEntity(
    @PrimaryKey val id: String,
    val farmerName: String,
    val farmerMobile: String?,
    val villageName: String?,
    val stateId: String?,
    val districtId: String?,
    val assignedUserId: String?,
    val lastSyncedAtMillis: Long,
)

@Entity(tableName = "cached_cases")
data class SurveyCaseEntity(
    @PrimaryKey val id: String,
    val farmerId: String,
    val contactStatus: String?,
    val attemptCount: Int,
    val postponedUntil: String?,
    val closedAt: String?,
    val forcefulSurveyFlag: Boolean,
    val lastSyncedAtMillis: Long,
    /**
     * This device's own most recent call-outcome / SMS-composer status for this case —
     * written locally right after a submission succeeds (see CaseActionsRepository and
     * core.status.CaseStatusMapper's class doc for why these two fields exist only here
     * and are never populated from the farmer-detail GET response).
     */
    val lastCallOutcome: String? = null,
    val lastMessageStatus: String? = null,
)

/**
 * One row per offline-queued side-effecting action (call event, message event, evidence
 * upload, emergency incident) awaiting an upload attempt. [idempotencyKey] is sent with
 * every request so a retried upload after a flaky connection can never double-create a
 * server-side row (see backend-and-web/backend/src/routes, every POST route's
 * idempotencyKey handling, and core.idempotency.IdempotencyKey which generates these).
 */
@Entity(tableName = "offline_queue")
data class OfflineEventEntity(
    @PrimaryKey val idempotencyKey: String,
    val kind: String, // "call_event" | "message_event" | "evidence" | "emergency_incident"
    val payloadJson: String,
    val filePath: String?,
    val status: String, // "pending" | "uploading" | "uploaded" | "failed"
    val attempts: Int,
    val lastError: String?,
    val createdAtMillis: Long,
)
