package com.pmfby.evidence.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pmfby.evidence.app.PmfbyEvidenceApp
import com.pmfby.evidence.app.telephony.CallLauncher
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.OutcomeResolutionSection
import com.pmfby.evidence.app.ui.components.PmfbyTopBar
import com.pmfby.evidence.app.ui.theme.GradientIndigoEnd
import com.pmfby.evidence.app.ui.theme.GradientIndigoStart
import com.pmfby.evidence.app.ui.theme.GreenSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(caseId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PmfbyEvidenceApp
    val viewModel = appViewModel { a -> CallViewModel(a, a.farmerRepository, a.caseActionsRepository, a.locationProvider, a.sessionManager, a.fileUploadApi, caseId) }
    val state by viewModel.uiState.collectAsState()

    val recordingPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadCallRecording(uri)
    }

    val requestRecordAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startAutoRecording()
    }

    // ── AUTO-DIAL: fire immediately when farmer loads — TRUE ONE-CLICK ─────────
    var autoCallTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(state.farmer) {
        val phone = state.farmer?.farmerMobile
        if (phone != null && !autoCallTriggered && state.callEventId == null && !state.isDialing) {
            autoCallTriggered = true
            viewModel.startCallAttempt()
            requestRecordAudio.launch(android.Manifest.permission.RECORD_AUDIO)
            if (app.sessionManager.preferInstantCall() && CallLauncher.hasCallPermission(context)) {
                CallLauncher.callDirectly(context, phone)
            } else {
                CallLauncher.dial(context, phone)
            }
        }
    }

    // ── CallStateObserver: auto-detect call end → pop outcome sheet ───────────
    val observerExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        viewModel.startCallStateObserver(observerExecutor)
        onDispose { viewModel.stopCallStateObserver() }
    }

    var showOutcomeSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(state.callEndedByObserver) {
        if (state.callEndedByObserver) showOutcomeSheet = true
    }

    LaunchedEffect(state.outcomeSubmitted) {
        if (state.outcomeSubmitted) {
            showOutcomeSheet = false
            viewModel.stopAndUploadAutoRecording()
            onDone()
        }
    }

    // Auto outcome bottom sheet — pops immediately when call ends (observer detected IDLE)
    if (showOutcomeSheet) {
        ModalBottomSheet(onDismissRequest = { showOutcomeSheet = false }, sheetState = sheetState) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("Call ended — set the outcome", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 4.dp))
                Text("Recording stopped and saved.", fontSize = 13.sp, color = GreenSuccess, modifier = Modifier.padding(bottom = 16.dp))

                Text("Who did you reach?", fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                PersonContactedType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(selected = state.personContactedType == type) { viewModel.onPersonContactedTypeChange(type) },
                    ) {
                        RadioButton(selected = state.personContactedType == type, onClick = { viewModel.onPersonContactedTypeChange(type) })
                        Text(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("How did it go?", fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                RawCallOutcome.entries.forEach { outcome ->
                    OutlinedButton(
                        onClick = { viewModel.submitCallOutcome(outcome) },
                        enabled = !state.isSubmittingOutcome,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text(outcome.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Resolve this attempt", fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                OutcomeResolutionSection(enabled = !state.isSubmittingOutcome) { outcome, reason, agreed, until ->
                    viewModel.resolveCaseOutcome(outcome, reason, agreed, until)
                }
            }
        }
    }

    Scaffold(topBar = { PmfbyTopBar(title = "📞  ${state.farmer?.farmerName ?: "Calling…"}", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ErrorBanner(state.error)

            // ── Calling hero card ──────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(GradientIndigoStart, GradientIndigoEnd))),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.autoRecordSource != null && state.autoRecordSource != "") Icons.Filled.MicNone else Icons.Filled.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.farmer?.farmerName ?: "Loading…",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                        )
                        Text(
                            state.farmer?.farmerMobile ?: "",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(8.dp))

                        // Recording status badge
                        when {
                            state.autoRecordSource != null && state.autoRecordSource != "" -> {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF22C55E).copy(alpha = 0.25f), RoundedCornerShape(50))
                                        .padding(horizontal = 14.dp, vertical = 5.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.MicNone, contentDescription = null, tint = Color(0xFF86EFAC), modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Recording (${state.autoRecordSource})", color = Color(0xFF86EFAC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            state.isDialing -> {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.height(4.dp))
                                Text("Connecting…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                            else -> {
                                Text("Return here after the call to record the outcome", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ── If auto-dial hasn't fired yet (no farmer), manual dial button ──
            if (!autoCallTriggered && state.farmer?.farmerMobile != null) {
                Button(
                    onClick = {
                        autoCallTriggered = true
                        viewModel.startCallAttempt()
                        requestRecordAudio.launch(android.Manifest.permission.RECORD_AUDIO)
                        val phone = state.farmer?.farmerMobile
                        if (!phone.isNullOrBlank()) {
                            if (app.sessionManager.preferInstantCall() && CallLauncher.hasCallPermission(context)) {
                                CallLauncher.callDirectly(context, phone)
                            } else {
                                CallLauncher.dial(context, phone)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Dial Now", fontWeight = FontWeight.Bold)
                }
            }

            // ── After call: outcome section ────────────────────────────────────
            if (state.callEventId != null || autoCallTriggered) {
                Spacer(Modifier.height(8.dp))

                // Who was contacted
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Who did you speak to?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        PersonContactedType.entries.forEach { type ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = state.personContactedType == type) { viewModel.onPersonContactedTypeChange(type) },
                            ) {
                                RadioButton(selected = state.personContactedType == type, onClick = { viewModel.onPersonContactedTypeChange(type) })
                                Text(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                            }
                        }
                        if (state.personContactedType == PersonContactedType.FAMILY_MEMBER || state.personContactedType == PersonContactedType.OTHER) {
                            OutlinedTextField(
                                value = state.personContactedName,
                                onValueChange = viewModel::onPersonContactedNameChange,
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            OutlinedTextField(
                                value = state.personContactedRelation,
                                onValueChange = viewModel::onPersonContactedRelationChange,
                                label = { Text("Relation to farmer") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }

                // Call result buttons
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How did the call go?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        RawCallOutcome.entries.forEach { outcome ->
                            OutlinedButton(
                                onClick = { viewModel.submitCallOutcome(outcome) },
                                enabled = !state.isSubmittingOutcome,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) { Text(outcome.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))

                // Case outcome resolution
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
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
                        OutcomeResolutionSection(enabled = !state.isSubmittingOutcome) { outcome, reason, farmerAgreedToPostpone, postponedUntil ->
                            viewModel.resolveCaseOutcome(outcome, reason, farmerAgreedToPostpone, postponedUntil)
                        }
                    }
                }

                // Manual recording attachment (fallback)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(1.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.StopCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Attach call recording (manual fallback)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                            state.autoRecordSource != null && state.autoRecordSource != "" ->
                                Text("Auto-recording running — will upload on submit.", style = MaterialTheme.typography.labelMedium, color = GreenSuccess, modifier = Modifier.padding(vertical = 4.dp))
                            state.autoRecordAttempted ->
                                Text(
                                    "Auto-recording blocked by OS/OEM. Use your phone's call recorder and attach below.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Checkbox(checked = state.recordingConsent, onCheckedChange = viewModel::onRecordingConsentChange)
                            Text("The person was informed the call was recorded", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { recordingPicker.launch("audio/*") },
                            enabled = state.recordingConsent && !state.isUploadingRecording,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    state.isUploadingRecording -> "Uploading…"
                                    state.recordingUploaded -> "Uploaded ✓ — attach another"
                                    else -> "📎 Attach recording"
                                },
                            )
                        }
                        if (state.autoRecordAttempted && state.autoRecordSource == "") {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Open app permissions") }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
