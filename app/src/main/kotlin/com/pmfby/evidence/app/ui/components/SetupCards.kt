package com.pmfby.evidence.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pmfby.evidence.app.telephony.SystemCallRecordingFinder
import com.pmfby.evidence.app.ui.screens.RecordingStatus

/** Re-checked every time the screen resumes, so it flips as soon as the user returns from Settings. */
@Composable
fun rememberAllFilesAccess(): Boolean {
    var granted by remember { mutableStateOf(SystemCallRecordingFinder.hasAccess()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = SystemCallRecordingFinder.hasAccess()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** Shown until "All files access" is granted — without it call recordings cannot be auto-attached. */
@Composable
fun AllFilesAccessCard(modifier: Modifier = Modifier) {
    if (rememberAllFilesAccess()) return
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("One-time setup: auto call recordings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF92400E))
            Text(
                "Your phone already records calls. Allow \"All files access\" once and every call recording is found and uploaded as evidence automatically.",
                fontSize = 12.sp,
                color = Color(0xFF78350F),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Button(
                onClick = {
                    val pkg = Uri.parse("package:${context.packageName}")
                    try {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
                    } catch (_: Exception) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Allow now", fontWeight = FontWeight.Bold) }
        }
    }
}

fun recordingStatusLabel(status: RecordingStatus): Pair<String, Color>? = when (status) {
    RecordingStatus.NONE -> null
    RecordingStatus.WAITING -> "Call recording will be picked up automatically when the call ends" to Color(0xFFCBD5E1)
    RecordingStatus.FINDING -> "Finding & uploading call recording…" to Color(0xFFFDE68A)
    RecordingStatus.UPLOADED -> "✓ Call recording uploaded as evidence" to Color(0xFF86EFAC)
    RecordingStatus.NO_ACCESS -> "Recording not uploaded — allow All files access" to Color(0xFFFCA5A5)
    RecordingStatus.NOT_FOUND -> "No recording found — turn on automatic call recording in Phone settings" to Color(0xFFFCA5A5)
    RecordingStatus.FAILED -> "Recording upload failed — will retry when online" to Color(0xFFFCA5A5)
}
