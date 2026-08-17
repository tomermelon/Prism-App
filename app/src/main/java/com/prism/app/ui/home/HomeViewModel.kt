package com.prism.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.app.data.model.Article
import com.prism.app.data.model.NewsScope
import com.prism.app.data.model.UserProfile
import com.prism.app.data.repository.ArticleRepository
import com.prism.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HomeUiState(
    val local: List<Article> = emptyList(),
    val national: List<Article> = emptyList(),
    val international: List<Article> = emptyList(),
    val showLocal: Boolean = true,
    val showNational: Boolean = true,
    val showInternational: Boolean = true,
    val loading: Boolean = true
)

class HomeViewModel(
    private val articleRepository: ArticleRepository = ArticleRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun bindProfile(profile: UserProfile?) {
        if (profile == null) return
        _state.value = _state.value.copy(
            showLocal = profile.showLocal,
            showNational = profile.showNational,
            showInternational = profile.showInternational
        )
    }

    init {
        observeScope(NewsScope.LOCAL) { copy(local = it, loading = false) }
        observeScope(NewsScope.NATIONAL) { copy(national = it, loading = false) }
        observeScope(NewsScope.INTERNATIONAL) { copy(international = it, loading = false) }
    }

    private fun observeScope(
        scope: NewsScope,
        update: HomeUiState.(List<Article>) -> HomeUiState
    ) {
        viewModelScope.launch {
            articleRepository.observeByScope(scope.firestoreValue).collectLatest { articles ->
                _state.value = _state.value.update(articles)
            }
        }
    }

    fun setSectionVisible(scope: NewsScope, visible: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        val current = _state.value
        val next = when (scope) {
            NewsScope.LOCAL -> current.copy(showLocal = visible)
            NewsScope.NATIONAL -> current.copy(showNational = visible)
            NewsScope.INTERNATIONAL -> current.copy(showInternational = visible)
        }
        _state.value = next
        viewModelScope.launch {
            authRepository.updateSectionToggles(
                uid = uid,
                showLocal = next.showLocal,
                showNational = next.showNational,
                showInternational = next.showInternational
            )
        }
    }
}
