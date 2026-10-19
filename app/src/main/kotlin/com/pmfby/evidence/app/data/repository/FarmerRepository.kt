package com.pmfby.evidence.app.data.repository

import com.pmfby.evidence.app.data.local.FarmerDao
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.local.SurveyCaseDao
import com.pmfby.evidence.app.data.local.SurveyCaseEntity
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.session.SessionManager
import com.pmfby.evidence.core.dto.ApiDtoMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Farmer/case list and detail — refreshes the Room cache from the server (RBAC-scoped
 * server-side already; see backend/src/routes/farmers.js) and exposes it as a Flow so
 * the UI keeps working offline from the last successful sync.
 */
class FarmerRepository(
    private val api: ApiService,
    private val session: SessionManager,
    private val farmerDao: FarmerDao,
    private val caseDao: SurveyCaseDao,
) {
    fun observeCachedFarmers(): Flow<List<FarmerEntity>> = farmerDao.observeAll()

    fun observeCasesForFarmer(farmerId: String): Flow<List<SurveyCaseEntity>> = caseDao.observeForFarmer(farmerId)

    fun observeAllCases(): Flow<List<SurveyCaseEntity>> = caseDao.observeAll()

    fun observeCase(caseId: String): Flow<SurveyCaseEntity?> = caseDao.observeById(caseId)

    suspend fun getCachedFarmer(farmerId: String): FarmerEntity? = farmerDao.getById(farmerId)

    suspend fun getCachedCase(caseId: String): SurveyCaseEntity? = caseDao.getById(caseId)

    suspend fun refreshFarmers(search: String? = null): Result<Unit> {
        val auth = session.authHeader() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = api.listFarmers(auth, search)
            @Suppress("UNCHECKED_CAST")
            val rawFarmers = response["farmers"] as? List<Map<String, Any?>> ?: emptyList()
            val dtos = ApiDtoMapper.farmersFromJsonList(rawFarmers)
            val now = System.currentTimeMillis()
            farmerDao.upsertAll(
                dtos.map {
                    FarmerEntity(
                        id = it.id,
                        farmerName = it.farmerName,
                        farmerMobile = it.farmerMobile,
                        villageName = it.villageName,
                        stateId = it.stateId,
                        districtId = it.districtId,
                        assignedUserId = it.assignedUserId,
                        lastSyncedAtMillis = now,
                    )
                },
            )
            // Reconcile deletes — ONLY for a full, unfiltered refresh. upsertAll alone
            // never removes a farmer whose assignment was revoked/reassigned server-side
            // (GET /farmers simply stops returning them), which left stale rows in "My
            // Assigned Farmers" forever. A filtered search result must never trigger this
            // (it is not the full assigned set) — see FarmerDao.deleteNotIn doc.
            if (search.isNullOrBlank()) {
                val keepIds = dtos.map { it.id }
                if (keepIds.isNotEmpty()) {
                    farmerDao.deleteNotIn(keepIds)
                    caseDao.deleteForFarmersNotIn(keepIds)
                } else {
                    farmerDao.clear()
                    caseDao.deleteForFarmersNotIn(emptyList())
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GET /farmers returns farmer identity rows only — case data (contact_status etc,
     * which the farmer-list status tabs are derived from, see core.status.CaseStatusMapper)
     * only comes back from GET /farmers/:id (see backend/src/routes/farmers.js). A
     * surveyor's own assigned list is small (tens, not thousands of rows) so backfilling
     * it with one detail call per farmer is an acceptable, honest tradeoff — done
     * sequentially (not in parallel) to stay gentle on a flaky field 2G/3G connection,
     * and best-effort: one farmer's failed detail fetch never blocks the others.
     */
    suspend fun refreshAllCachedFarmerDetails() {
        val farmerIds = farmerDao.observeAll().first().map { it.id }
        // Sequential on purpose — see this function's class doc above.
        for (id in farmerIds) {
            refreshFarmerDetail(id)
        }
    }

    suspend fun refreshFarmerDetail(farmerId: String): Result<Unit> {
        val auth = session.authHeader() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = api.getFarmer(auth, farmerId)
            @Suppress("UNCHECKED_CAST")
            val rawCases = response["cases"] as? List<Map<String, Any?>> ?: emptyList()
            val now = System.currentTimeMillis()
            val caseDtos = rawCases.map { ApiDtoMapper.surveyCaseFromJson(it) }
            // Preserve this device's own lastCallOutcome/lastMessageStatus (local-only
            // signals the server response doesn't carry — see Entities.kt doc) across a
            // refresh instead of wiping them back to null. Plain for-loop (reads happen
            // one at a time, deliberately — this runs once per farmer already).
            val entities = mutableListOf<SurveyCaseEntity>()
            for (dto in caseDtos) {
                val existing = caseDao.getById(dto.id)
                entities.add(
                    SurveyCaseEntity(
                        id = dto.id,
                        farmerId = dto.farmerId,
                        contactStatus = dto.contactStatus,
                        attemptCount = dto.attemptCount,
                        postponedUntil = dto.postponedUntil,
                        closedAt = dto.closedAt,
                        forcefulSurveyFlag = dto.forcefulSurveyFlag,
                        lastSyncedAtMillis = now,
                        lastCallOutcome = existing?.lastCallOutcome,
                        lastMessageStatus = existing?.lastMessageStatus,
                    ),
                )
            }
            caseDao.upsertAll(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
