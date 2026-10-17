package com.pmfby.evidence.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.PmfbyTopBar

@Composable
fun ProfileScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val viewModel = appViewModel { app -> ProfileViewModel(app.authRepository, app.sessionManager) }
    val state by viewModel.uiState.collectAsState()

    val requestCallPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setPreferInstantCall(granted)
    }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    Scaffold(topBar = { PmfbyTopBar(title = "Profile", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(state.name, style = MaterialTheme.typography.titleLarge)
            Text(state.roleKey, style = MaterialTheme.typography.bodyMedium)
            state.scopeStateId?.let { Text("State scope: $it", style = MaterialTheme.typography.bodyMedium) }
            state.scopeDistrictId?.let { Text("District scope: $it", style = MaterialTheme.typography.bodyMedium) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Instant call (ACTION_CALL)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Off by default: calling opens the dialer and you press Call " +
                            "yourself (ACTION_DIAL, no permission needed). Turning this on " +
                            "requests CALL_PHONE and places calls immediately with no extra " +
                            "tap — see README for the tradeoff.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.preferInstantCall,
                    onCheckedChange = { enabled ->
                        if (enabled) requestCallPermission.launch(Manifest.permission.CALL_PHONE) else viewModel.setPreferInstantCall(false)
                    },
                )
            }

            Button(
                onClick = viewModel::logout,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            ) { Text("Sign out") }
        }
    }
}
