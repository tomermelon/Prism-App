package com.prism.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.app.data.repository.AuthRepository
import com.prism.app.data.model.CountryCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val country: String = "",
    val isSignIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun updateUsername(value: String) = update { copy(username = value, error = null) }
    fun updateEmail(value: String) = update { copy(email = value, error = null) }
    fun updatePassword(value: String) = update { copy(password = value, error = null) }
    fun updateCountry(value: String) = update { copy(country = value, error = null) }
    fun toggleMode() = update { copy(isSignIn = !isSignIn, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.isSignIn) {
            if (current.country.isBlank() || current.country !in CountryCatalog.all) {
                _state.value = current.copy(error = "Please select a country from the list")
                return
            }
        }
        viewModelScope.launch {
            _state.value = current.copy(loading = true, error = null)
            val result = if (current.isSignIn) {
                authRepository.signIn(current.email, current.password)
            } else {
                authRepository.signUp(
                    username = current.username,
                    email = current.email,
                    password = current.password,
                    country = current.country
                )
            }
            _state.value = result.fold(
                onSuccess = { current.copy(loading = false, success = true, error = null) },
                onFailure = {
                    current.copy(
                        loading = false,
                        error = it.message ?: "Something went wrong"
                    )
                }
            )
        }
    }

    private fun update(block: AuthUiState.() -> AuthUiState) {
        _state.value = _state.value.block()
    }
}
