package com.pmfby.evidence.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.data.remote.FileUploadApi
import com.pmfby.evidence.app.data.repository.ActionResult
import com.pmfby.evidence.app.data.repository.CaseActionsRepository
import com.pmfby.evidence.app.data.repository.FarmerRepository
import com.pmfby.evidence.app.session.SessionManager
import com.pmfby.evidence.app.telephony.DirectSmsSender
import com.pmfby.evidence.core.template.TemplateRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    val messageEventId: String? = null,
    val isUploadingScreenshot: Boolean = false,
    val screenshotUploaded: Boolean = false,
    /** Direct-send status — null until attempted. */
    val directSendStatus: String? = null,
    val directSendResultDetail: String? = null,
    val selectedTemplateCode: String? = null,
    val postponedSurveyDate: String = "",
    val postponeReason: String = "",
)

/**
 * Template picker + SMS send. Supports two modes:
 *   - sessionId mode (new): one contact for all dockets in a farmer-batch.
 *   - caseId mode (legacy): per-case contact (CaseDetailScreen still uses this).
 *
 * Session mode sends sessionId to both the render endpoint and the message-events endpoint,
 * so the backend records the evidence against the whole batch (not a single case).
 */
class SmsTemplateViewModel(
    private val appContext: Context,
    private val fileUploadApi: FileUploadApi,
    private val api: ApiService,
    private val session: SessionManager,
    private val farmerRepository: FarmerRepository,
    private val caseActionsRepository: CaseActionsRepository,
    /** Exactly one of these must be non-null. sessionId = batch contact, caseId = single case. */
    private val caseId: String?,
    private val sessionId: String?,
    /** If set, the matching template is auto-selected after loading (skips the template picker). */
    private val preselectedTemplateCode: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsTemplateUiState())
    val uiState: StateFlow<SmsTemplateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (sessionId != null) {
                // Session mode: load farmer via session details from API
                val auth = session.authHeader()
                if (auth != null) {
                    try {
                        val response = api.getContactSession(auth, sessionId)
                        @Suppress("UNCHECKED_CAST")
                        val sessionMap = response["session"] as? Map<String, Any?>
                        val farmerId = sessionMap?.get("farmer_id")?.toString()
                        if (farmerId != null) {
                            val farmer = farmerRepository.getCachedFarmer(farmerId)
                            _uiState.update { it.copy(farmer = farmer) }
                        }
                    } catch (_: Exception) { /* farmer stays null, not fatal */ }
                }
            } else if (caseId != null) {
                val case = farmerRepository.getCachedCase(caseId)
                val farmer = case?.let { farmerRepository.getCachedFarmer(it.farmerId) }
                _uiState.update { it.copy(farmer = farmer) }
            }
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
                val id = jsonIdToString(row["id"]) ?: return@mapNotNull null
                val code = row["code"]?.toString() ?: return@mapNotNull null
                if (!TemplateRenderer.isEligibleForSmsComposer(code)) return@mapNotNull null
                SmsTemplateOption(id, code, code.replace('_', ' '))
            }
            _uiState.update { it.copy(templates = options, isLoading = false) }
            // Auto-select if a template code was pre-specified (e.g. from CaseDetailScreen SMS sheet)
            if (preselectedTemplateCode != null) {
                options.firstOrNull { it.code == preselectedTemplateCode }?.let { selectTemplate(it.id) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load templates") }
        }
    }

    fun selectTemplate(templateId: String) {
        val code = _uiState.value.templates.firstOrNull { it.id == templateId }?.code
        _uiState.update {
            it.copy(
                selectedTemplateId = templateId, selectedTemplateCode = code,
                renderedText = "", missingFields = emptyList(),
                postponedSurveyDate = "", postponeReason = "",
            )
        }
        reRender()
    }

    fun onPostponedSurveyDateChange(value: String) {
        _uiState.update { it.copy(postponedSurveyDate = value) }
        reRender()
    }

    fun onPostponeReasonChange(value: String) {
        _uiState.update { it.copy(postponeReason = value) }
        reRender()
    }

    fun isPostponeDataMissing(): Boolean {
        val state = _uiState.value
        return state.selectedTemplateCode == "FORMAT_2_POSTPONED" &&
            (state.postponedSurveyDate.isBlank() || state.postponeReason.isBlank())
    }

    private fun reRender() {
        val templateId = _uiState.value.selectedTemplateId ?: return
        viewModelScope.launch {
            val auth = session.authHeader() ?: return@launch
            try {
                val state = _uiState.value
                val body = buildMap<String, Any?> {
                    if (sessionId != null) put("sessionId", sessionId)
                    else if (caseId != null) put("caseId", caseId)
                    if (state.selectedTemplateCode == "FORMAT_2_POSTPONED") {
                        put("postponedSurveyDate", state.postponedSurveyDate.ifBlank { null })
                        put("postponeReason", state.postponeReason.ifBlank { null })
                    }
                }
                val response = api.renderTemplate(auth, templateId, body)
                val rendered = response["rendered"]?.toString().orEmpty()
                @Suppress("UNCHECKED_CAST")
                val missing = (response["missing"] as? List<String>).orEmpty()
                _uiState.update { it.copy(renderedText = rendered, missingFields = missing) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Could not render template") }
            }
        }
    }

    fun markComposerResult(opened: Boolean) {
        val templateId = _uiState.value.selectedTemplateId ?: return
        val renderedText = _uiState.value.renderedText
        val phone = _uiState.value.farmer?.farmerMobile
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val status = if (opened) "opened_composer" else "drafted"
            val result = if (sessionId != null) {
                caseActionsRepository.submitMessageEventForSession(sessionId, templateId, renderedText, phone, status)
            } else {
                caseActionsRepository.submitMessageEvent(caseId!!, templateId, renderedText, phone, status)
            }
            when (result) {
                is ActionResult.Submitted -> {
                    @Suppress("UNCHECKED_CAST")
                    val eventId = (result.serverResponse["messageEvent"] as? Map<String, Any?>)?.get("id")?.toString()
                    _uiState.update { it.copy(isSending = false, composerOpened = opened, messageEventId = eventId) }
                }
                is ActionResult.QueuedOffline ->
                    _uiState.update { it.copy(isSending = false, composerOpened = opened) }
                is ActionResult.Failed -> _uiState.update { it.copy(isSending = false, error = result.message) }
            }
        }
    }

    fun sendDirectly() {
        val templateId = _uiState.value.selectedTemplateId ?: return
        val renderedText = _uiState.value.renderedText
        val rawPhone = _uiState.value.farmer?.farmerMobile
        if (rawPhone.isNullOrBlank()) {
            _uiState.update { it.copy(error = "No farmer mobile number on file") }
            return
        }
        val digits = rawPhone.filter { it.isDigit() }
        val phone = when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            rawPhone.startsWith("+") -> rawPhone
            else -> rawPhone
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, directSendStatus = "sending") }
            var messageEventId: String? = null
            var sawAnyResult = false
            val timedOut = kotlinx.coroutines.withTimeoutOrNull(25_000) {
                DirectSmsSender.send(appContext, phone, renderedText).collect { result ->
                    sawAnyResult = true
                    val status = when (result.stage) {
                        DirectSmsSender.Stage.SENT -> "sent"
                        DirectSmsSender.Stage.SENT_FAILED -> "send_failed"
                        DirectSmsSender.Stage.DELIVERED -> "delivered"
                        DirectSmsSender.Stage.DELIVERY_FAILED -> "delivery_failed"
                        DirectSmsSender.Stage.DELIVERY_UNKNOWN -> "sent"
                    }
                    val detail = if (result.stage == DirectSmsSender.Stage.SENT_FAILED || result.stage == DirectSmsSender.Stage.DELIVERY_FAILED) {
                        DirectSmsSender.resultCodeName(result.resultCode)
                    } else null
                    _uiState.update { it.copy(directSendStatus = status, directSendResultDetail = detail) }
                    if (messageEventId == null) {
                        val submitResult = if (sessionId != null) {
                            caseActionsRepository.submitMessageEventForSession(sessionId, templateId, renderedText, phone, status)
                        } else {
                            caseActionsRepository.submitMessageEvent(caseId!!, templateId, renderedText, phone, status)
                        }
                        if (submitResult is ActionResult.Submitted) {
                            @Suppress("UNCHECKED_CAST")
                            messageEventId = (submitResult.serverResponse["messageEvent"] as? Map<String, Any?>)?.get("id")?.toString()
                            _uiState.update { it.copy(messageEventId = messageEventId) }
                        }
                    } else {
                        caseActionsRepository.updateMessageStatus(messageEventId!!, status)
                    }
                }
            }
            if (timedOut == null && !sawAnyResult) {
                // MIUI/RCS interception — auto-open SMS composer
                _uiState.update { it.copy(directSendStatus = "send_status_unknown") }
                if (sessionId != null) {
                    caseActionsRepository.submitMessageEventForSession(sessionId, templateId, renderedText, phone, "send_status_unknown")
                } else {
                    caseActionsRepository.submitMessageEvent(caseId!!, templateId, renderedText, phone, "send_status_unknown")
                }
            }
            _uiState.update { it.copy(isSending = false) }
        }
    }

    fun uploadScreenshot(uri: Uri) {
        viewModelScope.launch {
            val auth = session.authHeader() ?: run {
                _uiState.update { it.copy(error = "Not signed in") }
                return@launch
            }
            _uiState.update { it.copy(isUploadingScreenshot = true, screenshotUploaded = false, error = null) }
            var file: File? = null
            var fields: Map<String, String> = emptyMap()
            try {
                withContext(Dispatchers.IO) {
                    val resolver = appContext.contentResolver
                    val mime = resolver.getType(uri) ?: "image/png"
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "sms_${System.currentTimeMillis()}.png"
                    val dir = File(appContext.cacheDir, "evidence").apply { mkdirs() }
                    val target = File(dir, "sms_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                    file = target
                    resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                        ?: throw java.io.IOException("Could not open the selected image")
                    fields = buildMap {
                        if (sessionId != null) put("sessionId", sessionId)
                        else if (caseId != null) put("caseId", caseId)
                        put("kind", "screenshot")
                        put("mimeType", mime)
                        _uiState.value.messageEventId?.let { put("messageEventId", it) }
                    }
                    fileUploadApi.uploadEvidence(authHeader = auth, file = target, mimeType = mime, fields = fields)
                }
                file?.delete()
                _uiState.update { it.copy(isUploadingScreenshot = false, screenshotUploaded = true) }
            } catch (e: Exception) {
                val saved = file
                if (saved != null && saved.exists() && fields.isNotEmpty()) {
                    caseActionsRepository.enqueueEvidenceForRetry(saved.absolutePath, fields.mapValues { it.value })
                    _uiState.update { it.copy(isUploadingScreenshot = false, error = "Upload failed (${e.message}). Saved and queued for automatic retry.") }
                } else {
                    _uiState.update { it.copy(isUploadingScreenshot = false, error = "Screenshot upload failed: ${e.message}") }
                }
            }
        }
    }
}
