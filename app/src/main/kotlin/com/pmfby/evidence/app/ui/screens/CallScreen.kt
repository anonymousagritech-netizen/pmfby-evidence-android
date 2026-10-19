package com.pmfby.evidence.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.PmfbyEvidenceApp
import com.pmfby.evidence.app.telephony.CallLauncher
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.OutcomeResolutionSection
import com.pmfby.evidence.app.ui.components.PmfbyTopBar

@Composable
fun CallScreen(caseId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { app -> CallViewModel(app, app.farmerRepository, app.caseActionsRepository, app.locationProvider, app.sessionManager, app.fileUploadApi, caseId) }
    val state by viewModel.uiState.collectAsState()

    val recordingPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadCallRecording(uri)
    }

    LaunchedEffect(state.outcomeSubmitted) {
        if (state.outcomeSubmitted) onDone()
    }

    Scaffold(topBar = { PmfbyTopBar(title = "Call ${state.farmer?.farmerName ?: ""}", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            ErrorBanner(state.error)

            Text("Phone: ${state.farmer?.farmerMobile ?: "Not recorded"}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Tapping Call opens your phone's own dialer (ACTION_DIAL) — no CALL_PHONE " +
                    "permission is used by default, and you must still press the call button " +
                    "yourself. See README for the ACTION_DIAL vs ACTION_CALL tradeoff.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (state.callEventId == null) {
                Button(
                    onClick = {
                        viewModel.startCallAttempt()
                        val phone = state.farmer?.farmerMobile
                        if (!phone.isNullOrBlank()) {
                            val app = context.applicationContext as PmfbyEvidenceApp
                            if (app.sessionManager.preferInstantCall() && CallLauncher.hasCallPermission(context)) {
                                CallLauncher.callDirectly(context, phone)
                            } else {
                                CallLauncher.dial(context, phone)
                            }
                        }
                    },
                    enabled = !state.isDialing && !state.farmer?.farmerMobile.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.isDialing) "Recording attempt…" else "Call farmer") }
            } else {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Attempt recorded. Who did you actually speak to?", style = MaterialTheme.typography.titleMedium)
                        PersonContactedType.entries.forEach { type ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = state.personContactedType == type) { viewModel.onPersonContactedTypeChange(type) },
                            ) {
                                RadioButton(selected = state.personContactedType == type, onClick = { viewModel.onPersonContactedTypeChange(type) })
                                Text(type.name.lowercase().replace('_', ' '))
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

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Call recording", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Record the call with your phone's own call recorder, then attach the file here " +
                                "(Android does not allow apps to record calls themselves).",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.recordingConsent, onCheckedChange = viewModel::onRecordingConsentChange)
                            Text("The person on the call was informed the call was recorded")
                        }
                        OutlinedButton(
                            onClick = { recordingPicker.launch("audio/*") },
                            enabled = state.recordingConsent && !state.isUploadingRecording,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    state.isUploadingRecording -> "Uploading…"
                                    state.recordingUploaded -> "Recording uploaded ✓ — attach another"
                                    else -> "Attach call recording"
                                },
                            )
                        }
                    }
                }

                Text("How did the call go?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                RawCallOutcome.entries.forEach { outcome ->
                    OutlinedButton(
                        onClick = { viewModel.submitCallOutcome(outcome) },
                        enabled = !state.isSubmittingOutcome,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) { Text(outcome.name.lowercase().replace('_', ' ')) }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                OutcomeResolutionSection(enabled = !state.isSubmittingOutcome) { outcome, reason, farmerAgreedToPostpone, postponedUntil ->
                    viewModel.resolveCaseOutcome(outcome, reason, farmerAgreedToPostpone, postponedUntil)
                }
            }
        }
    }
}
