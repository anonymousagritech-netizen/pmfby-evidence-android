package com.pmfby.evidence.app.telephony

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
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

    data class Result(val stage: Stage, val resultCode: Int)

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
    private const val ACTION_SMS_DELIVERED = "com.pmfby.evidence.app.SMS_DELIVERED"

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

    fun send(context: Context, phoneNumber: String, body: String, deliveryTimeoutMs: Long = 20_000) = callbackFlow {
        // Use the subscription-ID-specific SmsManager so MIUI routes the message as SMS
        // (not RCS) regardless of what the default Messages app defaults to.
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
        val sentIntents = arrayListOf<PendingIntent>()
        val deliveredIntents = arrayListOf<PendingIntent>()
        var sentCount = 0
        var sentFailed = false
        var deliveredCount = 0

        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                sentCount++
                if (resultCode != android.app.Activity.RESULT_OK) sentFailed = true
                if (sentCount >= parts.size) {
                    trySend(Result(if (sentFailed) Stage.SENT_FAILED else Stage.SENT, resultCode))
                    if (sentFailed) close()
                }
            }
        }
        val deliveredReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                deliveredCount++
                if (resultCode == android.app.Activity.RESULT_OK) trySend(Result(Stage.DELIVERED, resultCode))
                else trySend(Result(Stage.DELIVERY_FAILED, resultCode))
                if (deliveredCount >= parts.size) close()
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT), Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(deliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") context.registerReceiver(sentReceiver, IntentFilter(ACTION_SMS_SENT))
            @Suppress("DEPRECATION") context.registerReceiver(deliveredReceiver, IntentFilter(ACTION_SMS_DELIVERED))
        }

        for (i in parts.indices) {
            sentIntents.add(PendingIntent.getBroadcast(context, i, Intent(ACTION_SMS_SENT).setPackage(context.packageName), flags))
            deliveredIntents.add(PendingIntent.getBroadcast(context, i + 1000, Intent(ACTION_SMS_DELIVERED).setPackage(context.packageName), flags))
        }
        try {
            smsManager.sendMultipartTextMessage(destination, null, parts, sentIntents, deliveredIntents)
        } catch (e: Exception) {
            trySend(Result(Stage.SENT_FAILED, -1))
            close()
        }

        awaitClose {
            try { context.unregisterReceiver(sentReceiver) } catch (_: Exception) {}
            try { context.unregisterReceiver(deliveredReceiver) } catch (_: Exception) {}
        }
    }
}
