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

@Composable
fun EvidenceHistoryScreen(caseId: String, onBack: () -> Unit) {
    val viewModel = appViewModel { app -> EvidenceHistoryViewModel(app.apiService, app.sessionManager, caseId) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { PmfbyTopBar(title = "Evidence history", onBack = onBack) }) { padding ->
        if (state.isLoading) {
            FullScreenLoading()
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ErrorBanner(state.error)
            if (state.rows.isEmpty()) {
                Text("No evidence uploaded for this case yet.", modifier = Modifier.padding(16.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.rows, key = { it.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(row.kind.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
                            Text("Status: ${row.uploadStatus}", style = MaterialTheme.typography.bodyMedium)
                            row.createdAt?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}
