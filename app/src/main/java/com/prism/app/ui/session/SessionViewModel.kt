package com.prism.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.prism.app.data.model.UserProfile
import com.prism.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SessionState(
    val loading: Boolean = true,
    val user: FirebaseUser? = null,
    val profile: UserProfile? = null
)

class SessionViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.authState().collectLatest { user ->
                if (user == null) {
                    _state.value = SessionState(loading = false, user = null, profile = null)
                } else {
                    _state.value = _state.value.copy(loading = true, user = user)
                    authRepository.observeProfile(user.uid).collectLatest { profile ->
                        _state.value = SessionState(
                            loading = false,
                            user = user,
                            profile = profile
                        )
                    }
                }
            }
        }
    }

    fun signOut() = authRepository.signOut()

    fun refreshProfile() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            val profile = authRepository.getProfile(uid)
            _state.value = _state.value.copy(profile = profile, loading = false)
        }
    }
}
