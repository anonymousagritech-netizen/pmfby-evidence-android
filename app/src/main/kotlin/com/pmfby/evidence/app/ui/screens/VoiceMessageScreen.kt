package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun VoiceMessageScreen(caseId: String, onBack: () -> Unit) {
    val viewModel = appViewModel { app -> VoiceMessageViewModel(app.apiService, app.sessionManager, app.fileUploadApi, caseId) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { PmfbyTopBar(title = "Voice message templates", onBack = onBack) }) { padding ->
        if (state.isLoading) {
            FullScreenLoading()
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ErrorBanner(state.error)
            Text(
                "These are admin-managed prerecorded messages for local playback only — " +
                    "this app cannot inject audio into a live phone call (a platform " +
                    "limitation, see LIMITATIONS.md). Play the message to the farmer on " +
                    "speaker, or read the matching SMS template aloud instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            if (state.templates.isEmpty()) {
                Text("No templates configured.")
            }
            state.templates.forEach { template ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(template.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Audio not bundled in this build — admin audio-upload pipeline " +
                                "is not implemented yet (see ROADMAP.md).",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(" Play (unavailable)")
                        }
                        OutlinedButton(
                            onClick = { viewModel.logPlaybackSelection(template) },
                            modifier = Modifier.padding(top = 4.dp),
                        ) { Text("I played/read this to the farmer — log it") }
                        if (state.loggedTemplateId == template.id) {
                            Text("Selection logged.", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
