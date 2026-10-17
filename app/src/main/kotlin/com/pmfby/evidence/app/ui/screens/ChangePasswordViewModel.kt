package com.pmfby.evidence.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

/** Backs ChangePasswordScreen — the mandatory first-login screen for any account whose
 * password was system-generated (see AuthRepository.changePassword). */
class ChangePasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrentPasswordChange(value: String) = _uiState.update { it.copy(currentPassword = value, error = null) }
    fun onNewPasswordChange(value: String) = _uiState.update { it.copy(newPassword = value, error = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, error = null) }

    fun submit() {
        val state = _uiState.value
        if (state.currentPassword.isBlank() || state.newPassword.isBlank()) {
            _uiState.update { it.copy(error = "Both fields are required") }
            return
        }
        if (state.newPassword.length < 8) {
            _uiState.update { it.copy(error = "New password must be at least 8 characters") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(error = "New password and confirmation do not match") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val error = authRepository.changePassword(state.currentPassword, state.newPassword)
            _uiState.update { it.copy(isLoading = false, error = error, done = error == null) }
        }
    }
}
