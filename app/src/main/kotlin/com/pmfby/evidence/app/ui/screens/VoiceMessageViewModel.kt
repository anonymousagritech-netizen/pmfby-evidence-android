package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.data.remote.FileUploadApi
import com.pmfby.evidence.app.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Moshi parses every JSON number inside a `Map<String, Any?>` as a Double, so an integer id
 * of 1 turns into "1.0" via toString() — which Postgres rejects for an integer column (HTTP
 * 500). This renders whole-number Doubles as plain integers.
 */
internal fun jsonIdToString(value: Any?): String? = when (value) {
    null -> null
    is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    else -> value.toString()
}

data class VoiceTemplateOption(val id: String, val code: String, val label: String)

data class VoiceMessageUiState(
    val templates: List<VoiceTemplateOption> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedTemplateId: String? = null,
)

/**
 * Local playback of admin-managed prerecorded voice-message templates.
 *
 * HONEST LIMITATION (see LIMITATIONS.md): this build has no audio content to play. The
 * backend's `message_templates` table (backend-and-web/backend/src/routes/templates.js)
 * models text SMS templates only — there is no modeled endpoint or storage column for an
 * admin-uploaded audio asset per template, so there is nothing real for this screen to
 * download and play yet. The player wiring below (MediaPlayer-based, see
 * ui.screens.VoiceMessageScreen) is real and would work the moment a `localAudioPath`
 * actually points at a file; until the audio-asset pipeline exists server-side, every
 * template here reports [VoiceTemplateOption] with no path, and the screen disables Play
 * rather than pretending a prerecorded message exists. "Live-call auto-injection" (playing
 * the message INTO an ongoing cellular call automatically) is a separate, much harder
 * platform limitation and is never attempted here or anywhere else in this app — see
 * LIMITATIONS.md "Automated voice-message injection into a live call".
 *
 * What IS real: selecting a template logs a `call_recordings` row
 * (kind=voice_message_playback) via FileUploadApi, exactly mirroring
 * backend/src/routes/callRecordings.js's existing support for that kind with no file.
 */
class VoiceMessageViewModel(
    private val api: ApiService,
    private val session: SessionManager,
    private val fileUploadApi: FileUploadApi,
    private val caseId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceMessageUiState())
    val uiState: StateFlow<VoiceMessageUiState> = _uiState.asStateFlow()

    init {
        loadTemplates()
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            val auth = session.authHeader() ?: run {
                _uiState.update { it.copy(error = "Not signed in") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.listTemplates(auth)
                @Suppress("UNCHECKED_CAST")
                val raw = response["templates"] as? List<Map<String, Any?>> ?: emptyList()
                val options = raw.mapNotNull { row ->
                    val id = jsonIdToString(row["id"]) ?: return@mapNotNull null
                    val code = row["code"]?.toString() ?: return@mapNotNull null
                    VoiceTemplateOption(id, code, code.replace('_', ' '))
                }
                _uiState.update { it.copy(templates = options, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load templates") }
            }
        }
    }

    fun logPlaybackSelection(template: VoiceTemplateOption) {
        viewModelScope.launch {
            val auth = session.authHeader() ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    fileUploadApi.uploadCallRecording(
                        authHeader = auth,
                        file = null,
                        mimeType = "audio/m4a",
                        fields = mapOf(
                            "caseId" to caseId,
                            "kind" to "voice_message_playback",
                            "voiceTemplateId" to template.id,
                        ),
                    )
                }
                _uiState.update { it.copy(loggedTemplateId = template.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Could not log playback") }
            }
        }
    }
}
