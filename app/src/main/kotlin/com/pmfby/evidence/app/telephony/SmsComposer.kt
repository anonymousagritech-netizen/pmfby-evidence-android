package com.pmfby.evidence.app.telephony

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands the rendered SMS text off to the phone's own SMS app via ACTION_SENDTO +
 * `smsto:` — this app never sends SMS programmatically (no SEND_SMS permission is even
 * requested; see AndroidManifest.xml and README). Because of this, the backend/client can
 * only ever record that the composer was opened, never that the message was actually
 * sent or delivered — the caller must record status as "composer_opened", and the
 * backend's message_events.status mirrors this (`drafted`/`opened_composer`, never
 * `sent`/`delivered`; see backend-and-web/backend/src/routes/messageEvents.js).
 */
object SmsComposer {

    /** Returns true if an SMS app responded to the intent (composer actually opened). */
    fun openComposer(context: Context, phoneNumber: String?, body: String): Boolean {
        val uri = if (phoneNumber.isNullOrBlank()) Uri.parse("smsto:") else Uri.parse("smsto:$phoneNumber")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            false
        }
    }
}
