package com.pockettavern.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.UserInfo
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
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
    }

    fun loadUserInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = localRepository.getUserPersona()) {
                is Result.Success -> {
                    val persona = result.data
                    _uiState.update {
                        it.copy(
                            userInfo = UserInfo(
                                handle = persona.name,
                                name = persona.name,
                                avatar = null,
                                isAdmin = false,
                                hasPassword = false,
                                created = null
                            ),
                            isLoading = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun showPasswordDialog() {
        _uiState.update {
            it.copy(error = "Password management is not available in standalone mode")
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
        _uiState.update { it.copy(error = "Password management is not available in standalone mode") }
    }

    fun logout() {
        // No server to log out from in standalone mode
        _uiState.update { it.copy(logoutSuccess = true) }
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
