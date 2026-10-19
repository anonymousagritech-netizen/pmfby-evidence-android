package com.pmfby.evidence.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmerDao {
    @Query("SELECT * FROM cached_farmers ORDER BY farmerName")
    fun observeAll(): Flow<List<FarmerEntity>>

    @Query("SELECT * FROM cached_farmers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FarmerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(farmers: List<FarmerEntity>)

    @Query("DELETE FROM cached_farmers")
    suspend fun clear()
}

@Dao
interface SurveyCaseDao {
    @Query("SELECT * FROM cached_cases WHERE farmerId = :farmerId ORDER BY lastSyncedAtMillis DESC")
    fun observeForFarmer(farmerId: String): Flow<List<SurveyCaseEntity>>

    @Query("SELECT * FROM cached_cases ORDER BY lastSyncedAtMillis DESC")
    fun observeAll(): Flow<List<SurveyCaseEntity>>

    @Query("SELECT * FROM cached_cases WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SurveyCaseEntity?

    @Query("SELECT * FROM cached_cases WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SurveyCaseEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cases: List<SurveyCaseEntity>)

    /** Write-through update after a call outcome is actually submitted — see Entities.kt doc. */
    @Query("UPDATE cached_cases SET lastCallOutcome = :outcome WHERE id = :caseId")
    suspend fun updateLastCallOutcome(caseId: String, outcome: String)

    /**
     * Write-through update immediately after a call_event is created and the server returns
     * the new attempt_no (attempt_count is incremented server-side, atomically, in the same
     * transaction as the call_events insert — see backend callEvents.js). Without this, the
     * cached case list keeps showing the stale attempt number until the next full sync, which
     * is why "Attempt #0" appeared to never change after real calls were made.
     */
    @Query("UPDATE cached_cases SET attemptCount = :attemptCount WHERE id = :caseId")
    suspend fun updateAttemptCount(caseId: String, attemptCount: Int)

    /** Write-through update after an SMS composer hand-off is logged — see Entities.kt doc. */
    @Query("UPDATE cached_cases SET lastMessageStatus = :status WHERE id = :caseId")
    suspend fun updateLastMessageStatus(caseId: String, status: String)

    /** Local-only update immediately after resolve-outcome succeeds, ahead of the next full refresh. */
    @Query(
        "UPDATE cached_cases SET contactStatus = :outcome, postponedUntil = :postponedUntil, " +
            "closedAt = CASE WHEN :isClosing THEN :closedAtIso ELSE NULL END WHERE id = :caseId",
    )
    suspend fun updateLocalOutcome(caseId: String, outcome: String, postponedUntil: String?, isClosing: Boolean, closedAtIso: String?)
}

@Dao
interface OfflineEventDao {
    @Query("SELECT * FROM offline_queue ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<OfflineEventEntity>>

    /**
     * 'failed_permanent' (a 401/403/404/409 — see core.http.UploadOutcomeClassifier) is
     * deliberately excluded: it is never retried automatically again.
     */
    @Query("SELECT * FROM offline_queue WHERE status = 'pending' OR status = 'failed' ORDER BY createdAtMillis ASC")
    suspend fun getPending(): List<OfflineEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: OfflineEventEntity)

    @Update
    suspend fun update(event: OfflineEventEntity)

    @Query("DELETE FROM offline_queue WHERE idempotencyKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM offline_queue WHERE status = 'uploaded'")
    suspend fun clearUploaded()
}
