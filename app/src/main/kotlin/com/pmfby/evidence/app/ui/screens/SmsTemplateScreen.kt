package com.pmfby.evidence.app.ui.screens

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
import androidx.compose.runtime.LaunchedEffect
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
        SmsTemplateViewModel(app.apiService, app.sessionManager, app.farmerRepository, app.caseActionsRepository, caseId)
    }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.composerOpened) {
        if (state.composerOpened) onDone()
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

            if (state.renderedText.isNotBlank()) {
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
                    onClick = {
                        val opened = SmsComposer.openComposer(context, state.farmer?.farmerMobile, state.renderedText)
                        viewModel.markComposerResult(opened)
                    },
                    enabled = !state.isSending,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Open SMS composer") }
                Text(
                    "This only opens your phone's own SMS app with the text pre-filled — you " +
                        "must press send yourself. The app has no way to confirm delivery.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
