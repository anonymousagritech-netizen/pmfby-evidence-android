package com.pmfby.evidence.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.pmfby.evidence.app.data.local.AppDatabase
import com.pmfby.evidence.app.navigation.Routes
import kotlinx.coroutines.launch

/**
 * Transparent trampoline registered for tel: and smsto:/sms: schemes so the app appears in
 * Android's "Open with" chooser alongside Truecaller, Messages, etc.
 *
 * On launch it looks up the phone number in the local Room cache, finds the matching farmer
 * and their primary case, then fires MainActivity with EXTRA_START_ROUTE pointing directly at
 * CallScreen (for tel:) or SmsTemplateScreen (for smsto:/sms:).  If the number isn't in
 * the cache (no assigned farmer), it falls through to the system default silently.
 */
class PhoneIntentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data ?: run { finish(); return }
        val rawPhone = uri.schemeSpecificPart?.trimStart('/') ?: run { finish(); return }
        // Normalize: strip non-digits, keep last 10 (handles +91XXXXXXXXXX and 0XXXXXXXXXX)
        val phone = rawPhone.filter { it.isDigit() }.takeLast(10)
        val isCall = intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_CALL

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val farmer = db.farmerDao().getByMobile(phone)

            if (farmer != null) {
                val case = db.surveyCaseDao().getFirstForFarmer(farmer.id)
                if (case != null) {
                    val route = if (isCall) Routes.call(case.id) else Routes.sms(case.id)
                    startActivity(
                        Intent(this@PhoneIntentActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra(EXTRA_START_ROUTE, route)
                        },
                    )
                    finish()
                    return@launch
                }
            }

            // Phone not matched — pass through to whatever the user normally uses
            try {
                startActivity(
                    Intent(intent.action, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // Exclude ourselves so we don't appear in the chooser again
                        `package` = null
                    },
                )
            } catch (_: Exception) { /* nothing we can do */ }
            finish()
        }
    }

    companion object {
        const val EXTRA_START_ROUTE = "pmfby_start_route"
    }
}
