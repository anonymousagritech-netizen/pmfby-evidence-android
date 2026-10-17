package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.remote.FileUploadApi
import com.pmfby.evidence.app.data.repository.ActionResult
import com.pmfby.evidence.app.data.repository.CaseActionsRepository
import com.pmfby.evidence.app.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Mirrors backend/src/routes/cases.js discrepancy-flags validReasons exactly. */
enum class DiscrepancyReason(val wireValue: String, val label: String) {
    CROP_MISMATCH("crop_mismatch", "Crop mismatch"),
    LAND_MISMATCH("land_mismatch", "Land mismatch"),
    DUPLICATE_CLAIM("duplicate_claim", "Duplicate claim"),
    OTHER("other", "Other"),
}

data class DiscrepancyFlagUiState(
    val reason: DiscrepancyReason = DiscrepancyReason.CROP_MISMATCH,
    val description: String = "",
    val photoFile: File? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

/**
 * Suspected-discrepancy (fraud/data-mismatch) flag — a photo is MANDATORY (task spec),
 * enforced here by [canSubmit] staying false without one, mirroring the backend's own
 * hard requirement (cases.js: "A photo evidence_file is mandatory for a discrepancy
 * flag"). The photo is uploaded as evidence kind='other' (evidence.js's VALID_KINDS has
 * no dedicated 'discrepancy_photo' kind — see README) and the resulting evidence_file id
 * is what's actually passed as evidenceFileId on the flag itself.
 */
class DiscrepancyFlagViewModel(
    private val session: SessionManager,
    private val fileUploadApi: FileUploadApi,
    private val caseActionsRepository: CaseActionsRepository,
    private val caseId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscrepancyFlagUiState())
    val uiState: StateFlow<DiscrepancyFlagUiState> = _uiState.asStateFlow()

    val canSubmit: Boolean get() = _uiState.value.photoFile != null && _uiState.value.description.isNotBlank() && !_uiState.value.isSubmitting

    fun onReasonChange(reason: DiscrepancyReason) {
        _uiState.update { it.copy(reason = reason) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onPhotoCaptured(file: File) {
        _uiState.update { it.copy(photoFile = file, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        val photo = state.photoFile ?: run {
            _uiState.update { it.copy(error = "A photo is required to raise a discrepancy flag") }
            return
        }
        if (state.description.isBlank()) {
            _uiState.update { it.copy(error = "A description is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val auth = session.authHeader()
            if (auth == null) {
                _uiState.update { it.copy(isSubmitting = false, error = "Not signed in") }
                return@launch
            }
            try {
                val evidenceResponse = withContext(Dispatchers.IO) {
                    fileUploadApi.uploadEvidence(
                        authHeader = auth,
                        file = photo,
                        mimeType = "image/jpeg",
                        fields = mapOf("caseId" to caseId, "kind" to "other"),
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val evidenceFileId = (evidenceResponse["evidenceFile"] as? Map<String, Any?>)?.get("id")?.toString()
                if (evidenceFileId == null) {
                    _uiState.update { it.copy(isSubmitting = false, error = "Photo upload did not return an evidence file id") }
                    return@launch
                }
                val result = caseActionsRepository.raiseDiscrepancyFlag(caseId, state.reason.wireValue, state.description, evidenceFileId)
                when (result) {
                    is ActionResult.Submitted -> _uiState.update { it.copy(isSubmitting = false, submitted = true) }
                    is ActionResult.Failed -> _uiState.update { it.copy(isSubmitting = false, error = result.message) }
                    is ActionResult.QueuedOffline -> _uiState.update { it.copy(isSubmitting = false, submitted = true) }
                }
            } catch (e: Exception) {
                // Mandatory evidence can't be "queued offline" the way a JSON action can —
                // raising the flag needs a real server-issued evidenceFileId first. Fail
                // honestly rather than pretending a discrepancy flag was recorded.
                _uiState.update { it.copy(isSubmitting = false, error = "Could not upload photo (are you offline?): ${e.message}") }
            }
        }
    }
}
