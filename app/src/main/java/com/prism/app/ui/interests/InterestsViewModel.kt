package com.prism.app.ui.interests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.app.data.model.Article
import com.prism.app.data.repository.ArticleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class InterestsUiState(
    val articles: List<Article> = emptyList(),
    val loading: Boolean = true,
    val hasInterests: Boolean = false
)

class InterestsViewModel(
    private val articleRepository: ArticleRepository = ArticleRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(InterestsUiState())
    val state: StateFlow<InterestsUiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private val listsByInterest = mutableMapOf<String, List<Article>>()

    fun bindInterests(interests: List<String>) {
        observeJob?.cancel()
        listsByInterest.clear()

        if (interests.isEmpty()) {
            _state.value = InterestsUiState(
                articles = emptyList(),
                loading = false,
                hasInterests = false
            )
            return
        }

        _state.value = InterestsUiState(
            articles = emptyList(),
            loading = true,
            hasInterests = true
        )

        observeJob = viewModelScope.launch {
            interests.forEach { interest ->
                launch {
                    articleRepository.observeByInterest(interest).collectLatest { list ->
                        listsByInterest[interest] = list
                        publishMerged()
                    }
                }
            }
        }
    }

    private fun publishMerged() {
        val merged = listsByInterest.values
            .flatten()
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
        _state.value = InterestsUiState(
            articles = merged,
            loading = false,
            hasInterests = true
        )
    }
}
