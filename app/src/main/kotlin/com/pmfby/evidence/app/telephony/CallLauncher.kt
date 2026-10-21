package com.pmfby.evidence.app.telephony

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * Two calling paths, exactly as specced:
 *
 * - [dial] (default, always available): ACTION_DIAL opens the phone's own dialer
 *   pre-filled with the number. Requires NO permission. The user still has to tap the
 *   call button themselves — a deliberate cancel point before anything happens, and
 *   Play Store review treats it as a routine, unprotected intent.
 * - [callDirectly] (opt-in, behind the CALL_PHONE runtime permission): ACTION_CALL
 *   places the call immediately with no extra tap. Faster for the surveyor, but
 *   CALL_PHONE is a "dangerous" permission Play Store reviews more strictly, and skips
 *   the user's own last-second cancel opportunity. Only used if the user has explicitly
 *   granted CALL_PHONE and chosen this path in Profile/settings.
 *
 * Neither path gives the app any callback about call duration or connection state — see
 * CallStateObserver for the best-effort alternative, and README for why outcome
 * recording is always `user_entered`, never verified.
 */
object CallLauncher {

    fun dial(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
        context.startActivity(intent)
    }

    /** Places the call immediately when CALL_PHONE is granted; otherwise falls back to the dialer. */
    fun place(context: Context, phoneNumber: String) {
        if (hasCallPermission(context)) callDirectly(context, phoneNumber) else dial(context, phoneNumber)
    }

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /**
     * Caller MUST have already confirmed [hasCallPermission]. Goes straight to the telephony
     * service, so no app (including this one) can intercept the call with a chooser.
     */
    fun callDirectly(context: Context, phoneNumber: String) {
        require(hasCallPermission(context)) { "CALL_PHONE permission not granted; use dial() instead" }
        val telecom = context.getSystemService(android.telecom.TelecomManager::class.java)
        try {
            telecom.placeCall(Uri.fromParts("tel", phoneNumber, null), android.os.Bundle())
        } catch (_: SecurityException) {
            dial(context, phoneNumber)
        }
    }
}
