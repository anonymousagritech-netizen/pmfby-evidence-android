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

/**
 * Manages creating a contact session for a farmer-batch (same farmer + same intimation date).
 * Used by FarmerDetailScreen when the surveyor taps "Call" or "SMS" on a batch row.
 * One session = one set of evidence for ALL dockets in that batch.
 */
class ContactSessionViewModel(
    private val api: ApiService,
    private val session: SessionManager,
) : ViewModel() {

    data class State(
        val isCreating: Boolean = false,
        /** Set after a session is successfully created — screen observes this to navigate. */
        val createdSessionId: String? = null,
        /** docketIdsText from the server — all docket IDs comma-separated for SMS template. */
        val docketIdsText: String? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun createSession(
        farmerId: String,
        intimationBatchDate: String?,
        contactType: String,        // "call", "sms", or "call_and_sms"
        onCreated: (sessionId: String, docketIdsText: String) -> Unit,
    ) {
        viewModelScope.launch {
            val auth = session.authHeader() ?: run {
                _state.update { it.copy(error = "Not signed in") }
                return@launch
            }
            _state.update { it.copy(isCreating = true, error = null) }
            try {
                val body = buildMap<String, Any?> {
                    put("farmerId", farmerId)
                    put("contactType", contactType)
                    if (!intimationBatchDate.isNullOrBlank()) put("intimationBatchDate", intimationBatchDate)
                }
                val response = api.createContactSession(auth, body)
                @Suppress("UNCHECKED_CAST")
                val sessionMap = response["session"] as? Map<String, Any?>
                val sessionId = sessionMap?.get("id")?.toString()
                val docketIdsText = response["docketIdsText"]?.toString() ?: ""
                if (sessionId != null) {
                    _state.update { it.copy(isCreating = false, createdSessionId = sessionId, docketIdsText = docketIdsText) }
                    onCreated(sessionId, docketIdsText)
                } else {
                    _state.update { it.copy(isCreating = false, error = "Server did not return session id") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isCreating = false, error = e.message ?: "Could not create session") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
