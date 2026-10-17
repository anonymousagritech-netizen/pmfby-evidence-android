package com.pmfby.evidence.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pmfby.evidence.app.BuildConfig
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.PmfbyTopBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscrepancyFlagScreen(caseId: String, onBack: () -> Unit, onSubmitted: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { app -> DiscrepancyFlagViewModel(app.sessionManager, app.fileUploadApi, app.caseActionsRepository, caseId) }
    val state by viewModel.uiState.collectAsState()
    var reasonMenuExpanded by remember { mutableStateOf(false) }
    var pendingPhotoFile by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingPhotoFile?.let { viewModel.onPhotoCaptured(File(it)) }
        }
    }

    LaunchedEffect(state.submitted) {
        if (state.submitted) onSubmitted()
    }

    Scaffold(topBar = { PmfbyTopBar(title = "Flag suspected discrepancy", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ErrorBanner(state.error)

            ExposedDropdownMenuBox(expanded = reasonMenuExpanded, onExpandedChange = { reasonMenuExpanded = it }) {
                OutlinedTextField(
                    value = state.reason.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Reason") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                DropdownMenu(expanded = reasonMenuExpanded, onDismissRequest = { reasonMenuExpanded = false }) {
                    DiscrepancyReason.entries.forEach { reason ->
                        DropdownMenuItem(
                            text = { Text(reason.label) },
                            onClick = {
                                viewModel.onReasonChange(reason)
                                reasonMenuExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description (required)") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            OutlinedButton(
                onClick = {
                    val file = File(context.cacheDir.resolve("evidence").apply { mkdirs() }, "discrepancy_${caseId}_${System.currentTimeMillis()}.jpg")
                    pendingPhotoFile = file.absolutePath
                    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
                    takePictureLauncher.launch(uri)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(if (state.photoFile != null) "Retake photo (required)" else "Take photo (required)") }

            if (state.photoFile != null) {
                Text("Photo captured: ${state.photoFile?.name}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }

            Button(
                onClick = viewModel::submit,
                enabled = viewModel.canSubmit,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) { Text(if (state.isSubmitting) "Submitting…" else "Submit discrepancy flag") }
        }
    }
}
