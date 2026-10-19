package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.OutcomeResolutionSection
import com.pmfby.evidence.app.ui.components.PmfbyTopBar
import com.pmfby.evidence.app.ui.theme.AmberWarning
import com.pmfby.evidence.app.ui.theme.AmberWarningContainer
import com.pmfby.evidence.app.ui.theme.GreenSuccess
import com.pmfby.evidence.app.ui.theme.GreenSuccessContainer
import com.pmfby.evidence.app.ui.theme.RedEmergency
import com.pmfby.evidence.app.ui.theme.RedEmergencyContainer
import com.pmfby.evidence.core.status.CaseStatusInput
import com.pmfby.evidence.core.status.CaseStatusMapper
import com.pmfby.evidence.core.status.CaseStatusTab

@Composable
fun CaseDetailScreen(
    caseId: String,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onEvidenceHistory: (String) -> Unit,
    onEmergency: (String) -> Unit,
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

            val tab = state.case?.let {
                CaseStatusMapper.tabFor(CaseStatusInput(it.contactStatus, it.attemptCount, it.forcefulSurveyFlag, it.lastCallOutcome, it.lastMessageStatus))
            } ?: CaseStatusTab.ASSIGNED

            // Status card — urgency badge + attempt count. Docket Number / Application
            // Number / Crop / Khasra-Plot fields from the reference design are NOT shown
            // here: the local Room cache (SurveyCaseEntity/FarmerEntity) does not currently
            // store them, and this screen only ever renders real cached data — see
            // LIMITATIONS.md "Case Detail card redesign — real fields only".
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusBadge(tab)
                    Text(
                        "Attempts so far: ${state.case?.attemptCount ?: 0}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // Farmer contact card — name/mobile + a prominent pill Call button, matching
            // the reference design's farmer-info card layout.
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(farmer?.farmerName ?: "Farmer", style = MaterialTheme.typography.titleLarge)
                            Text(
                                farmer?.farmerMobile ?: "No mobile on file",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = { onCall(caseId) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Call")
                        }
                    }
                    if (!farmer?.villageName.isNullOrBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Text("VILLAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(farmer?.villageName.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }

            // Primary actions — Call / Send SMS / Emergency all directly reachable from
            // this screen, no extra navigation (plus Evidence history as a lower-emphasis
            // row below).
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onCall(caseId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Filled.Call, contentDescription = null); Text(" Call", modifier = Modifier.padding(start = 4.dp)) }
                Button(
                    onClick = { onSms(caseId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Filled.Sms, contentDescription = null); Text(" SMS", modifier = Modifier.padding(start = 4.dp)) }
                Button(
                    onClick = { onEmergency(caseId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RedEmergency),
                ) { Icon(Icons.Filled.Warning, contentDescription = null); Text(" SOS", modifier = Modifier.padding(start = 4.dp)) }
            }
            ActionRow("Evidence history", Icons.Filled.History) { onEvidenceHistory(caseId) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val isClosed = state.case?.contactStatus == "completed"
            if (!isClosed) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    OutcomeResolutionSection(enabled = !state.isSubmitting) { outcome, reason, farmerAgreedToPostpone, postponedUntil ->
                        viewModel.resolveOutcome(outcome, reason, farmerAgreedToPostpone, postponedUntil)
                    }
                }
                Button(
                    onClick = { showCompleteDialog = true },
                    enabled = !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
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

/** Color-coded urgency/status badge — reuses the existing CaseStatusTab concept (the same
 * one driving the farmer-list tabs) as the priority indicator the redesign reference uses. */
@Composable
private fun StatusBadge(tab: CaseStatusTab) {
    val (bg, fg, label) = when (tab) {
        CaseStatusTab.EMERGENCY -> Triple(RedEmergencyContainer, RedEmergency, "EMERGENCY")
        CaseStatusTab.ESCALATED -> Triple(RedEmergencyContainer, RedEmergency, "ESCALATED")
        CaseStatusTab.CALL_NOT_ANSWERED -> Triple(AmberWarningContainer, AmberWarning, "CALL NOT ANSWERED")
        CaseStatusTab.PENDING -> Triple(AmberWarningContainer, AmberWarning, "PENDING")
        CaseStatusTab.SMS_SENT -> Triple(AmberWarningContainer, AmberWarning, "SMS SENT")
        CaseStatusTab.CONTACTED -> Triple(GreenSuccessContainer, GreenSuccess, "CONTACTED")
        CaseStatusTab.SURVEY_COMPLETED -> Triple(GreenSuccessContainer, GreenSuccess, "COMPLETED")
        CaseStatusTab.ASSIGNED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "ASSIGNED")
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

@Composable
private fun ActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Icon(icon, contentDescription = null)
        Text(" $label", modifier = Modifier.padding(start = 8.dp))
    }
}
