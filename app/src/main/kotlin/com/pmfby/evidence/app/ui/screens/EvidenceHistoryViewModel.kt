package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EvidenceRow(val id: String, val kind: String, val uploadStatus: String, val createdAt: String?, val originalFilename: String?)

data class EvidenceHistoryUiState(
    val rows: List<EvidenceRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class EvidenceHistoryViewModel(
    private val api: ApiService,
    private val session: SessionManager,
    private val caseId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EvidenceHistoryUiState())
    val uiState: StateFlow<EvidenceHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val auth = session.authHeader() ?: run {
                _uiState.update { it.copy(error = "Not signed in") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.listEvidenceForCase(auth, caseId)
                @Suppress("UNCHECKED_CAST")
                val raw = response["evidenceFiles"] as? List<Map<String, Any?>> ?: emptyList()
                val rows = raw.map {
                    EvidenceRow(
                        id = it["id"]?.toString().orEmpty(),
                        kind = it["kind"]?.toString().orEmpty(),
                        uploadStatus = it["upload_status"]?.toString() ?: "pending",
                        createdAt = it["created_at"]?.toString(),
                        originalFilename = it["original_filename"]?.toString(),
                    )
                }
                _uiState.update { it.copy(rows = rows, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load evidence history") }
            }
        }
    }
}
