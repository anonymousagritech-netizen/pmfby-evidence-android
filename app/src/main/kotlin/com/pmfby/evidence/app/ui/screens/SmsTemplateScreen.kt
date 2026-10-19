package com.pmfby.evidence.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pmfby.evidence.app.telephony.SmsComposer
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.FullScreenLoading
import com.pmfby.evidence.app.ui.components.PmfbyTopBar
import com.pmfby.evidence.app.ui.theme.AmberWarning
import com.pmfby.evidence.app.ui.theme.AmberWarningContainer
import com.pmfby.evidence.app.ui.theme.GreenSuccess
import com.pmfby.evidence.app.ui.theme.GreenSuccessContainer
import com.pmfby.evidence.app.ui.theme.OrangePostponed
import com.pmfby.evidence.app.ui.theme.OrangePostponedContainer
import com.pmfby.evidence.app.ui.theme.PurpleWrong
import com.pmfby.evidence.app.ui.theme.PurpleWrongContainer
import com.pmfby.evidence.app.ui.theme.RedEmergency
import com.pmfby.evidence.app.ui.theme.RedEmergencyContainer
import com.pmfby.evidence.app.ui.theme.VioletSms

/** Color pair for each template code */
private fun templateColors(code: String): Pair<Color, Color> = when {
    code.contains("DENIED")      -> Pair(Color(0xFFDC2626), Color(0xFFFEE2E2))
    code.contains("POSTPONED")   -> Pair(Color(0xFFEA580C), Color(0xFFFFF7ED))
    code.contains("WRONG")       -> Pair(Color(0xFF9333EA), Color(0xFFFAF5FF))
    code.contains("INCIDENT")    -> Pair(Color(0xFF0369A1), Color(0xFFE0F2FE))
    else                         -> Pair(Color(0xFF4F46E5), Color(0xFFEEF2FF))
}

