package com.pmfby.evidence.app.telephony

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * Best-effort call-state observation via TelephonyManager. Documented honestly, per the
 * task's explicit instruction: this is NOT reliable. Modern Android increasingly
 * restricts what READ_PHONE_STATE exposes to third-party (non-default-dialer) apps,
 * behavior varies by OEM/Android version, and on many devices/versions this will simply
 * never fire for a call placed via ACTION_DIAL/ACTION_CALL. The app's outcome recording
 * NEVER depends on this firing — call outcome is always surveyor-entered
 * (outcome_source = 'user_entered' server-side, see backend/src/routes/callEvents.js).
 * This exists only as an optional "looks like the call connected" hint, never a source
 * of truth.
 */
class CallStateObserver(private val context: Context) {

    enum class ObservedState { IDLE, RINGING, OFFHOOK, UNKNOWN }

    private var callback: TelephonyCallback? = null
    private var legacyListener: android.telephony.PhoneStateListener? = null

    fun hasReadPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /**
     * Registers a best-effort listener. No-ops silently (rather than crashing) if the
     * permission is missing or the OS/OEM simply doesn't deliver call-state callbacks to
     * this app — see class doc.
     */
    fun start(executor: Executor, onStateChanged: (ObservedState) -> Unit) {
        if (!hasReadPhoneStatePermission()) return
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    onStateChanged(mapState(state))
                }
            }
            callback = cb
            try {
                telephonyManager.registerTelephonyCallback(executor, cb)
            } catch (e: SecurityException) {
                // OEM/Android-version restriction — fall through silently, see class doc.
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : android.telephony.PhoneStateListener() {
                @Deprecated("Deprecated in API 31; kept for minSdk 26 compatibility")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    onStateChanged(mapState(state))
                }
            }
            legacyListener = listener
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: SecurityException) {
                // Same best-effort caveat as above.
            }
        }
    }

    fun stop() {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        callback?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) telephonyManager?.unregisterTelephonyCallback(it)
        }
        legacyListener?.let {
            @Suppress("DEPRECATION")
            telephonyManager?.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
        }
        callback = null
        legacyListener = null
    }

    private fun mapState(state: Int): ObservedState = when (state) {
        TelephonyManager.CALL_STATE_IDLE -> ObservedState.IDLE
        TelephonyManager.CALL_STATE_RINGING -> ObservedState.RINGING
        TelephonyManager.CALL_STATE_OFFHOOK -> ObservedState.OFFHOOK
        else -> ObservedState.UNKNOWN
    }
}
