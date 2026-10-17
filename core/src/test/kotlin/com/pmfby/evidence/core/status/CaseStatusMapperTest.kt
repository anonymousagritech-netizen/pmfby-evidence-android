package com.pmfby.evidence.core.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CaseStatusMapperTest {

    private fun input(
        contactStatus: String? = "pending_contact",
        attemptCount: Int = 0,
        forcefulSurveyFlag: Boolean = false,
        lastCallOutcome: String? = null,
        lastMessageStatus: String? = null,
    ) = CaseStatusInput(contactStatus, attemptCount, forcefulSurveyFlag, lastCallOutcome, lastMessageStatus)

    @Test
    fun `a never-attempted case is ASSIGNED`() {
        assertEquals(CaseStatusTab.ASSIGNED, CaseStatusMapper.tabFor(input(attemptCount = 0)))
    }

    @Test
    fun `postponed is PENDING regardless of attempt count`() {
        assertEquals(CaseStatusTab.PENDING, CaseStatusMapper.tabFor(input(contactStatus = "postponed", attemptCount = 3)))
    }

    @Test
    fun `an attempted case with a not-reached call outcome is CALL_NOT_ANSWERED`() {
        val result = CaseStatusMapper.tabFor(
            input(contactStatus = "pending_contact", attemptCount = 1, lastCallOutcome = "not_answered"),
        )
        assertEquals(CaseStatusTab.CALL_NOT_ANSWERED, result)
    }

    @Test
    fun `switched_off and busy and unreachable are all CALL_NOT_ANSWERED`() {
        listOf("switched_off", "busy", "unreachable", "invalid_number", "farmer_not_found").forEach { outcome ->
            val result = CaseStatusMapper.tabFor(input(attemptCount = 1, lastCallOutcome = outcome))
            assertEquals(CaseStatusTab.CALL_NOT_ANSWERED, result, "expected CALL_NOT_ANSWERED for outcome=$outcome")
        }
    }

    @Test
    fun `an attempted case with only a logged SMS is SMS_SENT`() {
        val result = CaseStatusMapper.tabFor(
            input(attemptCount = 1, lastCallOutcome = null, lastMessageStatus = "opened_composer"),
        )
        assertEquals(CaseStatusTab.SMS_SENT, result)
    }

    @Test
    fun `a drafted-only message status still counts as SMS_SENT`() {
        assertEquals(CaseStatusTab.SMS_SENT, CaseStatusMapper.tabFor(input(attemptCount = 1, lastMessageStatus = "drafted")))
    }

    @Test
    fun `denied and wrong_number are CONTACTED`() {
        assertEquals(CaseStatusTab.CONTACTED, CaseStatusMapper.tabFor(input(contactStatus = "denied")))
        assertEquals(CaseStatusTab.CONTACTED, CaseStatusMapper.tabFor(input(contactStatus = "wrong_number")))
    }

    @Test
    fun `completed is SURVEY_COMPLETED`() {
        assertEquals(CaseStatusTab.SURVEY_COMPLETED, CaseStatusMapper.tabFor(input(contactStatus = "completed")))
    }

    @Test
    fun `dispute_incident is ESCALATED`() {
        assertEquals(CaseStatusTab.ESCALATED, CaseStatusMapper.tabFor(input(contactStatus = "dispute_incident")))
    }

    @Test
    fun `forceful survey flag always wins as EMERGENCY, even over completed`() {
        val result = CaseStatusMapper.tabFor(input(contactStatus = "completed", forcefulSurveyFlag = true))
        assertEquals(CaseStatusTab.EMERGENCY, result)
    }

    @Test
    fun `forceful survey flag wins over dispute_incident too`() {
        val result = CaseStatusMapper.tabFor(input(contactStatus = "dispute_incident", forcefulSurveyFlag = true))
        assertEquals(CaseStatusTab.EMERGENCY, result)
    }

    @Test
    fun `an attempted case with neither a call nor sms signal falls back to ASSIGNED`() {
        val result = CaseStatusMapper.tabFor(input(contactStatus = "pending_contact", attemptCount = 2))
        assertEquals(CaseStatusTab.ASSIGNED, result)
    }

    @Test
    fun `filter returns only cases matching the requested tab`() {
        val cases = listOf(
            input(contactStatus = "completed"),
            input(contactStatus = "pending_contact", attemptCount = 0),
            input(contactStatus = "denied"),
        )
        val completed = CaseStatusMapper.filter(cases, CaseStatusTab.SURVEY_COMPLETED)
        assertEquals(1, completed.size)
        assertEquals(CaseStatusTab.SURVEY_COMPLETED, CaseStatusMapper.tabFor(completed[0]))
    }
}
