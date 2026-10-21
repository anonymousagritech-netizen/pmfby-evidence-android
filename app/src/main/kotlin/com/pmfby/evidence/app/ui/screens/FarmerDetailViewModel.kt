package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.local.SurveyCaseEntity
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.data.repository.FarmerRepository
import com.pmfby.evidence.app.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One batch = same farmer + same intimation date — one contact covers all dockets. */
data class CaseBatch(
    val intimationDate: String,    // "YYYY-MM-DD" or "unknown"
    val displayDate: String,
    val caseCount: Int,
    val docketIds: List<String>,
    /** null = no prior contact session for this batch */
    val latestSessionId: String?,
    val latestSessionOutcome: String?,
    val latestContactedAt: String?,
    val contactType: String?,       // "call", "sms", "call_and_sms"
    val smsCount: Int = 0,
    val lastSmsStatus: String? = null,
    val lastSmsText: String? = null,
)

data class FarmerDetailUiState(
    val farmer: FarmerEntity? = null,
    val cases: List<SurveyCaseEntity> = emptyList(),
    val batches: List<CaseBatch> = emptyList(),
    val isLoading: Boolean = false,
    val isCreatingSession: Boolean = false,
    val error: String? = null,
    /** Set when a Call session is created → screen dials phone then clears this */
    val pendingCallSession: Pair<String, String>? = null,  // (sessionId, farmerMobile)
    /** Set when an SMS session is created → screen navigates to template screen then clears */
    val pendingSmsSessionId: String? = null,
    /** Session awaiting outcome recording — shows the outcome bottom sheet */
    val outcomeSessionId: String? = null,
    val isSettingOutcome: Boolean = false,
)

