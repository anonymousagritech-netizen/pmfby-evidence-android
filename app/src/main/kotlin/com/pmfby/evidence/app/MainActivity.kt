package com.pmfby.evidence.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.pmfby.evidence.app.navigation.AppNavigation
import com.pmfby.evidence.app.ui.theme.PmfbyEvidenceTheme

/**
 * Single-activity app (standard for Jetpack Compose + Navigation-Compose) — every screen
 * listed in the task spec is a composable destination inside AppNavigation's NavHost.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLinkRoute = intent?.getStringExtra(PhoneIntentActivity.EXTRA_START_ROUTE)
        setContent {
            PmfbyEvidenceTheme {
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                LaunchedEffect(Unit) {
                    // Requested once up front so the surveyor isn't interrupted mid-task
                    // later (geotag capture, emergency recording, voice notes). Every one
                    // of these degrades gracefully if denied — see LocationProvider
                    // (returns null, never a fake coordinate), EmergencyCaptureService
                    // (reports an error via its listener), CallStateObserver (silently
                    // no-ops) — none of this app's core flows hard-crash on a denial.
                    val permissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        add(Manifest.permission.CAMERA)
                        add(Manifest.permission.RECORD_AUDIO)
                        add(Manifest.permission.READ_PHONE_STATE)
                        add(Manifest.permission.SEND_SMS)
                        add(Manifest.permission.READ_SMS)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                            add(Manifest.permission.READ_MEDIA_IMAGES)
                            add(Manifest.permission.READ_MEDIA_AUDIO)
                        }
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
                AppNavigation(deepLinkRoute = deepLinkRoute)
            }
        }
    }
}
