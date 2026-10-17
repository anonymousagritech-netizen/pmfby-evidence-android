package com.pmfby.evidence.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pmfby.evidence.app.ui.LocalApp
import com.pmfby.evidence.app.ui.screens.CallScreen
import com.pmfby.evidence.app.ui.screens.CaseDetailScreen
import com.pmfby.evidence.app.ui.screens.DiscrepancyFlagScreen
import com.pmfby.evidence.app.ui.screens.EmergencyScreen
import com.pmfby.evidence.app.ui.screens.EvidenceHistoryScreen
import com.pmfby.evidence.app.ui.screens.FarmerDetailScreen
import com.pmfby.evidence.app.ui.screens.FarmerListScreen
import com.pmfby.evidence.app.ui.screens.IntegrityConcernScreen
import com.pmfby.evidence.app.ui.screens.LoginScreen
import com.pmfby.evidence.app.ui.screens.ProfileScreen
import com.pmfby.evidence.app.ui.screens.SmsTemplateScreen
import com.pmfby.evidence.app.ui.screens.UploadQueueScreen
import com.pmfby.evidence.app.ui.screens.VoiceMessageScreen

/** The whole app's navigation graph — one composable destination per task-spec screen. */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val app = LocalApp()
    val startDestination = if (app.sessionManager.isSignedIn()) Routes.FARMERS else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = { navController.navigateSingleTopClearingBackStack(Routes.FARMERS) })
        }

        composable(Routes.FARMERS) {
            FarmerListScreen(
                onFarmerClick = { farmerId -> navController.navigate(Routes.farmerDetail(farmerId)) },
                onOpenUploadQueue = { navController.navigate(Routes.UPLOAD_QUEUE) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.FARMER_DETAIL_PATTERN) { backStackEntry ->
            FarmerDetailScreen(
                farmerId = backStackEntry.requireArg(Routes.ARG_FARMER_ID),
                onBack = { navController.popBackStack() },
                onCaseClick = { caseId -> navController.navigate(Routes.caseDetail(caseId)) },
            )
        }

        composable(Routes.CASE_DETAIL_PATTERN) { backStackEntry ->
            val caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID)
            CaseDetailScreen(
                caseId = caseId,
                onBack = { navController.popBackStack() },
                onCall = { navController.navigate(Routes.call(it)) },
                onSms = { navController.navigate(Routes.sms(it)) },
                onVoiceMessage = { navController.navigate(Routes.voiceMessage(it)) },
                onEvidenceHistory = { navController.navigate(Routes.evidenceHistory(it)) },
                onEmergency = { navController.navigate(Routes.emergency(it)) },
                onDiscrepancy = { navController.navigate(Routes.discrepancy(it)) },
                onIntegrityConcern = { navController.navigate(Routes.INTEGRITY_CONCERN) },
            )
        }

        composable(Routes.CALL_PATTERN) { backStackEntry ->
            CallScreen(
                caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID),
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.SMS_PATTERN) { backStackEntry ->
            SmsTemplateScreen(
                caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID),
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.VOICE_MESSAGE_PATTERN) { backStackEntry ->
            VoiceMessageScreen(caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID), onBack = { navController.popBackStack() })
        }

        composable(Routes.EVIDENCE_HISTORY_PATTERN) { backStackEntry ->
            EvidenceHistoryScreen(caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID), onBack = { navController.popBackStack() })
        }

        composable(Routes.EMERGENCY_PATTERN) { backStackEntry ->
            EmergencyScreen(
                caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID),
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.DISCREPANCY_PATTERN) { backStackEntry ->
            DiscrepancyFlagScreen(
                caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID),
                onBack = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
            )
        }

        composable(Routes.INTEGRITY_CONCERN) {
            IntegrityConcernScreen(onBack = { navController.popBackStack() }, onSubmitted = { navController.popBackStack() })
        }

        composable(Routes.UPLOAD_QUEUE) {
            UploadQueueScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = { navController.navigateSingleTopClearingBackStack(Routes.LOGIN) },
            )
        }
    }
}

private fun NavHostController.navigateSingleTopClearingBackStack(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

private fun androidx.navigation.NavBackStackEntry.requireArg(name: String): String =
    requireNotNull(arguments?.getString(name)) { "Missing required nav argument: $name" }
