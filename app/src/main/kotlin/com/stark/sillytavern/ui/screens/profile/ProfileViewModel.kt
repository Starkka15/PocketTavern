package com.stark.sillytavern.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SettingsRepository
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.domain.model.UserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userInfo: UserInfo? = null,
    val isLoading: Boolean = false,
    val showPasswordDialog: Boolean = false,
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isChangingPassword: Boolean = false,
    val passwordChangeSuccess: Boolean = false,
    val logoutSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: SillyTavernRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
    }

    fun loadUserInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getCurrentUser()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            userInfo = result.data,
                            isLoading = false
                        )
                    }
                }
                is Result.Error -> {
                    // If we can't get user info from server, use local settings
                    val settings = settingsRepository.getSettings()
                    _uiState.update {
                        it.copy(
                            userInfo = UserInfo(
                                handle = settings.username,
                                name = settings.username,
                                avatar = null,
                                isAdmin = false,
                                hasPassword = settings.password.isNotBlank(),
                                created = null
                            ),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun showPasswordDialog() {
        _uiState.update {
            it.copy(
                showPasswordDialog = true,
                oldPassword = "",
                newPassword = "",
                confirmPassword = "",
                error = null
            )
        }
    }

    fun hidePasswordDialog() {
        _uiState.update {
            it.copy(
                showPasswordDialog = false,
                oldPassword = "",
                newPassword = "",
                confirmPassword = ""
            )
        }
    }

    fun updateOldPassword(value: String) {
        _uiState.update { it.copy(oldPassword = value) }
    }

    fun updateNewPassword(value: String) {
        _uiState.update { it.copy(newPassword = value) }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value) }
    }

    fun changePassword() {
        val state = _uiState.value

        // Validate passwords
        if (state.newPassword.isBlank()) {
            _uiState.update { it.copy(error = "New password cannot be empty") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(error = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, error = null) }

            val oldPwd = state.oldPassword.ifBlank { null }
            val newPwd = state.newPassword.ifBlank { null }

            when (val result = repository.changePassword(oldPwd, newPwd)) {
                is Result.Success -> {
                    // Update local password storage if we have new password
                    if (newPwd != null) {
                        val settings = settingsRepository.getSettings()
                        settingsRepository.saveSettings(settings.copy(password = newPwd))
                    }
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            showPasswordDialog = false,
                            passwordChangeSuccess = true
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.logout()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    logoutSuccess = true
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetPasswordChangeSuccess() {
        _uiState.update { it.copy(passwordChangeSuccess = false) }
    }

    fun resetLogoutSuccess() {
        _uiState.update { it.copy(logoutSuccess = false) }
    }
}
