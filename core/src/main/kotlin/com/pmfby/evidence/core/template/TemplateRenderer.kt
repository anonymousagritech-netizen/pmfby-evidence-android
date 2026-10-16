package com.pmfby.evidence.core.template

/**
 * Client-side mirror of the backend's `{Variable}` placeholder renderer
 * (backend-and-web/backend/src/utils/templateRenderer.js) — used so the SMS
 * template-picker screen can show the surveyor a live preview before the
 * server-authoritative `/api/templates/:id/render` call is made (Attempt_No in
 * particular is always backend-assigned; this local render is preview-only,
 * never the value that gets sent).
 *
 * Placeholder syntax matches the backend exactly: `{VarName}`, letters/digits/underscore.
 */
object TemplateRenderer {
    private val PLACEHOLDER_REGEX = Regex("\\{([A-Za-z0-9_]+)}")

    data class RenderResult(val rendered: String, val missing: List<String>)

    fun render(bodyTemplate: String, data: Map<String, String?>): RenderResult {
        val missing = mutableListOf<String>()
        val rendered = PLACEHOLDER_REGEX.replace(bodyTemplate) { match ->
            val varName = match.groupValues[1]
            val value = data[varName]
            if (value.isNullOrEmpty()) {
                missing.add(varName)
                ""
            } else {
                value
            }
        }
        return RenderResult(rendered, missing)
    }

    /** Which template codes are allowed to be offered as an SMS send option. */
    private val SMS_ELIGIBLE_CODES = setOf(
        "FORMAT_1_DENIED",
        "FORMAT_2_POSTPONED",
        "FORMAT_3_WRONG_NUMBER",
    )

    /**
     * FORMAT_4_INCIDENT is internal-only and must never be offered as an SMS send option
     * in the template picker — enforced here, not just by backend route wiring, so the
     * UI can never even render the "Send via SMS" button for it.
     */
    fun isEligibleForSmsComposer(templateCode: String): Boolean = templateCode in SMS_ELIGIBLE_CODES
}
