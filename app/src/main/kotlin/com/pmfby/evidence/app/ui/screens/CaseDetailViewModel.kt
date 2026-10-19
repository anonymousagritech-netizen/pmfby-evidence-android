package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.local.SurveyCaseEntity
import com.pmfby.evidence.app.data.repository.ActionResult
import com.pmfby.evidence.app.data.repository.CaseActionsRepository
import com.pmfby.evidence.app.data.repository.FarmerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CaseDetailUiState(
    val case: SurveyCaseEntity? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val lastActionMessage: String? = null,
)

class CaseDetailViewModel(
    private val farmerRepository: FarmerRepository,
    private val caseActionsRepository: CaseActionsRepository,
    private val caseId: String,
) : ViewModel() {

    private val submitting = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val lastActionMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CaseDetailUiState> = combine(
        farmerRepository.observeCase(caseId),
        submitting,
        error,
        lastActionMessage,
    ) { case, isSubmitting, err, message ->
        CaseDetailUiState(case = case, isSubmitting = isSubmitting, error = err, lastActionMessage = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CaseDetailUiState())

    private val farmerState = MutableStateFlow<FarmerEntity?>(null)
    val farmer: StateFlow<FarmerEntity?> = farmerState

    init {
        viewModelScope.launch {
            val case = farmerRepository.getCachedCase(caseId)
            farmerState.value = case?.let { farmerRepository.getCachedFarmer(it.farmerId) }
        }
    }

    /** The mandatory outcome-resolution loop (non-completed outcomes only — see CaseResolutionOutcome). */
    fun resolveOutcome(outcome: CaseResolutionOutcome, reason: String?, farmerAgreedToPostpone: Boolean?, postponedUntilIso: String?) {
        viewModelScope.launch {
            submitting.value = true
            error.value = null
            val result = caseActionsRepository.resolveCaseOutcome(caseId, outcome.wireValue, reason, farmerAgreedToPostpone, postponedUntilIso)
            applyResult(result, "Outcome recorded: ${outcome.name.lowercase().replace('_', ' ')}")
        }
    }

    /** The ONLY path that fully closes a case as successfully surveyed. */
    fun markSurveyCompleted(reason: String?) {
        viewModelScope.launch {
            submitting.value = true
            error.value = null
            val result = caseActionsRepository.resolveCaseOutcome(caseId, "completed", reason, null, null)
            applyResult(result, "Survey marked completed")
        }
    }

    private fun applyResult(result: ActionResult, successMessage: String) {
        when (result) {
            is ActionResult.Submitted -> {
                submitting.value = false
                lastActionMessage.value = successMessage
            }
            is ActionResult.QueuedOffline -> {
                submitting.value = false
                lastActionMessage.value = "$successMessage (queued offline — will sync when connected)"
            }
            is ActionResult.Failed -> {
                submitting.value = false
                error.value = result.message
            }
        }
    }
}
