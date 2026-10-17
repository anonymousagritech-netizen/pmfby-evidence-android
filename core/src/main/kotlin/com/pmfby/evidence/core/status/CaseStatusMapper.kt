package com.pmfby.evidence.core.status

/**
 * Derives the farmer-list status-tab bucket for a cached survey case. Pure mapping logic
 * (no Android/DB imports) so it is unit-testable on the plain JVM — see
 * CaseStatusMapperTest. Mirrors `survey_cases.contact_status` exactly as written by the
 * backend's mandatory outcome-resolution loop (backend-and-web/backend/src/routes/cases.js,
 * POST /cases/:id/resolve-outcome, VALID_OUTCOMES) plus `forceful_survey_flag`
 * (backend-and-web/backend/src/routes/emergencyIncidents.js).
 *
 * `contact_status` is NEVER actually null on a real server row (the column is NOT NULL,
 * default 'pending_contact' — see migration 1700000003000_farmers_cases.js), but this
 * accepts null defensively for a locally-constructed/partial cache row.
 *
 * "Call-not-answered" and "SMS-sent" are NOT columns `survey_cases` itself exposes — they
 * describe the most recent *attempt* (call_events.outcome / message_events.status), which
 * live in separate tables the farmer-list/case-detail GET response does not join in. This
 * app writes [lastCallOutcome]/[lastMessageStatus] into the local cache itself, right after
 * a call outcome or SMS-composer-opened event the surveyor just submitted succeeds (see
 * data.repository.CaseActionsRepository) — so these two tabs are only populated from this
 * device's own activity, not from server-side history. On a fresh install, before any local
 * attempt on a case, such a case falls back to ASSIGNED/PENDING even if another surveyor
 * (or the web admin) already made attempts server-side. This is a known, documented
 * approximation (see android-kotlin/LIMITATIONS.md), not a bug.
 */
enum class CaseStatusTab {
    ASSIGNED,
    PENDING,
    CALL_NOT_ANSWERED,
    SMS_SENT,
    CONTACTED,
    SURVEY_COMPLETED,
    ESCALATED,
    EMERGENCY,
}

/** Minimal, pure-data view of the fields [CaseStatusMapper] needs — no Room/Entity import. */
data class CaseStatusInput(
    val contactStatus: String?,
    val attemptCount: Int,
    val forcefulSurveyFlag: Boolean,
    val lastCallOutcome: String? = null,
    val lastMessageStatus: String? = null,
)

object CaseStatusMapper {

    private val NOT_REACHED_CALL_OUTCOMES = setOf(
        "not_answered", "busy", "switched_off", "unreachable", "invalid_number", "farmer_not_found",
    )
    private val SMS_LOGGED_STATUSES = setOf("drafted", "opened_composer")
    private val CONTACTED_OUTCOMES = setOf("denied", "wrong_number")

    fun tabFor(input: CaseStatusInput): CaseStatusTab = when {
        input.forcefulSurveyFlag -> CaseStatusTab.EMERGENCY
        input.contactStatus == "dispute_incident" -> CaseStatusTab.ESCALATED
        input.contactStatus == "completed" -> CaseStatusTab.SURVEY_COMPLETED
        input.contactStatus in CONTACTED_OUTCOMES -> CaseStatusTab.CONTACTED
        input.contactStatus == "postponed" -> CaseStatusTab.PENDING
        input.attemptCount <= 0 -> CaseStatusTab.ASSIGNED
        input.lastCallOutcome in NOT_REACHED_CALL_OUTCOMES -> CaseStatusTab.CALL_NOT_ANSWERED
        input.lastMessageStatus in SMS_LOGGED_STATUSES -> CaseStatusTab.SMS_SENT
        else -> CaseStatusTab.ASSIGNED
    }

    fun filter(cases: List<CaseStatusInput>, tab: CaseStatusTab): List<CaseStatusInput> =
        cases.filter { tabFor(it) == tab }
}