class FarmerDetailViewModel(
    private val farmerRepository: FarmerRepository,
    private val api: ApiService,
    private val session: SessionManager,
    private val farmerId: String,
) : ViewModel() {

    private val loading = MutableStateFlow(false)
    private val creatingSession = MutableStateFlow(false)
    private val settingOutcome = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val pendingCallFlow = MutableStateFlow<Pair<String, String>?>(null)
    private val pendingSmsFlow = MutableStateFlow<String?>(null)
    private val batchesFlow = MutableStateFlow<List<CaseBatch>>(emptyList())
    private val outcomeSessionFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FarmerDetailUiState> = combine(
        farmerRepository.observeCachedFarmers(),
        farmerRepository.observeCasesForFarmer(farmerId),
        loading,
        creatingSession,
        errorFlow,
        pendingCallFlow,
        pendingSmsFlow,
        batchesFlow,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val farmers = arr[0] as List<FarmerEntity>
        @Suppress("UNCHECKED_CAST")
        val cases = arr[1] as List<SurveyCaseEntity>
        FarmerDetailUiState(
            farmer = farmers.firstOrNull { it.id == farmerId },
            cases = cases,
            batches = arr[7] as List<CaseBatch>,
            isLoading = arr[2] as Boolean,
            isCreatingSession = arr[3] as Boolean,
            error = arr[4] as String?,
            pendingCallSession = arr[5] as Pair<String, String>?,
            pendingSmsSessionId = arr[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FarmerDetailUiState())

    // Separate state for outcome sheet (can't fit in the 8-flow combine limit)
    val outcomeSessionId: StateFlow<String?> = outcomeSessionFlow
    val isSettingOutcome: StateFlow<Boolean> = settingOutcome

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            // Refresh local cache
            farmerRepository.refreshFarmerDetail(farmerId)
            // Load batches from /contact-sessions/farmer/:id (has docketIds, intimation dates)
            loadBatches()
            loading.value = false
        }
    }

    private suspend fun loadBatches() {
        val auth = session.authHeader() ?: return
        try {
            val response = api.getContactSessionsForFarmer(auth, farmerId)
            @Suppress("UNCHECKED_CAST")
            val rawBatches = response["batches"] as? List<Map<String, Any?>> ?: return
            batchesFlow.value = rawBatches.map { b ->
                val date = b["intimationDate"]?.toString() ?: "unknown"
                @Suppress("UNCHECKED_CAST")
                val docketIds = (b["docketIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val latestSession = b["latestSession"] as? Map<String, Any?>
                CaseBatch(
                    intimationDate = date,
                    displayDate = if (date == "unknown") "No Intimation Date" else date,
                    caseCount = (b["caseCount"] as? Number)?.toInt() ?: docketIds.size,
                    docketIds = docketIds,
                    latestSessionId = latestSession?.get("id")?.toString(),
                    latestSessionOutcome = latestSession?.get("outcome")?.toString(),
                    latestContactedAt = latestSession?.get("contacted_at")?.toString(),
                    contactType = latestSession?.get("contact_type")?.toString(),
                    smsCount = (latestSession?.get("sms_count") as? Number)?.toInt() ?: 0,
                    lastSmsStatus = latestSession?.get("last_sms_status")?.toString(),
                    lastSmsText = latestSession?.get("last_sms_text")?.toString(),
                )
            }
        } catch (_: Exception) {
            // Batches are best-effort — cases from Room cache still show
        }
    }

    /** Creates a "call" session for this batch, then opens the phone dialer. */
    fun callBatch(batch: CaseBatch) {
        val phone = uiState.value.farmer?.farmerMobile ?: run {
            errorFlow.value = "No mobile number on file for this farmer"
            return
        }
        viewModelScope.launch {
            val auth = session.authHeader() ?: run { errorFlow.value = "Not signed in"; return@launch }
            creatingSession.value = true
            errorFlow.value = null
            try {
                val body = buildMap<String, Any?> {
                    put("farmerId", farmerId)
                    put("contactType", "call")
                    if (batch.intimationDate != "unknown") put("intimationBatchDate", batch.intimationDate)
                }
                val response = api.createContactSession(auth, body)
                @Suppress("UNCHECKED_CAST")
                val sessionId = (response["session"] as? Map<String, Any?>)?.get("id")?.toString()
                if (sessionId != null) {
                    pendingCallFlow.value = Pair(sessionId, phone)
                    // After dialer fires, show outcome sheet so user records what happened
                    outcomeSessionFlow.value = sessionId
                    loadBatches()
                } else {
                    errorFlow.value = "Could not create call session"
                }
            } catch (e: Exception) {
                errorFlow.value = e.message ?: "Could not create session"
            } finally {
                creatingSession.value = false
            }
        }
    }

    /** Records the outcome of a call/contact session. */
    fun setCallOutcome(sessionId: String, outcome: String, surveyDate: String? = null) {
        viewModelScope.launch {
            val auth = session.authHeader() ?: run { errorFlow.value = "Not signed in"; return@launch }
            settingOutcome.value = true
            errorFlow.value = null
            try {
                val body = buildMap<String, Any?> {
                    put("outcome", outcome)
                    if (surveyDate != null) put("surveyScheduledDate", surveyDate)
                }
                api.setContactSessionOutcome(auth, sessionId, body)
                outcomeSessionFlow.value = null
                loadBatches()
            } catch (e: Exception) {
                errorFlow.value = e.message ?: "Could not record outcome"
            } finally {
                settingOutcome.value = false
            }
        }
    }

    fun showOutcomeSheet(sessionId: String) { outcomeSessionFlow.value = sessionId }
    fun clearOutcomeSheet() { outcomeSessionFlow.value = null }

    /** Creates an "sms" session for this batch, then navigates to SmsTemplateScreen. */
    fun smsBatch(batch: CaseBatch) {
        viewModelScope.launch {
            val auth = session.authHeader() ?: run { errorFlow.value = "Not signed in"; return@launch }
            creatingSession.value = true
            errorFlow.value = null
            try {
                val body = buildMap<String, Any?> {
                    put("farmerId", farmerId)
                    put("contactType", "sms")
                    if (batch.intimationDate != "unknown") put("intimationBatchDate", batch.intimationDate)
                }
                val response = api.createContactSession(auth, body)
                @Suppress("UNCHECKED_CAST")
                val sessionId = (response["session"] as? Map<String, Any?>)?.get("id")?.toString()
                if (sessionId != null) {
                    pendingSmsFlow.value = sessionId
                    loadBatches()
                } else {
                    errorFlow.value = "Could not create SMS session"
                }
            } catch (e: Exception) {
                errorFlow.value = e.message ?: "Could not create session"
            } finally {
                creatingSession.value = false
            }
        }
    }

    fun clearPendingCall() { pendingCallFlow.value = null }
    fun clearPendingSms() { pendingSmsFlow.value = null }
    fun clearError() { errorFlow.value = null }
}
