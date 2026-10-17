package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.PmfbyTopBar

/**
 * Press-and-hold emergency audio+video capture. This is an explicit, always-foreground
 * action — there is deliberately no locked-screen or background variant (see
 * EmergencyCaptureService's class doc and LIMITATIONS.md). A visible "RECORDING" indicator
 * and live countdown are always on screen while capturing, with an unambiguous Stop/Cancel
 * control — never a silent/covert recording.
 */
@Composable
fun EmergencyScreen(caseId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { app ->
        EmergencyViewModel(context.applicationContext, app.caseActionsRepository, app.sessionManager, app.fileUploadApi, caseId)
    }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.phase) {
        if (state.phase == EmergencyPhase.STOPPED || state.phase == EmergencyPhase.CANCELLED) onDone()
    }

    Scaffold(topBar = { PmfbyTopBar(title = "Emergency", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErrorBanner(state.error)
            Text(
                "Press and hold the button below only if you feel unsafe. It records " +
                    "audio and video with GPS, capped at 2 minutes, with a visible " +
                    "notification the whole time — this is never covert.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            when (state.phase) {
                EmergencyPhase.IDLE -> {
                    // The press gesture only STARTS the recording (confirming deliberate
                    // intent, same as holding a button to confirm a destructive action) —
                    // stopping is an explicit "Stop recording" button once RECORDING, not
                    // tied to releasing the finger. Realistically, someone facing a hostile
                    // farmer cannot keep a finger pressed on the exact spot for up to two
                    // full minutes; a deliberate press-to-start plus an unambiguous
                    // stop/cancel control is the honest, usable version of this gesture.
                    PressHoldButton(onPress = viewModel::beginCapture, onRelease = {})
                }
                EmergencyPhase.STARTING -> {
                    Text("Starting…", style = MaterialTheme.typography.titleMedium)
                }
                EmergencyPhase.RECORDING -> {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("● RECORDING", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                            Text("${state.elapsedSeconds}s / ${state.elapsedSeconds + state.remainingSeconds}s")
                        }
                    }
                    Button(
                        onClick = viewModel::releaseCapture,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(top = 24.dp),
                    ) { Text("Stop recording") }
                    OutlinedButton(onClick = viewModel::cancelCapture, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Cancel (discard, nothing uploaded)")
                    }
                }
                EmergencyPhase.UPLOADING -> {
                    Text("Saving and uploading…", style = MaterialTheme.typography.titleMedium)
                }
                EmergencyPhase.STOPPED -> {
                    Text(
                        if (state.hardCapped) "Recording hit the 2-minute cap and stopped." else "Recording saved.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                EmergencyPhase.CANCELLED -> {
                    Text("Recording cancelled and discarded.", style = MaterialTheme.typography.titleMedium)
                }
                EmergencyPhase.ERROR -> {
                    Text("Something went wrong.", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PressHoldButton(onPress: () -> Unit, onRelease: () -> Unit) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("HOLD", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.titleLarge)
    }
}
