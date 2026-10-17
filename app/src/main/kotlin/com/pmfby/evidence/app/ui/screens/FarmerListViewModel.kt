package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.repository.FarmerRepository
import com.pmfby.evidence.core.status.CaseStatusInput
import com.pmfby.evidence.core.status.CaseStatusMapper
import com.pmfby.evidence.core.status.CaseStatusTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FarmerListItem(val farmer: FarmerEntity, val dominantTab: CaseStatusTab, val caseCount: Int)

data class FarmerListUiState(
    val items: List<FarmerListItem> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: CaseStatusTab? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/** Farmer list dominates its displayed status tab by the most urgent of its own cases. */
private val TAB_URGENCY_ORDER = listOf(
    CaseStatusTab.EMERGENCY,
    CaseStatusTab.ESCALATED,
    CaseStatusTab.CALL_NOT_ANSWERED,
    CaseStatusTab.SMS_SENT,
    CaseStatusTab.CONTACTED,
    CaseStatusTab.PENDING,
    CaseStatusTab.ASSIGNED,
    CaseStatusTab.SURVEY_COMPLETED,
)

class FarmerListViewModel(private val farmerRepository: FarmerRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedTab = MutableStateFlow<CaseStatusTab?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val filters = combine(query, selectedTab) { q, tab -> q to tab }
    private val status = combine(refreshing, error) { isRefreshing, err -> isRefreshing to err }

    val uiState: StateFlow<FarmerListUiState> = combine(
        farmerRepository.observeCachedFarmers(),
        farmerRepository.observeAllCases(),
        filters,
        status,
    ) { farmers, cases, filterPair, statusPair ->
        val (q, tab) = filterPair
        val (isRefreshing, err) = statusPair

        val casesByFarmer = cases.groupBy { it.farmerId }
        val items = farmers
            .filter {
                q.isBlank() ||
                    it.farmerName.contains(q, ignoreCase = true) ||
                    (it.villageName?.contains(q, ignoreCase = true) == true)
            }
            .map { farmer ->
                val farmerCases = casesByFarmer[farmer.id].orEmpty()
                val tabs = if (farmerCases.isEmpty()) {
                    listOf(CaseStatusTab.ASSIGNED)
                } else {
                    farmerCases.map {
                        CaseStatusMapper.tabFor(
                            CaseStatusInput(it.contactStatus, it.attemptCount, it.forcefulSurveyFlag, it.lastCallOutcome, it.lastMessageStatus),
                        )
                    }
                }
                FarmerListItem(farmer, TAB_URGENCY_ORDER.firstOrNull { it in tabs } ?: CaseStatusTab.ASSIGNED, farmerCases.size)
            }
            .filter { tab == null || it.dominantTab == tab }
        FarmerListUiState(items, q, tab, isRefreshing, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FarmerListUiState())

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onTabSelected(tab: CaseStatusTab?) {
        selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val result = farmerRepository.refreshFarmers(query.value.ifBlank { null })
            if (result.isFailure) {
                error.value = result.exceptionOrNull()?.message ?: "Could not refresh farmers"
            } else {
                error.value = null
                farmerRepository.refreshAllCachedFarmerDetails()
            }
            refreshing.value = false
        }
    }
}
