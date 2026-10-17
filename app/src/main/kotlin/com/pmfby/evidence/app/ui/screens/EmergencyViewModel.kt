package com.pmfby.evidence.app.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.capture.EmergencyCaptureService
import com.pmfby.evidence.app.data.remote.FileUploadApi
import com.pmfby.evidence.app.data.repository.ActionResult
import com.pmfby.evidence.app.data.repository.CaseActionsRepository
import com.pmfby.evidence.app.session.SessionManager
import com.pmfby.evidence.core.timer.EmergencyTimerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class EmergencyPhase { IDLE, STARTING, RECORDING, UPLOADING, STOPPED, CANCELLED, ERROR }

data class EmergencyUiState(
    val phase: EmergencyPhase = EmergencyPhase.IDLE,
    val elapsedSeconds: Int = 0,
    val remainingSeconds: Int = EmergencyTimerController.HARD_CAP_SECONDS,
    val hardCapped: Boolean = false,
    val error: String? = null,
)

/**
 * Binds to EmergencyCaptureService for the press-and-hold emergency audio+video capture.
 * This ViewModel owns the bind/unbind lifecycle and the backend incident record; the
 * Service owns the actual MediaRecorder/CameraX capture and the 120s hard-cap ticking (via
 * :core's EmergencyTimerController — unit tested there with a fake clock). See
 * EmergencyCaptureService's class doc for why there is deliberately no locked-screen or
 * background capture here.
 */
class EmergencyViewModel(
    private val appContext: Context,
    private val caseActionsRepository: CaseActionsRepository,
    private val session: SessionManager,
    private val fileUploadApi: FileUploadApi,
    private val caseId: String,
) : ViewModel(), EmergencyCaptureService.EmergencyCaptureListener {

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    private var service: EmergencyCaptureService? = null
    private var bound = false
    private var incidentId: String? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? EmergencyCaptureService.LocalBinder ?: return
            service = local.getService()
            service?.setListener(this@EmergencyViewModel)
            service?.startCapture(caseId)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    /** Called once the user has confirmed the press-and-hold gesture (see EmergencyScreen). */
    fun beginCapture() {
        if (_uiState.value.phase != EmergencyPhase.IDLE) return
        _uiState.update { it.copy(phase = EmergencyPhase.STARTING, error = null) }

        viewModelScope.launch {
            val result = caseActionsRepository.submitEmergencyIncident(caseId, "THREAT")
            when (result) {
                is ActionResult.Submitted -> {
                    @Suppress("UNCHECKED_CAST")
                    incidentId = (result.serverResponse["incident"] as? Map<String, Any?>)?.get("id")?.toString()
                }
                is ActionResult.QueuedOffline -> incidentId = null // finalized update will no-op if this never resolves
                is ActionResult.Failed -> {
                    _uiState.update { it.copy(error = "Could not record emergency start: ${result.message}") }
                }
            }
        }

        EmergencyCaptureService.start(appContext)
        val intent = Intent(appContext, EmergencyCaptureService::class.java)
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        _uiState.update { it.copy(phase = EmergencyPhase.RECORDING) }
    }

    /** User released the press-and-hold before the hard cap, or tapped Stop explicitly. */
    fun releaseCapture() {
        service?.stopCapture()
    }

    fun cancelCapture() {
        service?.cancelCapture()
    }

    override fun onTick(elapsedSeconds: Int, remainingSeconds: Int) {
        _uiState.update { it.copy(elapsedSeconds = elapsedSeconds, remainingSeconds = remainingSeconds) }
    }

    override fun onStopped(file: File?, durationSeconds: Int, hardCapped: Boolean) {
        _uiState.update { it.copy(phase = EmergencyPhase.UPLOADING, hardCapped = hardCapped) }
        viewModelScope.launch {
            finalizeIncident(status = "completed", durationSeconds = durationSeconds)
            if (file != null && file.exists()) uploadFile(file)
            unbindService()
            _uiState.update { it.copy(phase = EmergencyPhase.STOPPED) }
        }
    }

    override fun onCancelled() {
        viewModelScope.launch {
            finalizeIncident(status = "cancelled", durationSeconds = 0)
            unbindService()
            _uiState.update { it.copy(phase = EmergencyPhase.CANCELLED) }
        }
    }

    override fun onError(message: String) {
        _uiState.update { it.copy(phase = EmergencyPhase.ERROR, error = message) }
    }

    private suspend fun finalizeIncident(status: String, durationSeconds: Int) {
        val id = incidentId ?: return
        caseActionsRepository.updateEmergencyIncident(id, status, durationSeconds, description = null)
    }

    private suspend fun uploadFile(file: File) {
        val auth = session.authHeader() ?: return
        val id = incidentId
        val fields = buildMap {
            put("caseId", caseId)
            put("kind", "emergency_video")
            put("mimeType", "video/mp4")
            if (id != null) put("emergencyIncidentId", id)
        }
        try {
            withContext(Dispatchers.IO) {
                fileUploadApi.uploadEvidence(authHeader = auth, file = file, mimeType = "video/mp4", fields = fields)
            }
        } catch (e: Exception) {
            caseActionsRepository.enqueueEvidenceForRetry(file.absolutePath, fields)
            _uiState.update { it.copy(error = "Recording saved locally but upload failed: ${e.message}. Queued for automatic retry.") }
        }
    }

    private fun unbindService() {
        if (bound) {
            appContext.unbindService(connection)
            bound = false
        }
        service = null
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}
