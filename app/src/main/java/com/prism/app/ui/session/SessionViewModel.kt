package com.prism.app.ui.session

import android.util.Log
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
import kotlinx.coroutines.withTimeoutOrNull

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
        Log.i(TAG, "SessionViewModel init")
        viewModelScope.launch {
            authRepository.authState().collectLatest { user ->
                Log.i(TAG, "authState user=${user?.uid ?: "null"}")
                if (user == null) {
                    _state.value = SessionState(loading = false, user = null, profile = null)
                    return@collectLatest
                }

                // Show spinner only briefly; never block the app if Firestore hangs.
                _state.value = _state.value.copy(loading = true, user = user)

                val profile = withTimeoutOrNull(10_000L) {
                    runCatching { authRepository.getProfile(user.uid) }
                        .onFailure { Log.e(TAG, "getProfile failed", it) }
                        .getOrNull()
                }
                Log.i(
                    TAG,
                    "initial profile loaded=${profile != null} " +
                        "onboarding=${profile?.onboardingComplete}"
                )
                _state.value = SessionState(
                    loading = false,
                    user = user,
                    profile = profile
                )

                // Live updates after UI is unblocked.
                authRepository.observeProfile(user.uid).collectLatest { liveProfile ->
                    Log.i(TAG, "observeProfile update loaded=${liveProfile != null}")
                    _state.value = SessionState(
                        loading = false,
                        user = user,
                        profile = liveProfile
                    )
                }
            }
        }
    }

    fun signOut() = authRepository.signOut()

    fun refreshProfile() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            val profile = runCatching { authRepository.getProfile(uid) }.getOrNull()
            _state.value = _state.value.copy(profile = profile, loading = false)
        }
    }

    companion object {
        private const val TAG = "SessionViewModel"
    }
}
