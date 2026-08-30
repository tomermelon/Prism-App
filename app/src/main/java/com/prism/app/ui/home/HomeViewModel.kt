package com.prism.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prism.app.data.model.Article
import com.prism.app.data.repository.ArticleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class HomeUiState(
    val articles: List<Article> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

/**
 * Same loading pattern as Interests: snapshot listener only.
 * Avoids Firestore `.get().await()`, which can hang forever and leave Home on a spinner.
 */
class HomeViewModel(
    private val articleRepository: ArticleRepository = ArticleRepository()
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Independent of Firestore — clears spinner even if the listener never fires.
            launch {
                delay(10_000)
                if (_state.value.loading) {
                    _state.value = HomeUiState(
                        articles = emptyList(),
                        loading = false,
                        error = "Home timed out loading stories."
                    )
                }
            }

            articleRepository.observeLatest(limit = 50).collectLatest { articles ->
                _state.value = HomeUiState(
                    articles = articles,
                    loading = false,
                    error = null
                )
            }
        }
    }
}
