package com.pmfby.evidence.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.PmfbyTopBar

@Composable
fun UploadQueueScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { app -> UploadQueueViewModel(context.applicationContext, app.database.offlineEventDao()) }
    val queue by viewModel.queue.collectAsState()

    Scaffold(topBar = { PmfbyTopBar(title = "Upload queue", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Button(onClick = viewModel::retryNow, modifier = Modifier.padding(end = 8.dp)) { Text("Retry now") }
                OutlinedButton(onClick = viewModel::clearUploaded) { Text("Clear uploaded") }
            }
            if (queue.isEmpty()) {
                Text("Nothing queued — everything is synced.", modifier = Modifier.padding(16.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(queue, key = { it.idempotencyKey }) { event ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(event.kind.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
                            val statusColor = when (event.status) {
                                "uploaded" -> MaterialTheme.colorScheme.primary
                                "failed", "failed_permanent" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val statusLabel = if (event.status == "failed_permanent") "failed (will not auto-retry)" else event.status
                            Text("Status: $statusLabel (attempts: ${event.attempts})", color = statusColor)
                            event.lastError?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}
