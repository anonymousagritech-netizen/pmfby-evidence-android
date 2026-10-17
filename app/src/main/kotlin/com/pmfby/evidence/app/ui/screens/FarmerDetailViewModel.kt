package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.local.FarmerEntity
import com.pmfby.evidence.app.data.local.SurveyCaseEntity
import com.pmfby.evidence.app.data.repository.FarmerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FarmerDetailUiState(
    val farmer: FarmerEntity? = null,
    val cases: List<SurveyCaseEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class FarmerDetailViewModel(private val farmerRepository: FarmerRepository, private val farmerId: String) : ViewModel() {

    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FarmerDetailUiState> = combine(
        farmerRepository.observeCachedFarmers(),
        farmerRepository.observeCasesForFarmer(farmerId),
        loading,
        error,
    ) { farmers, cases, isLoading, err ->
        FarmerDetailUiState(farmers.firstOrNull { it.id == farmerId }, cases, isLoading, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FarmerDetailUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            val result = farmerRepository.refreshFarmerDetail(farmerId)
            error.value = result.exceptionOrNull()?.message
            loading.value = false
        }
    }
}
