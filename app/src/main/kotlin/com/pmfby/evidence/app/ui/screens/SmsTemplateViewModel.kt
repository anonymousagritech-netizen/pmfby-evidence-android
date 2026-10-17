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
    private val appContext: Context,
    private val fileUploadApi: FileUploadApi,
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
                val id = jsonIdToString(row["id"]) ?: return@mapNotNull null
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

    /**
     * Android never lets one app silently screenshot another (the SMS app), so the surveyor
     * takes the phone's own screenshot of the sent message and attaches it here. It is stored
     * as `evidence_files` kind=screenshot, linked to this case and this message event. If the
     * upload fails the file is queued for automatic retry instead of being lost.
     */
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
                        put("caseId", caseId)
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
                    caseActionsRepository.enqueueEvidenceForRetry(saved.absolutePath, fields)
                    _uiState.update { it.copy(isUploadingScreenshot = false, error = "Upload failed (${e.message}). Saved and queued for automatic retry.") }
                } else {
                    _uiState.update { it.copy(isUploadingScreenshot = false, error = "Screenshot upload failed: ${e.message}") }
                }
            }
        }
    }
}
