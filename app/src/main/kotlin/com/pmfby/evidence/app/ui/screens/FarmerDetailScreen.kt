package com.pmfby.evidence.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pmfby.evidence.app.ui.LocalApp
import com.pmfby.evidence.app.ui.appViewModel
import com.pmfby.evidence.app.ui.components.ErrorBanner
import com.pmfby.evidence.app.ui.components.FullScreenLoading
import com.pmfby.evidence.app.ui.components.PmfbyTopBar
import com.pmfby.evidence.app.ui.theme.AmberWarning
import com.pmfby.evidence.app.ui.theme.AmberWarningContainer
import com.pmfby.evidence.app.ui.theme.GreenSuccess
import com.pmfby.evidence.app.ui.theme.GreenSuccessContainer
import com.pmfby.evidence.app.ui.theme.IndigoCall
import com.pmfby.evidence.app.ui.theme.RedEmergency
import com.pmfby.evidence.app.ui.theme.RedEmergencyContainer
import com.pmfby.evidence.app.ui.theme.VioletSms
import com.pmfby.evidence.core.status.CaseStatusInput
import com.pmfby.evidence.core.status.CaseStatusMapper
import com.pmfby.evidence.core.status.CaseStatusTab

private fun statusChipColors(tab: CaseStatusTab): Pair<Color, Color> = when (tab) {
    CaseStatusTab.EMERGENCY         -> Pair(RedEmergencyContainer, RedEmergency)
    CaseStatusTab.ESCALATED         -> Pair(RedEmergencyContainer, RedEmergency)
    CaseStatusTab.CALL_NOT_ANSWERED -> Pair(AmberWarningContainer, AmberWarning)
    CaseStatusTab.PENDING           -> Pair(AmberWarningContainer, AmberWarning)
    CaseStatusTab.SMS_SENT          -> Pair(Color(0xFFF5F3FF), Color(0xFF7C3AED))
    CaseStatusTab.CONTACTED         -> Pair(GreenSuccessContainer, GreenSuccess)
    CaseStatusTab.SURVEY_COMPLETED  -> Pair(GreenSuccessContainer, GreenSuccess)
    CaseStatusTab.ASSIGNED          -> Pair(Color(0xFFEEF3F9), Color(0xFF1E3A5F))
}

private fun statusLabel(tab: CaseStatusTab): String = when (tab) {
    CaseStatusTab.EMERGENCY         -> "🚨 EMERGENCY"
    CaseStatusTab.ESCALATED         -> "⚠️ ESCALATED"
    CaseStatusTab.CALL_NOT_ANSWERED -> "📵 NOT ANSWERED"
    CaseStatusTab.PENDING           -> "⏳ PENDING"
    CaseStatusTab.SMS_SENT          -> "💬 SMS SENT"
    CaseStatusTab.CONTACTED         -> "✅ CONTACTED"
    CaseStatusTab.SURVEY_COMPLETED  -> "🎉 COMPLETED"
    CaseStatusTab.ASSIGNED          -> "📋 ASSIGNED"
}

