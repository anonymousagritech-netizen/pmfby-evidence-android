package com.pmfby.evidence.app.navigation

/** Centralized nav-compose route strings/builders — see AppNavigation.kt for the NavHost. */
object Routes {
    const val LOGIN = "login"
    const val FARMERS = "farmers"

    const val FARMER_DETAIL_PATTERN = "farmer/{farmerId}"
    fun farmerDetail(farmerId: String) = "farmer/$farmerId"

    const val CASE_DETAIL_PATTERN = "case/{caseId}"
    fun caseDetail(caseId: String) = "case/$caseId"

    const val CALL_PATTERN = "call/{caseId}"
    fun call(caseId: String) = "call/$caseId"

    const val VOICE_MESSAGE_PATTERN = "voice-message/{caseId}"
    fun voiceMessage(caseId: String) = "voice-message/$caseId"

    const val SMS_PATTERN = "sms/{caseId}"
    fun sms(caseId: String) = "sms/$caseId"

    const val EVIDENCE_HISTORY_PATTERN = "evidence/{caseId}"
    fun evidenceHistory(caseId: String) = "evidence/$caseId"

    const val EMERGENCY_PATTERN = "emergency/{caseId}"
    fun emergency(caseId: String) = "emergency/$caseId"

    const val DISCREPANCY_PATTERN = "discrepancy/{caseId}"
    fun discrepancy(caseId: String) = "discrepancy/$caseId"

    const val INTEGRITY_CONCERN = "integrity-concern"
    const val UPLOAD_QUEUE = "upload-queue"
    const val PROFILE = "profile"

    const val ARG_CASE_ID = "caseId"
    const val ARG_FARMER_ID = "farmerId"
}
