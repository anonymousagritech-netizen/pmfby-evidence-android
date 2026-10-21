package com.pmfby.evidence.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pmfby.evidence.app.ui.LocalApp
import com.pmfby.evidence.app.ui.screens.CallScreen
import com.pmfby.evidence.app.ui.screens.CaseDetailScreen
import com.pmfby.evidence.app.ui.screens.ChangePasswordScreen
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
fun AppNavigation(deepLinkRoute: String? = null) {
    val navController = rememberNavController()
    val app = LocalApp()
    val startDestination = when {
        !app.sessionManager.isSignedIn() -> Routes.LOGIN
        app.sessionManager.forcePasswordChange() -> Routes.CHANGE_PASSWORD
        else -> Routes.FARMERS
    }

    // When launched from PhoneIntentActivity (tel:/smsto: chooser), navigate directly to
    // the target screen once the graph is ready — user sees CallScreen or SmsTemplateScreen
    // immediately with no extra taps.
    if (deepLinkRoute != null) {
        LaunchedEffect(deepLinkRoute) {
            navController.navigate(deepLinkRoute)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    val next = if (app.sessionManager.forcePasswordChange()) Routes.CHANGE_PASSWORD else Routes.FARMERS
                    navController.navigateSingleTopClearingBackStack(next)
                },
            )
        }

        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(onDone = { navController.navigateSingleTopClearingBackStack(Routes.FARMERS) })
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
                onSmsBatch = { sessionId -> navController.navigate(Routes.smsSession(sessionId)) },
            )
        }

        composable(Routes.CASE_DETAIL_PATTERN) { backStackEntry ->
            val caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID)
            CaseDetailScreen(
                caseId = caseId,
                onBack = { navController.popBackStack() },
                onCall = { navController.navigate(Routes.call(it)) },
                onSms = { navController.navigate(Routes.sms(it)) },
                onSmsWithCode = { id, code -> navController.navigate(Routes.smsWithCode(id, code)) },
                onEvidenceHistory = { navController.navigate(Routes.evidenceHistory(it)) },
                onEmergency = { navController.navigate(Routes.emergency(it)) },
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
                sessionId = null,
                preselectedTemplateCode = null,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.SMS_CODE_PATTERN) { backStackEntry ->
            SmsTemplateScreen(
                caseId = backStackEntry.requireArg(Routes.ARG_CASE_ID),
                sessionId = null,
                preselectedTemplateCode = backStackEntry.requireArg(Routes.ARG_TEMPLATE_CODE),
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.SMS_SESSION_PATTERN) { backStackEntry ->
            SmsTemplateScreen(
                caseId = null,
                sessionId = backStackEntry.requireArg(Routes.ARG_SESSION_ID),
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
