package com.pmfby.evidence.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.telephony.SmsComposer
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.FullScreenLoading
import com.pmfby.evidence.app.ui.components.PmfbyTopBar

@Composable
fun SmsTemplateScreen(caseId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { app ->
        SmsTemplateViewModel(app, app.fileUploadApi, app.apiService, app.sessionManager, app.farmerRepository, app.caseActionsRepository, caseId)
    }
    val state by viewModel.uiState.collectAsState()

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadScreenshot(uri)
    }

    val requestSendSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.sendDirectly()
    }

    Scaffold(topBar = { PmfbyTopBar(title = "Send SMS", onBack = onBack) }) { padding ->
        if (state.isLoading) {
            FullScreenLoading()
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            ErrorBanner(state.error)
            Text("To: ${state.farmer?.farmerMobile ?: "Not recorded"}", style = MaterialTheme.typography.bodyLarge)

            Text("Choose a template", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            if (state.templates.isEmpty()) {
                Text("No SMS-eligible templates available.", style = MaterialTheme.typography.bodyMedium)
            }
            state.templates.forEach { option ->
                OutlinedButton(
                    onClick = { viewModel.selectTemplate(option.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(option.label) }
            }

            if (state.selectedTemplateCode == "FORMAT_2_POSTPONED" && !state.composerOpened && state.directSendStatus == null) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Postponed — required details", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This template needs the new date and a reason before it can be sent " +
                                "(same facts recorded by \"Resolve this attempt\" → Postponed).",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.postponedSurveyDate,
                            onValueChange = viewModel::onPostponedSurveyDateChange,
                            label = { Text("New survey date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.postponeReason,
                            onValueChange = viewModel::onPostponeReasonChange,
                            label = { Text("Reason for postponement") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }

            if (state.directSendStatus != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Direct send status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (state.directSendStatus) {
                                "sending" -> "Sending…"
                                "sent" -> "Sent ✓ (carrier accepted the message). Waiting for delivery confirmation…"
                                "send_failed" -> "Send failed — the carrier/radio rejected the message. Try the SMS composer fallback below."
                                "delivered" -> "Delivered ✓ — confirmed by the carrier."
                                "delivery_failed" -> "Sent, but delivery failed (e.g. handset unreachable)."
                                else -> state.directSendStatus.orEmpty()
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.directSendStatus in setOf("send_failed", "delivery_failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Done") }
                    }
                }
            } else if (state.composerOpened) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("SMS app opened", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Press send in your SMS app, take a screenshot of the sent message, then " +
                                "come back here and attach it as evidence.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        OutlinedButton(
                            onClick = { screenshotPicker.launch("image/*") },
                            enabled = !state.isUploadingScreenshot,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    state.isUploadingScreenshot -> "Uploading…"
                                    state.screenshotUploaded -> "Screenshot uploaded ✓ — attach another"
                                    else -> "Attach SMS screenshot"
                                },
                            )
                        }
                        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Done") }
                    }
                }
            } else if (state.renderedText.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(state.renderedText, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
                if (state.missingFields.isNotEmpty()) {
                    Text(
                        "Missing: ${state.missingFields.joinToString()}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = { requestSendSms.launch(android.Manifest.permission.SEND_SMS) },
                    enabled = !state.isSending && !viewModel.isPostponeDataMissing(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Send SMS now") }
                Text(
                    "Sends directly and reports real carrier SENT/DELIVERED status — no screenshot needed.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = {
                        val opened = SmsComposer.openComposer(context, state.farmer?.farmerMobile, state.renderedText)
                        viewModel.markComposerResult(opened)
                    },
                    enabled = !state.isSending && !viewModel.isPostponeDataMissing(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Fallback: open SMS composer") }
                Text(
                    "Use this if direct send is denied or fails — opens your phone's own SMS app; " +
                        "you must press send yourself, and must attach a screenshot afterward.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
