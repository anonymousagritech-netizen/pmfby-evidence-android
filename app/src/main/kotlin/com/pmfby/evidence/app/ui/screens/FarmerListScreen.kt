package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.core.status.CaseStatusTab

private fun CaseStatusTab.displayLabel(): String = when (this) {
    CaseStatusTab.ASSIGNED -> "Assigned"
    CaseStatusTab.PENDING -> "Pending"
    CaseStatusTab.CALL_NOT_ANSWERED -> "Call not answered"
    CaseStatusTab.SMS_SENT -> "SMS sent"
    CaseStatusTab.CONTACTED -> "Contacted"
    CaseStatusTab.SURVEY_COMPLETED -> "Survey completed"
    CaseStatusTab.ESCALATED -> "Escalated"
    CaseStatusTab.EMERGENCY -> "Emergency"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerListScreen(
    onFarmerClick: (String) -> Unit,
    onOpenUploadQueue: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val viewModel = appViewModel { app -> FarmerListViewModel(app.farmerRepository) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Assigned Farmers") },
                actions = {
                    IconButton(onClick = onOpenUploadQueue) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Upload queue")
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search by name or village") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )

                val tabs = listOf<CaseStatusTab?>(null) + CaseStatusTab.entries
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    items(tabs) { tab ->
                        FilterChip(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.onTabSelected(tab) },
                            label = { Text(tab?.displayLabel() ?: "All") },
                        )
                    }
                }

                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.farmer.id }) { item ->
                        FarmerRow(item = item, onClick = { onFarmerClick(item.farmer.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmerRow(item: FarmerListItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.farmer.farmerName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(item.farmer.villageName, item.farmer.farmerMobile).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusBadge(item.dominantTab)
        }
    }
}

@Composable
private fun StatusBadge(tab: CaseStatusTab) {
    val (container, content) = when (tab) {
        CaseStatusTab.EMERGENCY -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CaseStatusTab.ESCALATED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CaseStatusTab.SURVEY_COMPLETED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = tab.displayLabel(),
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .padding(top = 6.dp)
            .background(container, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
