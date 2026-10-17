package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.local.SurveyCaseEntity
import com.pmfby.evidence.app.data.repository.ActionResult
import com.pmfby.evidence.app.data.repository.CaseActionsRepository
import com.pmfby.evidence.app.data.repository.FarmerRepository
import com.pmfby.evidence.app.location.GeoTag
import com.pmfby.evidence.app.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PersonContactedType { FARMER, FAMILY_MEMBER, NEIGHBOR, OTHER }

/** Mirrors backend/src/routes/callEvents.js VALID_CALL_OUTCOMES exactly. */
enum class RawCallOutcome(val wireValue: String) {
    ANSWERED("answered"),
    NOT_ANSWERED("not_answered"),
    BUSY("busy"),
    SWITCHED_OFF("switched_off"),
    UNREACHABLE("unreachable"),
    INVALID_NUMBER("invalid_number"),
    FARMER_NOT_FOUND("farmer_not_found"),
}

/** The mandatory 4-outcome resolution loop for a non-completed contact attempt — mirrors
 * backend/src/routes/cases.js VALID_OUTCOMES (minus 'completed', which is its own
 * separate, always-fully-closing flow; see CaseDetailScreen's "Survey Completed" button). */
enum class CaseResolutionOutcome(val wireValue: String) {
    DENIED("denied"),
    POSTPONED("postponed"),
    WRONG_NUMBER("wrong_number"),
    DISPUTE_INCIDENT("dispute_incident"),
}

data class CallUiState(
    val farmer: FarmerEntity? = null,
    val case: SurveyCaseEntity? = null,
    val callEventId: String? = null,
    val isDialing: Boolean = false,
    val geoTag: GeoTag? = null,
    val personContactedType: PersonContactedType? = null,
    val personContactedName: String = "",
    val personContactedRelation: String = "",
    val isSubmittingOutcome: Boolean = false,
    val outcomeSubmitted: Boolean = false,
    val error: String? = null,
)

class CallViewModel(
    private val farmerRepository: FarmerRepository,
    private val caseActionsRepository: CaseActionsRepository,
    private val locationProvider: LocationProvider,
    private val caseId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val case = farmerRepository.getCachedCase(caseId)
            val farmer = case?.let { farmerRepository.getCachedFarmer(it.farmerId) }
            _uiState.update { it.copy(case = case, farmer = farmer) }
        }
    }

    /** Captures a geotag and records the call attempt — called right before CallLauncher.dial(). */
    fun startCallAttempt() {
        val phone = _uiState.value.farmer?.farmerMobile
        if (phone.isNullOrBlank()) {
            _uiState.update { it.copy(error = "No farmer mobile number on file") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDialing = true, error = null) }
            val geo = locationProvider.captureCurrentLocation()
            val result = caseActionsRepository.submitCallEvent(
                caseId = caseId,
                phoneNumber = phone,
                geo = geo?.let { mapOf("lat" to it.lat, "lon" to it.lon, "accuracyM" to it.accuracyM) },
            )
            val callEventId = when (result) {
                is ActionResult.Submitted -> {
                    @Suppress("UNCHECKED_CAST")
                    (result.serverResponse["callEvent"] as? Map<String, Any?>)?.get("id")?.toString()
                }
                is ActionResult.QueuedOffline -> null // queued; outcome can still be recorded locally once a real id exists after sync
                is ActionResult.Failed -> {
                    _uiState.update { it.copy(error = result.message) }
                    null
                }
            }
            _uiState.update { it.copy(isDialing = false, geoTag = geo, callEventId = callEventId) }
        }
    }

    fun onPersonContactedTypeChange(type: PersonContactedType) {
        _uiState.update { it.copy(personContactedType = type) }
    }

    fun onPersonContactedNameChange(value: String) {
        _uiState.update { it.copy(personContactedName = value) }
    }

    fun onPersonContactedRelationChange(value: String) {
        _uiState.update { it.copy(personContactedRelation = value) }
    }

    /** Logs the raw call-connection outcome (answered/not_answered/busy/...). */
    fun submitCallOutcome(outcome: RawCallOutcome) {
        val callEventId = _uiState.value.callEventId
        if (callEventId == null) {
            _uiState.update { it.copy(error = "Call attempt was queued offline — outcome will be loggable once it syncs") }
            return
        }
        val state = _uiState.value
        val personContacted = state.personContactedType?.let {
            mapOf(
                "type" to it.name.lowercase(),
                "name" to state.personContactedName.ifBlank { null },
                "relation" to state.personContactedRelation.ifBlank { null },
            )
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOutcome = true, error = null) }
            val geo = state.geoTag ?: locationProvider.captureCurrentLocation()
            val result = caseActionsRepository.submitCallOutcome(
                callEventId = callEventId,
                outcome = outcome.wireValue,
                personContacted = personContacted,
                geo = geo?.let { mapOf("lat" to it.lat, "lon" to it.lon, "accuracyM" to it.accuracyM) },
            )
            when (result) {
                is ActionResult.Submitted -> _uiState.update { it.copy(isSubmittingOutcome = false, outcomeSubmitted = true) }
                is ActionResult.Failed -> _uiState.update { it.copy(isSubmittingOutcome = false, error = result.message) }
                is ActionResult.QueuedOffline -> _uiState.update { it.copy(isSubmittingOutcome = false, outcomeSubmitted = true) }
            }
        }
    }

    /** The mandatory outcome-resolution loop — see CaseResolutionOutcome doc. */
    fun resolveCaseOutcome(outcome: CaseResolutionOutcome, reason: String?, postponedUntilIso: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingOutcome = true, error = null) }
            val result = caseActionsRepository.resolveCaseOutcome(caseId, outcome.wireValue, reason, postponedUntilIso)
            when (result) {
                is ActionResult.Submitted -> _uiState.update { it.copy(isSubmittingOutcome = false, outcomeSubmitted = true) }
                is ActionResult.Failed -> _uiState.update { it.copy(isSubmittingOutcome = false, error = result.message) }
                is ActionResult.QueuedOffline -> _uiState.update { it.copy(isSubmittingOutcome = false, outcomeSubmitted = true) }
            }
        }
    }
}
