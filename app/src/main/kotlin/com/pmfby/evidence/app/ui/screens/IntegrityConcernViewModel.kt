package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.repository.ActionResult
import com.pmfby.evidence.app.data.repository.CaseActionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IntegrityConcernUiState(
    val description: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

/**
 * Confidential bribery/inducement reporting — intentionally separate from the Emergency
 * flow (personal safety) and from Discrepancy flags (fraud/data-mismatch). Mirrors
 * backend/src/routes/integrityConcerns.js exactly: any authenticated user may submit, only
 * super_admin may ever list them, and there is NO edit/delete route anywhere — once sent,
 * it cannot be taken back or corrected from this app. The UI makes that permanence clear
 * before submitting rather than implying it is a normal, revisable form.
 */
class IntegrityConcernViewModel(
    private val caseActionsRepository: CaseActionsRepository,
    private val caseId: String?,
    private val farmerId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntegrityConcernUiState())
    val uiState: StateFlow<IntegrityConcernUiState> = _uiState.asStateFlow()

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value, error = null) }
    }

    fun submit() {
        val description = _uiState.value.description
        if (description.isBlank()) {
            _uiState.update { it.copy(error = "Please describe the concern before submitting") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = caseActionsRepository.reportIntegrityConcern(caseId, farmerId, description)
            when (result) {
                is ActionResult.Submitted -> _uiState.update { it.copy(isSubmitting = false, submitted = true) }
                is ActionResult.QueuedOffline -> _uiState.update { it.copy(isSubmitting = false, submitted = true) }
                is ActionResult.Failed -> _uiState.update { it.copy(isSubmitting = false, error = result.message) }
            }
        }
    }
}
