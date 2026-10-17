package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.FullScreenLoading
import com.pmfby.evidence.app.ui.components.PmfbyTopBar
import com.pmfby.evidence.core.status.CaseStatusInput
import com.pmfby.evidence.core.status.CaseStatusMapper

@Composable
fun FarmerDetailScreen(farmerId: String, onBack: () -> Unit, onCaseClick: (String) -> Unit) {
    val viewModel = appViewModel { app -> FarmerDetailViewModel(app.farmerRepository, farmerId) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { PmfbyTopBar(title = state.farmer?.farmerName ?: "Farmer", onBack = onBack) }) { padding ->
        if (state.isLoading && state.farmer == null) {
            FullScreenLoading()
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ErrorBanner(state.error)
            state.farmer?.let { farmer ->
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Village: ${farmer.villageName ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                        Text("Mobile: ${farmer.farmerMobile ?: "Not recorded"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                text = "Survey cases (${state.cases.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.cases, key = { it.id }) { case ->
                    val tab = CaseStatusMapper.tabFor(
                        CaseStatusInput(case.contactStatus, case.attemptCount, case.forcefulSurveyFlag, case.lastCallOutcome, case.lastMessageStatus),
                    )
                    Card(
                        onClick = { onCaseClick(case.id) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Attempt #${case.attemptCount}", style = MaterialTheme.typography.titleMedium)
                            Text("Status: ${tab.name.replace('_', ' ')}", style = MaterialTheme.typography.bodyMedium)
                            case.postponedUntil?.let { Text("Postponed until: $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }
}
