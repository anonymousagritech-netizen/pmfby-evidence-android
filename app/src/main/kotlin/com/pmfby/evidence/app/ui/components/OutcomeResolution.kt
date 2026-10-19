package com.pmfby.evidence.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmfby.evidence.app.ui.screens.CaseResolutionOutcome

/**
 * The mandatory 4-outcome resolution loop (task spec: "exactly one required for any
 * non-completed contact"). [onResolve] is called with the chosen outcome, an optional
 * free-text reason, and — only for POSTPONED — whether the farmer actually AGREED to the
 * new date (a case can be marked postponed procedurally without true agreement — captured
 * as its own fact) and the new target date as an ISO-8601 string (yyyy-MM-dd), required only
 * when agreed=true (cases.js: "postponedUntil is required when outcome=postponed and
 * farmerAgreedToPostpone=true"). "Survey Completed" is NOT one of these four — it is a
 * separate, always-fully-closing action (see CaseDetailScreen's own button), kept out of
 * this loop on purpose so it can never be reached by muscle-memory tapping through these four.
 */
@Composable
fun OutcomeResolutionSection(
    enabled: Boolean,
    onResolve: (CaseResolutionOutcome, reason: String?, farmerAgreedToPostpone: Boolean?, postponedUntilIso: String?) -> Unit,
) {
    var pendingOutcome by remember { mutableStateOf<CaseResolutionOutcome?>(null) }
    var reasonText by remember { mutableStateOf("") }
    var postponedDate by remember { mutableStateOf("") }
    var farmerAgreed by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Resolve this attempt", style = MaterialTheme.typography.titleMedium)
        Text(
            "Exactly one outcome is required for every attempt that did not result in a completed survey.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        OutcomeChoiceButton("Denied", enabled) { pendingOutcome = CaseResolutionOutcome.DENIED }
        OutcomeChoiceButton("Postponed", enabled) { pendingOutcome = CaseResolutionOutcome.POSTPONED }
        OutcomeChoiceButton("Wrong number / not connected", enabled) { pendingOutcome = CaseResolutionOutcome.WRONG_NUMBER }
        OutcomeChoiceButton("Dispute / incident", enabled) { pendingOutcome = CaseResolutionOutcome.DISPUTE_INCIDENT }
    }

    val outcome = pendingOutcome
    if (outcome != null) {
        AlertDialog(
            onDismissRequest = { pendingOutcome = null },
            title = { Text("Confirm: ${outcome.name.lowercase().replace('_', ' ')}") },
            text = {
                Column {
                    if (outcome == CaseResolutionOutcome.POSTPONED) {
                        Text("Did the farmer agree to this new date?", style = MaterialTheme.typography.bodyMedium)
                        Row {
                            Row {
                                RadioButton(selected = farmerAgreed == true, onClick = { farmerAgreed = true })
                                Text("Yes", modifier = Modifier.padding(top = 12.dp, end = 16.dp))
                            }
                            Row {
                                RadioButton(selected = farmerAgreed == false, onClick = { farmerAgreed = false })
                                Text("No", modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        if (farmerAgreed == true) {
                            OutlinedTextField(
                                value = postponedDate,
                                onValueChange = { postponedDate = it },
                                label = { Text("New target date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Reason / notes (optional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = outcome != CaseResolutionOutcome.POSTPONED ||
                        (farmerAgreed == false || (farmerAgreed == true && postponedDate.isNotBlank())),
                    onClick = {
                        onResolve(
                            outcome,
                            reasonText.ifBlank { null },
                            if (outcome == CaseResolutionOutcome.POSTPONED) farmerAgreed else null,
                            if (outcome == CaseResolutionOutcome.POSTPONED && farmerAgreed == true) postponedDate else null,
                        )
                        pendingOutcome = null
                        reasonText = ""
                        postponedDate = ""
                        farmerAgreed = null
                    },
                ) { Text("Confirm") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingOutcome = null; farmerAgreed = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OutcomeChoiceButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(label)
    }
}
