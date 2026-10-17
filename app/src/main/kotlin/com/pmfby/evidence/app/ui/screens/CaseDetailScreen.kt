package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.OutcomeResolutionSection
import com.pmfby.evidence.app.ui.components.PmfbyTopBar
import com.pmfby.evidence.core.status.CaseStatusInput
import com.pmfby.evidence.core.status.CaseStatusMapper
import com.pmfby.evidence.core.status.CaseStatusTab

@Composable
fun CaseDetailScreen(
    caseId: String,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onVoiceMessage: (String) -> Unit,
    onEvidenceHistory: (String) -> Unit,
    onEmergency: (String) -> Unit,
    onDiscrepancy: (String) -> Unit,
    onIntegrityConcern: () -> Unit,
) {
    val viewModel = appViewModel { app -> CaseDetailViewModel(app.farmerRepository, app.caseActionsRepository, caseId) }
    val state by viewModel.uiState.collectAsState()
    val farmer by viewModel.farmer.collectAsState()
    var showCompleteDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { PmfbyTopBar(title = "Case detail", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ErrorBanner(state.error)
            state.lastActionMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
            }

            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(farmer?.farmerName ?: "Farmer", style = MaterialTheme.typography.titleLarge)
                    Text(farmer?.villageName ?: "", style = MaterialTheme.typography.bodyMedium)
                    val tab = state.case?.let {
                        CaseStatusMapper.tabFor(CaseStatusInput(it.contactStatus, it.attemptCount, it.forcefulSurveyFlag, it.lastCallOutcome, it.lastMessageStatus))
                    } ?: CaseStatusTab.ASSIGNED
                    Text("Status: ${tab.name.replace('_', ' ')}", style = MaterialTheme.typography.bodyMedium)
                    Text("Attempts so far: ${state.case?.attemptCount ?: 0}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            ActionRow("Call farmer", Icons.Filled.Call) { onCall(caseId) }
            ActionRow("Send SMS", Icons.Filled.Sms) { onSms(caseId) }
            ActionRow("Play voice message", Icons.Filled.Sms) { onVoiceMessage(caseId) }
            ActionRow("Evidence history", Icons.Filled.History) { onEvidenceHistory(caseId) }
            ActionRow("Flag suspected discrepancy", Icons.Filled.Flag) { onDiscrepancy(caseId) }
            ActionRow("Report integrity concern (confidential)", Icons.Filled.Gavel) { onIntegrityConcern() }

            Button(
                onClick = { onEmergency(caseId) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(" Emergency — press and hold", modifier = Modifier.padding(start = 8.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val isClosed = state.case?.contactStatus == "completed"
            if (!isClosed) {
                OutcomeResolutionSection(enabled = !state.isSubmitting) { outcome, reason, postponedUntil ->
                    viewModel.resolveOutcome(outcome, reason, postponedUntil)
                }
                Button(
                    onClick = { showCompleteDialog = true },
                    enabled = !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Mark Survey Completed") }
            } else {
                Text(
                    "This case is closed — survey completed.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (showCompleteDialog) {
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Confirm survey completed") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.markSurveyCompleted(notes.ifBlank { null })
                    showCompleteDialog = false
                }) { Text("Confirm") }
            },
            dismissButton = { OutlinedButton(onClick = { showCompleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Icon(icon, contentDescription = null)
        Text(" $label", modifier = Modifier.padding(start = 8.dp))
    }
}
