package com.pmfby.evidence.app.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the JWT and the signed-in user's own role/scope fields (the same shape
 * returned by POST /api/auth/login — see backend-and-web/backend/src/routes/auth.js).
 * Backed by EncryptedSharedPreferences so the token at rest on disk is encrypted, not a
 * plaintext session.
 */
class SessionManager(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pmfby_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveSession(
        token: String,
        userId: String,
        roleKey: String,
        name: String,
        scopeStateId: String?,
        scopeDistrictId: String?,
        forcePasswordChange: Boolean = false,
    ) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ROLE_KEY, roleKey)
            .putString(KEY_NAME, name)
            .putString(KEY_SCOPE_STATE, scopeStateId)
            .putString(KEY_SCOPE_DISTRICT, scopeDistrictId)
            .putBoolean(KEY_FORCE_PASSWORD_CHANGE, forcePasswordChange)
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)
    fun userId(): String? = prefs.getString(KEY_USER_ID, null)
    fun roleKey(): String? = prefs.getString(KEY_ROLE_KEY, null)
    fun name(): String? = prefs.getString(KEY_NAME, null)
    fun scopeStateId(): String? = prefs.getString(KEY_SCOPE_STATE, null)
    fun scopeDistrictId(): String? = prefs.getString(KEY_SCOPE_DISTRICT, null)
    fun isSignedIn(): Boolean = token() != null

    fun authHeader(): String? = token()?.let { "Bearer $it" }

    /**
     * Mirrors the backend's `users.force_password_change` (see migration
     * 1700000011000_surveyor_bulk_import.js) — true for any account whose password was
     * system-generated rather than chosen by the user (currently: every bulk-imported
     * surveyor, see surveyorImportService.js). AppNavigation routes here before the
     * main app is reachable at all; cleared locally the moment
     * AuthRepository.changePassword succeeds against the real backend.
     */
    fun forcePasswordChange(): Boolean = prefs.getBoolean(KEY_FORCE_PASSWORD_CHANGE, false)
    fun setForcePasswordChange(value: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_PASSWORD_CHANGE, value).apply()
    }

    /**
     * User-facing opt-in to the ACTION_CALL instant-call path (CallLauncher.callDirectly)
     * instead of the default ACTION_DIAL. Stored here (not just "has the permission") so
     * granting CALL_PHONE once doesn't silently switch calling behavior without the
     * surveyor explicitly choosing it in Profile — see CallLauncher's class doc.
     */
    fun preferInstantCall(): Boolean = prefs.getBoolean(KEY_PREFER_INSTANT_CALL, false)
    fun setPreferInstantCall(value: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_INSTANT_CALL, value).apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ROLE_KEY = "role_key"
        private const val KEY_NAME = "name"
        private const val KEY_SCOPE_STATE = "scope_state_id"
        private const val KEY_SCOPE_DISTRICT = "scope_district_id"
        private const val KEY_PREFER_INSTANT_CALL = "prefer_instant_call"
        private const val KEY_FORCE_PASSWORD_CHANGE = "force_password_change"
    }
}
