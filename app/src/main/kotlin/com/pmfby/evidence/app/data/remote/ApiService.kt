package com.pmfby.evidence.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface matching the backend's actual route contract (read from
 * backend-and-web/backend/src/routes as of the repo split — see each route file for the
 * authoritative shape). Request/response bodies are plain `Map<String, Any?>`: Moshi can
 * serialize/deserialize an arbitrary JSON object into/out of a Map without needing a
 * matching data class per endpoint, and the response maps are then turned into typed
 * domain objects by :core's ApiDtoMapper (see data.repository.*Repository) — the same
 * mapping code that is unit tested in isolation in :core.
 *
 * Every endpoint except login requires the `Authorization: Bearer <jwt>` header, matching
 * backend/src/middleware/auth.js's `authenticate` middleware exactly.
 */
interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: Map<String, String>): Map<String, Any?>

    @POST("auth/change-password")
    suspend fun changePassword(@Header("Authorization") auth: String, @Body body: Map<String, String>): Map<String, Any?>

    @GET("farmers")
    suspend fun listFarmers(
        @Header("Authorization") auth: String,
        @Query("search") search: String?,
    ): Map<String, Any?>

    @GET("farmers/{id}")
    suspend fun getFarmer(@Header("Authorization") auth: String, @Path("id") id: String): Map<String, Any?>

    @POST("call-events")
    suspend fun createCallEvent(@Header("Authorization") auth: String, @Body body: Map<String, Any?>): Map<String, Any?>

    @PATCH("call-events/{id}/outcome")
    suspend fun updateCallOutcome(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: Map<String, Any?>,
    ): Map<String, Any?>

    @POST("cases/{id}/resolve-outcome")
    suspend fun resolveCaseOutcome(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: Map<String, Any?>,
    ): Map<String, Any?>

    @POST("cases/{id}/discrepancy-flags")
    suspend fun raiseDiscrepancyFlag(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: Map<String, Any?>,
    ): Map<String, Any?>

    @GET("templates")
    suspend fun listTemplates(@Header("Authorization") auth: String): Map<String, Any?>

    @POST("templates/{id}/render")
    suspend fun renderTemplate(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: Map<String, Any?>,
    ): Map<String, Any?>

    @POST("message-events")
    suspend fun createMessageEvent(@Header("Authorization") auth: String, @Body body: Map<String, Any?>): Map<String, Any?>

    @PATCH("message-events/{id}/status")
    suspend fun updateMessageStatus(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: Map<String, Any?>,
    ): Map<String, Any?>

    @GET("evidence/case/{caseId}")
    suspend fun listEvidenceForCase(@Header("Authorization") auth: String, @Path("caseId") caseId: String): Map<String, Any?>

    @POST("emergency-incidents")
    suspend fun startEmergencyIncident(@Header("Authorization") auth: String, @Body body: Map<String, Any?>): Map<String, Any?>

    @PATCH("emergency-incidents/{id}")
    suspend fun updateEmergencyIncident(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: Map<String, Any?>,
    ): Map<String, Any?>

    @POST("integrity-concerns")
    suspend fun reportIntegrityConcern(@Header("Authorization") auth: String, @Body body: Map<String, Any?>): Map<String, Any?>
}
