package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.data.repository.ActionResult
import com.pmfby.evidence.app.data.repository.CaseActionsRepository
import com.pmfby.evidence.app.data.repository.FarmerRepository
import com.pmfby.evidence.app.session.SessionManager
import com.pmfby.evidence.core.template.TemplateRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SmsTemplateOption(val id: String, val code: String, val label: String)

data class SmsTemplateUiState(
    val farmer: FarmerEntity? = null,
    val templates: List<SmsTemplateOption> = emptyList(),
    val selectedTemplateId: String? = null,
    val renderedText: String = "",
    val missingFields: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val composerOpened: Boolean = false,
)

/**
 * Template picker + preview + send-via-composer. The live preview the picker shows while
 * choosing is rendered locally with :core's TemplateRenderer (same placeholder syntax as
 * the backend's utils/templateRenderer.js) purely so the surveyor sees *something*
 * immediately; the text actually sent is always re-rendered server-side via
 * POST /templates/:id/render right before the composer opens, since Attempt_No is
 * backend-assigned and can't be trusted from a local/stale value (see TemplateRenderer's
 * class doc). FORMAT_4_INCIDENT is filtered out entirely — see isEligibleForSmsComposer.
 */
class SmsTemplateViewModel(
    private val api: ApiService,
    private val session: SessionManager,
    private val farmerRepository: FarmerRepository,
    private val caseActionsRepository: CaseActionsRepository,
    private val caseId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsTemplateUiState())
    val uiState: StateFlow<SmsTemplateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val case = farmerRepository.getCachedCase(caseId)
            val farmer = case?.let { farmerRepository.getCachedFarmer(it.farmerId) }
            _uiState.update { it.copy(farmer = farmer) }
            loadTemplates()
        }
    }

    private suspend fun loadTemplates() {
        val auth = session.authHeader() ?: run {
            _uiState.update { it.copy(error = "Not signed in") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val response = api.listTemplates(auth)
            @Suppress("UNCHECKED_CAST")
            val raw = response["templates"] as? List<Map<String, Any?>> ?: emptyList()
            val options = raw.mapNotNull { row ->
                val id = row["id"]?.toString() ?: return@mapNotNull null
                val code = row["code"]?.toString() ?: return@mapNotNull null
                if (!TemplateRenderer.isEligibleForSmsComposer(code)) return@mapNotNull null
                SmsTemplateOption(id, code, code.replace('_', ' '))
            }
            _uiState.update { it.copy(templates = options, isLoading = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load templates") }
        }
    }

    fun selectTemplate(templateId: String) {
        _uiState.update { it.copy(selectedTemplateId = templateId, renderedText = "", missingFields = emptyList()) }
        viewModelScope.launch {
            val auth = session.authHeader() ?: return@launch
            try {
                val response = api.renderTemplate(auth, templateId, mapOf("caseId" to caseId))
                val rendered = response["rendered"]?.toString().orEmpty()
                @Suppress("UNCHECKED_CAST")
                val missing = (response["missing"] as? List<String>).orEmpty()
                _uiState.update { it.copy(renderedText = rendered, missingFields = missing) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Could not render template") }
            }
        }
    }

    /** Hands off to the SMS composer, then logs whether it actually opened — see SmsComposer doc. */
    fun markComposerResult(opened: Boolean) {
        val templateId = _uiState.value.selectedTemplateId ?: return
        val renderedText = _uiState.value.renderedText
        val phone = _uiState.value.farmer?.farmerMobile
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val status = if (opened) "opened_composer" else "drafted"
            val result = caseActionsRepository.submitMessageEvent(caseId, templateId, renderedText, phone, status)
            when (result) {
                is ActionResult.Submitted, is ActionResult.QueuedOffline ->
                    _uiState.update { it.copy(isSending = false, composerOpened = opened) }
                is ActionResult.Failed -> _uiState.update { it.copy(isSending = false, error = result.message) }
            }
        }
    }
}
