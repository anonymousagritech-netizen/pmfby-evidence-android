package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.PmfbyTopBar

@Composable
fun IntegrityConcernScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val viewModel = appViewModel { app -> IntegrityConcernViewModel(app.caseActionsRepository, caseId = null, farmerId = null) }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.submitted) {
        if (state.submitted) onSubmitted()
    }

    Scaffold(topBar = { PmfbyTopBar(title = "Report integrity concern", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ErrorBanner(state.error)
            Text(
                "Confidential. Only super admin can read these reports — your manager " +
                    "cannot see this. Describe any bribery or improper inducement you " +
                    "encountered. Once submitted this cannot be edited or withdrawn.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Describe what happened") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(if (state.isSubmitting) "Submitting…" else "Submit confidentially") }
        }
    }
}
