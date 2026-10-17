package com.pmfby.evidence.app.data.repository

import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.session.SessionManager

class AuthRepository(
    private val api: ApiService,
    private val session: SessionManager,
) {
    /** Returns null on success, or a human-readable error message on failure. */
    suspend fun login(email: String, password: String): String? {
        return try {
            val response = api.login(mapOf("email" to email, "password" to password))
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
            )
            null
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) "Invalid email or password" else "Server error (HTTP ${e.code()})"
        } catch (e: java.io.IOException) {
            "Could not reach the server — check the API base URL and your connection"
        }
    }

    fun logout() = session.clearSession()
}
