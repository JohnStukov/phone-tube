package app.phonetube.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.MediaErrors
import app.phonetube.core.media.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val results: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSearchingSuggestions: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val showingCachedData: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    private val repository: YouTubeRepository
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var suggestionsJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                repository.recentSearchQueries()
            }.onSuccess { recent ->
                _state.value = _state.value.copy(recentQueries = recent)
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(
            query = query,
            error = null,
            hasSearched = false,
            results = emptyList()
        )
        suggestionsJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(suggestions = emptyList(), isSearchingSuggestions = false)
            return
        }
        suggestionsJob = viewModelScope.launch {
            _state.value = _state.value.copy(isSearchingSuggestions = true)
            delay(300)
            try {
                val tags = repository.searchSuggestions(query)
                _state.value = _state.value.copy(
                    suggestions = tags,
                    isSearchingSuggestions = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    suggestions = emptyList(),
                    isSearchingSuggestions = false
                )
            }
        }
    }

    fun search(query: String = _state.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                query = trimmed,
                isLoading = true,
                error = null,
                hasSearched = true,
                suggestions = emptyList()
            )
            try {
                val result = repository.searchVideos(trimmed)
                val recent = runCatching { repository.recentSearchQueries() }.getOrDefault(_state.value.recentQueries)
                _state.value = _state.value.copy(
                    results = result.items,
                    isLoading = false,
                    showingCachedData = result.isFromCache,
                    recentQueries = recent
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }
}
