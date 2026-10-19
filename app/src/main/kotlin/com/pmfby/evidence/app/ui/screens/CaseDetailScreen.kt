package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.OutcomeResolutionSection
import com.pmfby.evidence.app.ui.components.PmfbyTopBar
import com.pmfby.evidence.app.ui.theme.AmberWarning
import com.pmfby.evidence.app.ui.theme.AmberWarningContainer
import com.pmfby.evidence.app.ui.theme.EmeraldComplete
import com.pmfby.evidence.app.ui.theme.GreenSuccess
import com.pmfby.evidence.app.ui.theme.GreenSuccessContainer
import com.pmfby.evidence.app.ui.theme.GradientEmeraldEnd
import com.pmfby.evidence.app.ui.theme.GradientEmeraldStart
import com.pmfby.evidence.app.ui.theme.GradientIndigoEnd
import com.pmfby.evidence.app.ui.theme.GradientIndigoStart
import com.pmfby.evidence.app.ui.theme.GradientNavyEnd
import com.pmfby.evidence.app.ui.theme.GradientNavyStart
import com.pmfby.evidence.app.ui.theme.GradientRedEnd
import com.pmfby.evidence.app.ui.theme.GradientRedStart
import com.pmfby.evidence.app.ui.theme.GradientTealEnd
import com.pmfby.evidence.app.ui.theme.GradientTealStart
import com.pmfby.evidence.app.ui.theme.GradientVioletEnd
import com.pmfby.evidence.app.ui.theme.GradientVioletStart
import com.pmfby.evidence.app.ui.theme.IndigoCall
import com.pmfby.evidence.app.ui.theme.RedEmergency
import com.pmfby.evidence.app.ui.theme.RedEmergencyContainer
import com.pmfby.evidence.app.ui.theme.TealEvidence
import com.pmfby.evidence.app.ui.theme.VioletSms
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

    val tab = state.case?.let {
        CaseStatusMapper.tabFor(CaseStatusInput(it.contactStatus, it.attemptCount, it.forcefulSurveyFlag, it.lastCallOutcome, it.lastMessageStatus))
    } ?: CaseStatusTab.ASSIGNED

    val (statusGradStart, statusGradEnd, statusBg, statusFg, statusLabel) = when (tab) {
        CaseStatusTab.EMERGENCY  -> StatusColors(GradientRedStart, GradientRedEnd, RedEmergencyContainer, RedEmergency, "🚨 EMERGENCY")
        CaseStatusTab.ESCALATED  -> StatusColors(GradientRedStart, GradientRedEnd, RedEmergencyContainer, RedEmergency, "⚠️ ESCALATED")
        CaseStatusTab.CALL_NOT_ANSWERED -> StatusColors(GradientAmberS, GradientAmberE, AmberWarningContainer, AmberWarning, "📵 NOT ANSWERED")
        CaseStatusTab.PENDING    -> StatusColors(GradientAmberS, GradientAmberE, AmberWarningContainer, AmberWarning, "⏳ PENDING")
        CaseStatusTab.SMS_SENT   -> StatusColors(GradientVioletStart, GradientVioletEnd, Color(0xFFF5F3FF), Color(0xFF7C3AED), "💬 SMS SENT")
        CaseStatusTab.CONTACTED  -> StatusColors(GradientEmeraldStart, GradientEmeraldEnd, GreenSuccessContainer, GreenSuccess, "✅ CONTACTED")
        CaseStatusTab.SURVEY_COMPLETED -> StatusColors(GradientEmeraldStart, GradientEmeraldEnd, GreenSuccessContainer, GreenSuccess, "🎉 COMPLETED")
        CaseStatusTab.ASSIGNED   -> StatusColors(GradientNavyStart, GradientNavyEnd, Color(0xFFEEF3F9), Color(0xFF1E3A5F), "📋 ASSIGNED")
    }

    Scaffold(topBar = { PmfbyTopBar(title = "Case Detail", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ErrorBanner(state.error)

            // ── Status + Farmer hero card ────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(statusGradStart, statusGradEnd))),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Status badge
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                        ) {
                            Text(statusLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        // Farmer name row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    farmer?.farmerName ?: "Loading…",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                )
                                Text(
                                    farmer?.farmerMobile ?: "No mobile on file",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                        if (!farmer?.villageName.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("📍 Village: ", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                Text(farmer?.villageName.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Attempts so far: ${state.case?.attemptCount ?: 0}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            state.lastActionMessage?.let {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = GreenSuccessContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(it, color = GreenSuccess, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Primary action buttons ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onCall(caseId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoCall),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Call", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onSms(caseId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = VioletSms),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("SMS", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onEmergency(caseId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RedEmergency),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("SOS", fontWeight = FontWeight.Bold)
                }
            }

            // Evidence history
            Card(
                onClick = { onEvidenceHistory(caseId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF3F9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TealEvidence.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = TealEvidence, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Evidence History", fontWeight = FontWeight.SemiBold, color = Color(0xFF1E3A5F), fontSize = 15.sp)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color(0xFFE4E7EB))

            // ── Outcome resolution ───────────────────────────────────────────
            val isClosed = state.case?.contactStatus == "completed"
            if (!isClosed) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF2E5080))), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text("Resolve this attempt", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text(
                            "Exactly one outcome is required for every attempt that did not result in a completed survey.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                        OutcomeResolutionSection(enabled = !state.isSubmitting) { outcome, reason, farmerAgreedToPostpone, postponedUntil ->
                            viewModel.resolveOutcome(outcome, reason, farmerAgreedToPostpone, postponedUntil)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { showCompleteDialog = true },
                    enabled = !state.isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldComplete),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                ) {
                    Text("✓  Mark Survey Completed", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(20.dp))
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GreenSuccessContainer),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("🎉 This case is closed — survey completed.", modifier = Modifier.padding(16.dp), color = GreenSuccess, fontWeight = FontWeight.SemiBold)
                }
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
                Button(
                    onClick = { viewModel.markSurveyCompleted(notes.ifBlank { null }); showCompleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldComplete),
                ) { Text("Confirm") }
            },
            dismissButton = { OutlinedButton(onClick = { showCompleteDialog = false }) { Text("Cancel") } },
        )
    }
}

// Helpers to avoid Destructuring component count limits
private data class StatusColors(
    val gradStart: Color, val gradEnd: Color,
    val bg: Color, val fg: Color, val label: String,
)
private val GradientAmberS = Color(0xFFD97706)
private val GradientAmberE = Color(0xFFB45309)
