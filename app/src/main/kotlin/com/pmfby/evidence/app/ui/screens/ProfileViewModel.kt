package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import com.pmfby.evidence.app.data.repository.AuthRepository
import com.pmfby.evidence.app.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProfileUiState(
    val name: String = "",
    val roleKey: String = "",
    val scopeStateId: String? = null,
    val scopeDistrictId: String? = null,
    val preferInstantCall: Boolean = false,
    val loggedOut: Boolean = false,
)

class ProfileViewModel(private val authRepository: AuthRepository, private val session: SessionManager) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            name = session.name().orEmpty(),
            roleKey = session.roleKey().orEmpty(),
            scopeStateId = session.scopeStateId(),
            scopeDistrictId = session.scopeDistrictId(),
            preferInstantCall = session.preferInstantCall(),
        ),
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun setPreferInstantCall(value: Boolean) {
        session.setPreferInstantCall(value)
        _uiState.update { it.copy(preferInstantCall = value) }
    }

    fun logout() {
        authRepository.logout()
        _uiState.update { it.copy(loggedOut = true) }
    }
}
