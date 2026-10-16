package com.pmfby.evidence.core.dto

/**
 * Plain Kotlin data classes + mapping functions mirroring the backend's actual JSON
 * response shapes (see backend-and-web/backend/src/routes, each route's .js file —
 * field names here match the Postgres column names the routes SELECT and return verbatim, e.g. `farmer_name`,
 * `district_id`, `contact_status`). Kept here (not in the `app` module) so the
 * parsing/mapping logic itself — not the Retrofit/Moshi wiring — is unit-testable on the
 * plain JVM. The `app` module's Retrofit service returns raw Map/JSON which these
 * functions turn into the app's domain model; Moshi/kotlinx.serialization annotations are
 * intentionally NOT used on these classes so this module has no JSON-library dependency.
 */

data class FarmerDto(
    val id: String,
    val farmerName: String,
    val farmerMobile: String?,
    val villageName: String?,
    val stateId: String?,
    val districtId: String?,
    val assignedUserId: String?,
)

data class SurveyCaseDto(
    val id: String,
    val farmerId: String,
    val contactStatus: String?,
    val attemptCount: Int,
    val postponedUntil: String?,
    val closedAt: String?,
    val forcefulSurveyFlag: Boolean,
)

data class CallEventDto(
    val id: String,
    val caseId: String,
    val attemptNo: Int,
    val outcome: String?,
    val outcomeSource: String?,
    val geoLat: Double?,
    val geoLon: Double?,
    val geoAccuracyM: Double?,
)

/**
 * Thrown when a required field is missing/malformed in a server response — surfaced to
 * the caller rather than silently producing a half-populated DTO (a null farmer name or
 * case id would corrupt the offline cache and every screen reading from it).
 */
class DtoParseException(message: String) : Exception(message)

object ApiDtoMapper {

    private fun str(map: Map<String, Any?>, key: String): String? = map[key]?.toString()

    private fun requireStr(map: Map<String, Any?>, key: String, context: String): String =
        str(map, key)?.takeIf { it.isNotBlank() }
            ?: throw DtoParseException("Missing required field '$key' while parsing $context")

    private fun toInt(value: Any?): Int = when (value) {
        null -> 0
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

    private fun toDoubleOrNull(value: Any?): Double? = when (value) {
        null -> null
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun toBool(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> false
    }

    fun farmerFromJson(map: Map<String, Any?>): FarmerDto = FarmerDto(
        id = requireStr(map, "id", "farmer"),
        farmerName = requireStr(map, "farmer_name", "farmer"),
        farmerMobile = str(map, "farmer_mobile"),
        villageName = str(map, "village_name"),
        stateId = str(map, "state_id"),
        districtId = str(map, "district_id"),
        assignedUserId = str(map, "assigned_user_id"),
    )

    fun surveyCaseFromJson(map: Map<String, Any?>): SurveyCaseDto = SurveyCaseDto(
        id = requireStr(map, "id", "survey_case"),
        farmerId = requireStr(map, "farmer_id", "survey_case"),
        contactStatus = str(map, "contact_status"),
        attemptCount = toInt(map["attempt_count"]),
        postponedUntil = str(map, "postponed_until"),
        closedAt = str(map, "closed_at"),
        forcefulSurveyFlag = toBool(map["forceful_survey_flag"]),
    )

    fun callEventFromJson(map: Map<String, Any?>): CallEventDto = CallEventDto(
        id = requireStr(map, "id", "call_event"),
        caseId = requireStr(map, "case_id", "call_event"),
        attemptNo = toInt(map["attempt_no"]),
        outcome = str(map, "outcome"),
        outcomeSource = str(map, "outcome_source"),
        geoLat = toDoubleOrNull(map["geo_lat"]),
        geoLon = toDoubleOrNull(map["geo_lon"]),
        geoAccuracyM = toDoubleOrNull(map["geo_accuracy_m"]),
    )

    fun farmersFromJsonList(list: List<Map<String, Any?>>): List<FarmerDto> = list.map { farmerFromJson(it) }
}