@Composable
fun SmsTemplateScreen(caseId: String?, sessionId: String?, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { app ->
        SmsTemplateViewModel(app, app.fileUploadApi, app.apiService, app.sessionManager, app.farmerRepository, app.caseActionsRepository, caseId, sessionId)
    }
    val state by viewModel.uiState.collectAsState()

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadScreenshot(uri)
    }
    val requestSendSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.sendDirectly()
    }

    // Note: on MIUI/VoWiFi devices SmsManager callbacks never fire, so send_status_unknown
    // means the SMS was likely sent at radio level but we got no carrier confirmation.
    // We DO NOT auto-open the composer here — that caused double-sends via RCS which fail.

    Scaffold(topBar = {
        PmfbyTopBar(title = "Send SMS", onBack = onBack)
    }) { padding ->
        if (state.isLoading) { FullScreenLoading(); return@Scaffold }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ErrorBanner(state.error)

            // ── Recipient header ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF4F46E5))))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Sms, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Send SMS to Farmer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            state.farmer?.farmerMobile ?: "Number not available",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // ── Template chooser ────────────────────────────────────────────
            if (state.directSendStatus == null && !state.composerOpened) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Choose a Message Template",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E3A5F),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    if (state.templates.isEmpty()) {
                        Text("No SMS templates available.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B7280))
                    }
                    state.templates.forEach { option ->
                        val (fg, bg) = templateColors(option.code)
                        Button(
                            onClick = { viewModel.selectTemplate(option.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = fg),
                        ) {
                            Text(option.label.replace("FORMAT ", "FORMAT "), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Postponed extra fields
            if (state.selectedTemplateCode == "FORMAT_2_POSTPONED" && !state.composerOpened && state.directSendStatus == null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OrangePostponedContainer),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Postponement Details", fontWeight = FontWeight.Bold, color = OrangePostponed, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = state.postponedSurveyDate,
                            onValueChange = viewModel::onPostponedSurveyDateChange,
                            label = { Text("New survey date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = state.postponeReason,
                            onValueChange = viewModel::onPostponeReasonChange,
                            label = { Text("Reason for postponement") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Rendered message preview ─────────────────────────────────────
            if (state.renderedText.isNotBlank() && state.directSendStatus == null && !state.composerOpened) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FF)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Message Preview", fontWeight = FontWeight.Bold, color = Color(0xFF1E3A5F), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(state.renderedText, fontSize = 13.sp, lineHeight = 20.sp)
                        if (state.missingFields.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Missing fields: ${state.missingFields.joinToString()}", color = RedEmergency, fontSize = 12.sp)
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    Button(
                        onClick = { requestSendSms.launch(android.Manifest.permission.SEND_SMS) },
                        enabled = !state.isSending && !viewModel.isPostponeDataMissing(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletSms),
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Sending…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send SMS Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Text(
                        "Sends directly and records carrier confirmation as evidence.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    OutlinedButton(
                        onClick = {
                            val opened = SmsComposer.openComposer(context, state.farmer?.farmerMobile, state.renderedText)
                            viewModel.markComposerResult(opened)
                        },
                        enabled = !state.isSending && !viewModel.isPostponeDataMissing(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Open SMS App Instead", fontWeight = FontWeight.SemiBold) }
                }
            }

            // ── Sending in progress ──────────────────────────────────────────
            if (state.directSendStatus == "sending") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF3F9)),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF4F46E5))
                        Spacer(Modifier.width(12.dp))
                        Text("Sending SMS… waiting up to 25s for carrier reply", color = Color(0xFF1E3A5F), fontSize = 14.sp)
                    }
                }
            }

            // ── Direct send result (sent / delivered / failed / status_unknown) ─
            val showDirectResult = state.directSendStatus != null &&
                state.directSendStatus != "sending"
            if (showDirectResult) {
                val isUnknown = state.directSendStatus == "send_status_unknown"
                val isSuccess = state.directSendStatus in setOf("sent", "delivered") || isUnknown
                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) GreenSuccessContainer else RedEmergencyContainer,
                    ),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (isSuccess) GreenSuccess else RedEmergency,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when (state.directSendStatus) {
                                    "sent"               -> "SMS Sent ✓"
                                    "delivered"          -> "SMS Delivered ✓"
                                    "send_failed"        -> "Send Failed"
                                    "delivery_failed"    -> "Delivery Failed"
                                    "send_status_unknown" -> "SMS Sent ✓"
                                    else                 -> state.directSendStatus.orEmpty()
                                },
                                fontWeight = FontWeight.Bold,
                                color = if (isSuccess) GreenSuccess else RedEmergency,
                                fontSize = 16.sp,
                            )
                        }
                        Text(
                            when (state.directSendStatus) {
                                "sent"               -> "Carrier accepted the message."
                                "delivered"          -> "Confirmed delivered by the carrier."
                                "send_failed"        -> "The carrier/radio rejected the message."
                                "send_status_unknown" -> "SMS sent. Carrier confirmation unavailable on this device — take a screenshot of your SMS app as additional evidence."
                                else -> state.directSendResultDetail ?: ""
                            },
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = 13.sp,
                            color = if (isSuccess) GreenSuccess else RedEmergency,
                        )
                        Spacer(Modifier.height(10.dp))
                        // Step 1: open the SMS app so user can take a screenshot
                        Button(
                            onClick = {
                                val phone = state.farmer?.farmerMobile ?: ""
                                val digits = phone.filter { it.isDigit() }
                                val smsUri = android.net.Uri.parse("smsto:$digits")
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, smsUri))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("📱 Open SMS App → Take Screenshot", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        // Step 2: come back and upload the screenshot
                        if (state.screenshotUploaded) {
                            Text("✓ Screenshot saved as evidence", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GreenSuccess, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        OutlinedButton(
                            onClick = { screenshotPicker.launch("image/*") },
                            enabled = !state.isUploadingScreenshot,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (state.isUploadingScreenshot) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Uploading…")
                            } else {
                                Text(if (state.screenshotUploaded) "📸 Attach Another Screenshot" else "📸 Upload SMS Screenshot")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSuccess) GreenSuccess else Color(0xFF1E3A5F)),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Done", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // ── Composer opened (incl. MIUI auto-open) ───────────────────────
            if (state.composerOpened) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberWarningContainer),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("SMS App Opened", fontWeight = FontWeight.Bold, color = AmberWarning, fontSize = 16.sp)
                        Text(
                            "The message has been opened in your SMS app. Press Send there, then come back here and attach a screenshot as evidence.",
                            fontSize = 13.sp,
                            color = Color(0xFF374151),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        if (state.error != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                            ) {
                                Text(state.error.orEmpty(), color = Color(0xFFDC2626), modifier = Modifier.padding(10.dp), fontSize = 12.sp)
                            }
                        }
                        if (state.screenshotUploaded) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                            ) {
                                Text("✓ Screenshot uploaded successfully", color = Color(0xFF16A34A), modifier = Modifier.padding(10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(
                            onClick = { screenshotPicker.launch("image/*") },
                            enabled = !state.isUploadingScreenshot,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (state.isUploadingScreenshot) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Uploading…", fontWeight = FontWeight.SemiBold)
                            } else {
                                Text(
                                    if (state.screenshotUploaded) "📸 Attach Another Screenshot" else "📸 Attach SMS Screenshot",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Done", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
