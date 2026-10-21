package com.pmfby.evidence.app.telephony

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * Sends SMS programmatically via SmsManager, requiring the SEND_SMS runtime permission —
 * the primary path now (replacing the old composer-only design; see README's older
 * "SEND_SMS is never requested" note, now superseded). This is viable here specifically
 * because the app is sideloaded, not Play-Store distributed: Play's SMS/Call Log
 * permissions policy restricts SEND_SMS to default SMS-handler apps, but that policy only
 * applies to apps distributed through Play. SmsComposer.openComposer (ACTION_SENDTO) is
 * kept as the fallback when SEND_SMS is denied or sending throws.
 *
 * Reports real carrier-level SENT/DELIVERED status via PendingIntent broadcasts — this is
 * actual confirmation, unlike a screenshot of the composer, which only proves the compose
 * screen was opened. DELIVERED status is not guaranteed by every carrier/handset (delivery
 * reports are opt-in at the carrier level and commonly disabled) — SENT (radio accepted the
 * message) is the reliable signal; DELIVERED is reported when it arrives, "unknown"
 * otherwise, and callers must not treat a missing DELIVERED as a failure.
 */
object DirectSmsSender {

    enum class Stage { SENT, SENT_FAILED, DELIVERED, DELIVERY_FAILED, DELIVERY_UNKNOWN }

    data class Result(val stage: Stage, val resultCode: Int, val partsAccepted: Int = 0, val partsTotal: Int = 0)

    /** Human-readable name for SmsManager's documented RESULT_ERROR_* result codes, so a
     * failure can be diagnosed from the UI/logs instead of just "it failed". */
    fun resultCodeName(code: Int): String = when (code) {
        android.app.Activity.RESULT_OK -> "RESULT_OK"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "RESULT_ERROR_GENERIC_FAILURE"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "RESULT_ERROR_NO_SERVICE (no cellular service for SMS — e.g. WiFi-only/VoWiFi without carrier SMS support)"
        SmsManager.RESULT_ERROR_NULL_PDU -> "RESULT_ERROR_NULL_PDU"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "RESULT_ERROR_RADIO_OFF (airplane mode or radio disabled)"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "RESULT_ERROR_LIMIT_EXCEEDED"
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "RESULT_ERROR_FDN_CHECK_FAILURE (Fixed Dialing Numbers restriction)"
        -1 -> "no result received at all"
        else -> "unknown result code $code"
    }

    private const val ACTION_SMS_SENT = "com.pmfby.evidence.app.SMS_SENT"
    private const val TAG = "PMFBY-SMS"

    /**
     * Sends [body] to [phoneNumber], splitting into multipart parts if needed. Emits one
     * [Result] for SENT (always, success or failure) and a second for DELIVERED if/when
     * that broadcast arrives (it may never arrive — see class doc). The flow completes
     * after emitting the SENT result if SENT failed (no point waiting for delivery of a
     * message that was never sent); otherwise it stays open for up to [deliveryTimeoutMs]
     * waiting for the delivery report, then closes with a DELIVERY_UNKNOWN result.
     */
    // Normalise an Indian mobile number to E.164 (+91XXXXXXXXXX) so that MIUI's RCS
    // engine does not match the bare 10-digit number to an RCS profile and then route
    // the send attempt through RCS (which may fail if the carrier or recipient doesn't
    // support it). SmsManager ignores the +91 prefix for domestic SMS routing.
    private fun toE164(number: String): String {
        val digits = number.replace(Regex("\\D"), "")
        return when {
            digits.startsWith("91") && digits.length == 12 -> "+$digits"
            digits.length == 10 -> "+91$digits"
            else -> number // already formatted or unknown — pass through unchanged
        }
    }

    /**
     * Sends [body] as a multipart SMS and reports how many parts the radio accepted.
     *
     * Delivery reports are deliberately NOT requested: on this MediaTek/Jio IMS stack, asking for a
     * status report per part made the modem hold every later part ("waiting for status report"),
     * so a 470-character Hindi message (8 UCS-2 parts) stalled after part 1 and never completed.
     * SENT (radio accepted every part) is the reliable signal; callers must not claim delivery.
     */
    fun send(context: Context, phoneNumber: String, body: String) = callbackFlow {
        @Suppress("DEPRECATION")
        val smsManager: SmsManager = run {
            val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val base = context.getSystemService(SmsManager::class.java)!!
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(subId) else base
            } else {
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                else
                    SmsManager.getDefault()
            }
        }
        val destination = toE164(phoneNumber)
        val parts = smsManager.divideMessage(body) ?: arrayListOf(body)
        var accepted = 0
        var answered = 0
        var failedCode = android.app.Activity.RESULT_OK

        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                answered++
                if (resultCode == android.app.Activity.RESULT_OK) accepted++ else failedCode = resultCode
                Log.i(TAG, "Part result code=$resultCode ($accepted ok / $answered answered of ${parts.size})")
                if (answered >= parts.size) {
                    val ok = accepted == parts.size
                    trySend(Result(if (ok) Stage.SENT else Stage.SENT_FAILED, if (ok) android.app.Activity.RESULT_OK else failedCode, accepted, parts.size))
                    close()
                }
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT))
        }
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        for (i in parts.indices) {
            sentIntents.add(PendingIntent.getBroadcast(context, i, Intent(ACTION_SMS_SENT).setPackage(context.packageName), flags))
        }
        Log.i(TAG, "Sending ${parts.size} part(s) to $destination")
        try {
            smsManager.sendMultipartTextMessage(destination, null, parts, sentIntents, null)
        } catch (e: Exception) {
            Log.w(TAG, "sendMultipartTextMessage threw: ${e.message}")
            trySend(Result(Stage.SENT_FAILED, -1, 0, parts.size))
            close()
        }

        awaitClose {
            try { context.unregisterReceiver(sentReceiver) } catch (_: Exception) {}
        }
    }
}