private fun outcomeChipColor(outcome: String?): Pair<Color, Color> = when (outcome) {
    "agreed"       -> Pair(GreenSuccessContainer, GreenSuccess)
    "denied"       -> Pair(RedEmergencyContainer, RedEmergency)
    "postponed"    -> Pair(AmberWarningContainer, AmberWarning)
    "not_answered" -> Pair(AmberWarningContainer, AmberWarning)
    "wrong_number" -> Pair(Color(0xFFF5F3FF), Color(0xFF7C3AED))
    "completed"    -> Pair(GreenSuccessContainer, GreenSuccess)
    else           -> Pair(Color(0xFFEEF3F9), Color(0xFF6B7280))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmerDetailScreen(
    farmerId: String,
    onBack: () -> Unit,
    onCaseClick: (String) -> Unit,
    onSmsBatch: (sessionId: String) -> Unit,
) {
    val app = LocalApp()
    val viewModel = appViewModel { FarmerDetailViewModel(it, it.farmerRepository, it.apiService, app.sessionManager, farmerId) }
    val state by viewModel.uiState.collectAsState()
    val outcomeSessionId by viewModel.outcomeSessionId.collectAsState()
    val isSettingOutcome by viewModel.isSettingOutcome.collectAsState()
    val context = LocalContext.current

    // Reload batches every time the screen comes back into view (user returns from
    // CallScreen or SmsTemplateScreen — new call/SMS events must appear immediately).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
                viewModel.onReturnedToApp()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Open phone dialer when a call session is ready
    LaunchedEffect(state.pendingCallSession) {
        val pending = state.pendingCallSession ?: return@LaunchedEffect
        val dialUri = Uri.parse("tel:${pending.second}")
        context.startActivity(Intent(Intent.ACTION_DIAL, dialUri))
        viewModel.clearPendingCall()
    }

    // Navigate to SMS template screen when session is ready
    LaunchedEffect(state.pendingSmsSessionId) {
        val sessionId = state.pendingSmsSessionId ?: return@LaunchedEffect
        onSmsBatch(sessionId)
        viewModel.clearPendingSms()
    }

    // Outcome bottom sheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (outcomeSessionId != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearOutcomeSheet() },
            sheetState = sheetState,
            containerColor = Color.White,
        ) {
            CallOutcomeSheet(
                sessionId = outcomeSessionId!!,
                isLoading = isSettingOutcome,
                fileUploadApi = app.fileUploadApi,
                authHeader = app.sessionManager.authHeader(),
                onOutcome = { sessionId, outcome -> viewModel.setCallOutcome(sessionId, outcome) },
                onDismiss = { viewModel.clearOutcomeSheet() },
            )
        }
    }

    Scaffold(topBar = { PmfbyTopBar(title = state.farmer?.farmerName ?: "Farmer", onBack = onBack) }) { padding ->
        if (state.isLoading && state.farmer == null) { FullScreenLoading(); return@Scaffold }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { ErrorBanner(state.error) }
            item { com.pmfby.evidence.app.ui.components.AllFilesAccessCard(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
            item {
                val recStatus by viewModel.recordingStatus.collectAsState()
                com.pmfby.evidence.app.ui.components.recordingStatusLabel(recStatus)?.let { (text, _) ->
                    Text(
                        text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (recStatus) {
                            RecordingStatus.UPLOADED -> GreenSuccess
                            RecordingStatus.NO_ACCESS, RecordingStatus.NOT_FOUND, RecordingStatus.FAILED -> RedEmergency
                            else -> Color(0xFF1E3A5F)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // ── Farmer hero card ──────────────────────────────────────────────
            item {
                state.farmer?.let { farmer ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F2040)))),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(farmer.farmerName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                    Text(farmer.farmerMobile ?: "No mobile", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                }
                            }
                            if (!farmer.villageName.isNullOrBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("📍 ", color = Color.White, fontSize = 13.sp)
                                    Text(farmer.villageName.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Batch section header ──────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FF)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Intimation Batches", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E3A5F))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isCreatingSession) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF1E3A5F))
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(
                            modifier = Modifier.background(Color(0xFF1E3A5F), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text("${state.batches.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Batch cards ───────────────────────────────────────────────────
            if (state.batches.isNotEmpty()) {
                items(state.batches, key = { it.intimationDate }) { batch ->
                    BatchCard(
                        batch = batch,
                        isCreatingSession = state.isCreatingSession,
                        onCall = { viewModel.callBatch(batch) },
                        onSms = { viewModel.smsBatch(batch) },
                        onRecordOutcome = { sessionId -> viewModel.showOutcomeSheet(sessionId) },
                    )
                }
            } else if (!state.isLoading) {
                // Fallback: show cases when no batch data available
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FF)).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Survey Cases", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E3A5F))
                        Box(
                            modifier = Modifier.background(Color(0xFF1E3A5F), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text("${state.cases.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                items(state.cases, key = { it.id }) { case ->
                    CaseRow(case = case, onClick = { onCaseClick(case.id) })
                }
            }

            // ── All Dockets (under batches) ───────────────────────────────────
            if (state.batches.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF8F9FF)).padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text("All Dockets", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E3A5F))
                    }
                }
                items(state.cases, key = { it.id }) { case ->
                    CaseRow(case = case, onClick = { onCaseClick(case.id) })
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun BatchCard(
    batch: CaseBatch,
    isCreatingSession: Boolean,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onRecordOutcome: (String) -> Unit,
) {
    val (outcomeBg, outcomeFg) = outcomeChipColor(batch.latestSessionOutcome)
    val hasUnrecordedOutcome = batch.latestSessionId != null && batch.latestSessionOutcome == null

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Date + case count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "📅 Intimation: ${batch.displayDate}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E3A5F),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("${batch.caseCount} docket${if (batch.caseCount != 1) "s" else ""}", fontSize = 13.sp, color = Color(0xFF6B7280))
                }
                if (batch.latestSessionOutcome != null) {
                    Box(
                        modifier = Modifier.background(outcomeBg, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            batch.latestSessionOutcome.replace('_', ' ').uppercase(),
                            color = outcomeFg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Docket IDs
            if (batch.docketIds.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val shown = batch.docketIds.take(3)
                val extra = batch.docketIds.size - shown.size
                val text = shown.joinToString(", ") + if (extra > 0) " …+$extra more" else ""
                Text(text, fontSize = 12.sp, color = Color(0xFF374151), lineHeight = 18.sp)
            }

            // Last contacted timestamp
            if (batch.latestContactedAt != null) {
                Spacer(Modifier.height(4.dp))
                Text("Last contact: ${batch.latestContactedAt.take(16).replace('T', ' ')}", fontSize = 11.sp, color = Color(0xFF9CA3AF))
            }

            Spacer(Modifier.height(12.dp))

            // SMS evidence block — shown whenever an SMS session exists for this batch
            if (batch.smsCount > 0 || batch.contactType == "sms") {
                val smsStatusColor = when (batch.lastSmsStatus) {
                    "sent", "delivered" -> GreenSuccess
                    "send_failed", "delivery_failed" -> RedEmergency
                    "send_status_unknown", "opened_composer", "drafted" -> AmberWarning
                    else -> Color(0xFF7C3AED)
                }
                val smsStatusLabel = when (batch.lastSmsStatus) {
                    "sent" -> "✅ SMS Sent"
                    "delivered" -> "✅ SMS Delivered"
                    "send_failed" -> "❌ Send Failed"
                    "delivery_failed" -> "❌ Delivery Failed"
                    "send_status_unknown" -> "⚠️ SMS NOT confirmed — resend or send via Messages"
                    "opened_composer" -> "✉️ Opened in Messages (awaiting your Send)"
                    "drafted" -> "✉️ Draft only — not sent"
                    else -> "💬 SMS logged"
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(smsStatusLabel, fontWeight = FontWeight.Bold, color = smsStatusColor, fontSize = 13.sp)
                        }
                        if (!batch.lastSmsText.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                batch.lastSmsText.take(120) + if (batch.lastSmsText.length > 120) "…" else "",
                                fontSize = 11.sp,
                                color = Color(0xFF374151),
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }

            // "Record Outcome" banner when a call was made but outcome not recorded
            if (hasUnrecordedOutcome) {
                Card(
                    onClick = { onRecordOutcome(batch.latestSessionId!!) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberWarningContainer),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📝", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Record Call Outcome", fontWeight = FontWeight.Bold, color = AmberWarning, fontSize = 13.sp)
                            Text("Tap to save what happened + attach call screenshot as evidence", fontSize = 11.sp, color = Color(0xFF92400E))
                        }
                    }
                }
            }

            // Action buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    enabled = !isCreatingSession,
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoCall),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call Farmer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onSms,
                    modifier = Modifier.weight(1f),
                    enabled = !isCreatingSession,
                    colors = ButtonDefaults.buttonColors(containerColor = VioletSms),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("💬 SMS Farmer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Call outcome bottom sheet ─────────────────────────────────────────────────

@Composable
private fun CallOutcomeSheet(
    sessionId: String,
    isLoading: Boolean,
    fileUploadApi: com.pmfby.evidence.app.data.remote.FileUploadApi,
    authHeader: String?,
    onOutcome: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingUri = uri
    }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        if (authHeader == null) { uploadStatus = "Not signed in"; return@LaunchedEffect }
        isUploading = true
        uploadStatus = null
        try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "call_evidence_${System.currentTimeMillis()}.jpg"
            val dir = java.io.File(context.cacheDir, "evidence").apply { mkdirs() }
            val target = java.io.File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            fileUploadApi.uploadEvidence(
                authHeader = authHeader,
                file = target,
                mimeType = mime,
                fields = mapOf("sessionId" to sessionId, "kind" to "call_screenshot"),
            )
            target.delete()
            uploadStatus = "✓ Screenshot uploaded as evidence"
        } catch (e: Exception) {
            uploadStatus = "Upload failed: ${e.message}"
        } finally {
            isUploading = false
            pendingUri = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text("Record Call Outcome", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF1E3A5F))
        Text("What happened when you called the farmer?", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

        val outcomes = listOf(
            Triple("agreed",       "✅ Farmer Agreed",       GreenSuccess),
            Triple("denied",       "❌ Farmer Denied",       RedEmergency),
            Triple("not_answered", "📵 Not Answered",        AmberWarning),
            Triple("postponed",    "⏰ Postponed",           Color(0xFFEA580C)),
            Triple("wrong_number", "❓ Wrong Number",        Color(0xFF7C3AED)),
            Triple("completed",    "🎉 Survey Completed",    GreenSuccess),
        )

        outcomes.forEach { (key, label, color) ->
            OutlinedButton(
                onClick = { if (!isLoading) onOutcome(sessionId, key) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.7f)),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Evidence screenshot attachment
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📸 Attach Call Evidence (Optional)", fontWeight = FontWeight.Bold, color = Color(0xFF1E3A5F), fontSize = 13.sp)
                Text(
                    "Take a screenshot of the call log showing the duration and attach it as proof.",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                if (uploadStatus != null) {
                    Text(
                        uploadStatus!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (uploadStatus!!.startsWith("✓")) GreenSuccess else RedEmergency,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Uploading…")
                    } else {
                        Text(if (uploadStatus?.startsWith("✓") == true) "📸 Attach Another" else "📸 Attach Call Screenshot")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CaseRow(case: com.pmfby.evidence.app.data.local.SurveyCaseEntity, onClick: () -> Unit) {
    val tab = CaseStatusMapper.tabFor(
        CaseStatusInput(case.contactStatus, case.attemptCount, case.forcefulSurveyFlag, case.lastCallOutcome, case.lastMessageStatus),
    )
    val (chipBg, chipFg) = statusChipColors(tab)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (case.attemptCount == 0) "New case — no attempts yet" else "Attempt #${case.attemptCount}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E3A5F),
                )
                case.postponedUntil?.let {
                    Spacer(Modifier.height(2.dp))
                    Text("Postponed until: $it", fontSize = 12.sp, color = AmberWarning)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.background(chipBg, RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(statusLabel(tab), color = chipFg, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}
