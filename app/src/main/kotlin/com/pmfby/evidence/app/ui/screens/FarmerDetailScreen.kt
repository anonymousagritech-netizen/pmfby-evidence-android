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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.pmfby.evidence.app.ui.components.FullScreenLoading
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

/** Returns background and text color for each status tab */
private fun statusChipColors(tab: CaseStatusTab): Pair<Color, Color> = when (tab) {
    CaseStatusTab.EMERGENCY        -> Pair(RedEmergencyContainer, RedEmergency)
    CaseStatusTab.ESCALATED        -> Pair(RedEmergencyContainer, RedEmergency)
    CaseStatusTab.CALL_NOT_ANSWERED -> Pair(AmberWarningContainer, AmberWarning)
    CaseStatusTab.PENDING          -> Pair(AmberWarningContainer, AmberWarning)
    CaseStatusTab.SMS_SENT         -> Pair(Color(0xFFF5F3FF), Color(0xFF7C3AED))
    CaseStatusTab.CONTACTED        -> Pair(GreenSuccessContainer, GreenSuccess)
    CaseStatusTab.SURVEY_COMPLETED -> Pair(GreenSuccessContainer, GreenSuccess)
    CaseStatusTab.ASSIGNED         -> Pair(Color(0xFFEEF3F9), Color(0xFF1E3A5F))
}

private fun statusLabel(tab: CaseStatusTab): String = when (tab) {
    CaseStatusTab.EMERGENCY        -> "🚨 EMERGENCY"
    CaseStatusTab.ESCALATED        -> "⚠️ ESCALATED"
    CaseStatusTab.CALL_NOT_ANSWERED -> "📵 NOT ANSWERED"
    CaseStatusTab.PENDING          -> "⏳ PENDING"
    CaseStatusTab.SMS_SENT         -> "💬 SMS SENT"
    CaseStatusTab.CONTACTED        -> "✅ CONTACTED"
    CaseStatusTab.SURVEY_COMPLETED -> "🎉 COMPLETED"
    CaseStatusTab.ASSIGNED         -> "📋 ASSIGNED"
}

@Composable
fun FarmerDetailScreen(farmerId: String, onBack: () -> Unit, onCaseClick: (String) -> Unit) {
    val viewModel = appViewModel { app -> FarmerDetailViewModel(app.farmerRepository, farmerId) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { PmfbyTopBar(title = state.farmer?.farmerName ?: "Farmer", onBack = onBack) }) { padding ->
        if (state.isLoading && state.farmer == null) { FullScreenLoading(); return@Scaffold }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { ErrorBanner(state.error) }

            // ── Farmer hero card ─────────────────────────────────────────────
            item {
                state.farmer?.let { farmer ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F2040)))),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(farmer.farmerName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                    Text(farmer.farmerMobile ?: "No mobile", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                }
                            }
                            if (!farmer.villageName.isNullOrBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("📍 ", color = Color.White, fontSize = 13.sp)
                                    Text(farmer.villageName.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Cases section header ─────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F9FF))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Survey Cases", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E3A5F))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E3A5F), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text("${state.cases.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // ── Case rows ────────────────────────────────────────────────────
            items(state.cases, key = { it.id }) { case ->
                val tab = CaseStatusMapper.tabFor(
                    CaseStatusInput(case.contactStatus, case.attemptCount, case.forcefulSurveyFlag, case.lastCallOutcome, case.lastMessageStatus),
                )
                val (chipBg, chipFg) = statusChipColors(tab)

                Card(
                    onClick = { onCaseClick(case.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (case.attemptCount == 0) "New case — no attempts yet" else "Attempt #${case.attemptCount}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1E3A5F),
                            )
                            case.postponedUntil?.let {
                                Spacer(Modifier.height(2.dp))
                                Text("Postponed until: $it", fontSize = 12.sp, color = AmberWarning)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(chipBg, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(statusLabel(tab), color = chipFg, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
