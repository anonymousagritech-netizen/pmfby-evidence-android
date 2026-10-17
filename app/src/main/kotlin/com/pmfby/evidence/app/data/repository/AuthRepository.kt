package com.pmfby.evidence.app.data.repository

import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.session.SessionManager

class AuthRepository(
    private val api: ApiService,
    private val session: SessionManager,
) {
    /** Returns null on success, or a human-readable error message on failure. */
    suspend fun login(identifier: String, password: String): String? {
        return try {
            // "identifier" accepted by the backend as either email or mobile number
            val response = api.login(mapOf("identifier" to identifier, "password" to password))
            val token = response["token"] as? String
                ?: return "Server did not return a session token"
            @Suppress("UNCHECKED_CAST")
            val user = response["user"] as? Map<String, Any?> ?: emptyMap()
            session.saveSession(
                token = token,
                userId = user["id"]?.toString().orEmpty(),
                roleKey = user["roleKey"]?.toString().orEmpty(),
                name = user["name"]?.toString().orEmpty(),
                scopeStateId = user["scopeStateId"]?.toString(),
                scopeDistrictId = user["scopeDistrictId"]?.toString(),
                // Drives AppNavigation's mandatory "Set your password" screen — true for
                // every bulk-imported surveyor until they actually set their own
                // password (see surveyorImportService.js / change-password below).
                forcePasswordChange = user["forcePasswordChange"] as? Boolean ?: false,
            )
            null
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) "Invalid email/mobile or password" else "Server error (HTTP ${e.code()})"
        } catch (e: java.io.IOException) {
            "Could not reach the server — check the API base URL and your connection"
        }
    }

    /**
     * The only way a bulk-imported surveyor (system-generated temp password, never
     * chosen by them) can set their own credential. Mirrors admin-web's
     * ChangePasswordPage against the same POST /api/auth/change-password endpoint.
     * Returns null on success, or a human-readable error message on failure.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): String? {
        val auth = session.authHeader() ?: return "Not signed in"
        return try {
            api.changePassword(auth, mapOf("currentPassword" to currentPassword, "newPassword" to newPassword))
            session.setForcePasswordChange(false)
            null
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> "Current password is incorrect"
                400 -> "New password must be at least 8 characters"
                else -> "Server error (HTTP ${e.code()})"
            }
        } catch (e: java.io.IOException) {
            "Could not reach the server — check your connection"
        }
    }

    fun logout() = session.clearSession()
}
