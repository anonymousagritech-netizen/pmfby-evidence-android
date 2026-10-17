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

    fun saveSession(token: String, userId: String, roleKey: String, name: String, scopeStateId: String?, scopeDistrictId: String?) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ROLE_KEY, roleKey)
            .putString(KEY_NAME, name)
            .putString(KEY_SCOPE_STATE, scopeStateId)
            .putString(KEY_SCOPE_DISTRICT, scopeDistrictId)
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

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ROLE_KEY = "role_key"
        private const val KEY_NAME = "name"
        private const val KEY_SCOPE_STATE = "scope_state_id"
        private const val KEY_SCOPE_DISTRICT = "scope_district_id"
    }
}
