package com.pmfby.evidence.app.data.repository

import com.pmfby.evidence.app.data.local.FarmerDao
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.local.SurveyCaseDao
import com.pmfby.evidence.app.data.local.SurveyCaseEntity
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.session.SessionManager
import com.pmfby.evidence.core.dto.ApiDtoMapper
import kotlinx.coroutines.flow.Flow

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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
            caseDao.upsertAll(
                caseDtos.map {
                    SurveyCaseEntity(
                        id = it.id,
                        farmerId = it.farmerId,
                        contactStatus = it.contactStatus,
                        attemptCount = it.attemptCount,
                        postponedUntil = it.postponedUntil,
                        closedAt = it.closedAt,
                        forcefulSurveyFlag = it.forcefulSurveyFlag,
                        lastSyncedAtMillis = now,
                    )
                },
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
