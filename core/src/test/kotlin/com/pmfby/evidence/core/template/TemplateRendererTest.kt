package com.pmfby.evidence.core.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateRendererTest {

    @Test
    fun `substitutes all known variables`() {
        val template = "किसान {Farmer_Name}, गाँव {Village_Name}, प्रयास {Attempt_No}"
        val result = TemplateRenderer.render(
            template,
            mapOf("Farmer_Name" to "Ramesh Kumar", "Village_Name" to "Rohtak", "Attempt_No" to "2"),
        )
        assertEquals("किसान Ramesh Kumar, गाँव Rohtak, प्रयास 2", result.rendered)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `missing variable renders as empty string and is reported`() {
        val result = TemplateRenderer.render("Name: {Farmer_Name}, Mobile: {Farmer_Mobile}", mapOf("Farmer_Name" to "Sita"))
        assertEquals("Name: Sita, Mobile: ", result.rendered)
        assertEquals(listOf("Farmer_Mobile"), result.missing)
    }

    @Test
    fun `blank-string variable counts as missing, not as a valid empty value`() {
        val result = TemplateRenderer.render("{Denial_Reason}", mapOf("Denial_Reason" to ""))
        assertEquals("", result.rendered)
        assertEquals(listOf("Denial_Reason"), result.missing)
    }

    @Test
    fun `template with no placeholders is returned unchanged`() {
        val result = TemplateRenderer.render("Plain text, no vars.", emptyMap())
        assertEquals("Plain text, no vars.", result.rendered)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `only the three SMS formats are SMS-eligible, incident format is internal-only`() {
        assertTrue(TemplateRenderer.isEligibleForSmsComposer("FORMAT_1_DENIED"))
        assertTrue(TemplateRenderer.isEligibleForSmsComposer("FORMAT_2_POSTPONED"))
        assertTrue(TemplateRenderer.isEligibleForSmsComposer("FORMAT_3_WRONG_NUMBER"))
        assertFalse(TemplateRenderer.isEligibleForSmsComposer("FORMAT_4_INCIDENT"))
        assertFalse(TemplateRenderer.isEligibleForSmsComposer("SOME_UNKNOWN_CODE"))
    }
}
