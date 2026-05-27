package app.phonetube.ui.subscriptions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.MediaErrors
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.VideoItemMapper
import app.phonetube.core.media.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscriptionsUiState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val showingCachedData: Boolean = false,
    val needsSignIn: Boolean = false
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    application: Application,
    private val repository: YouTubeRepository,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SubscriptionsUiState())
    val state: StateFlow<SubscriptionsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.state.collect {
                load()
            }
        }
    }

    fun load() {
        if (!authRepository.isSignedIn()) {
            _state.value = SubscriptionsUiState(needsSignIn = true)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = _state.value.videos.isEmpty(),
                isLoadingMore = false,
                canLoadMore = false,
                error = null,
                needsSignIn = false
            )
            try {
                val page = repository.loadSubscriptionsFeed()
                _state.value = SubscriptionsUiState(
                    videos = page.videos,
                    isLoading = false,
                    canLoadMore = page.canLoadMore,
                    showingCachedData = page.isFromCache
                )
            } catch (e: NotSignedInException) {
                _state.value = SubscriptionsUiState(needsSignIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun refresh() {
        if (!authRepository.isSignedIn()) return
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val page = repository.loadSubscriptionsFeed()
                _state.value = _state.value.copy(
                    videos = page.videos,
                    isRefreshing = false,
                    canLoadMore = page.canLoadMore,
                    showingCachedData = page.isFromCache
                )
            } catch (e: NotSignedInException) {
                _state.value = SubscriptionsUiState(needsSignIn = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun loadMore() {
        if (!authRepository.isSignedIn()) return
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.isRefreshing || !current.canLoadMore) return
        viewModelScope.launch {
            _state.value = current.copy(isLoadingMore = true, error = null)
            try {
                val page = repository.loadSubscriptionsFeedMore()
                _state.value = current.copy(
                    videos = VideoItemMapper.merge(current.videos, page.videos),
                    isLoadingMore = false,
                    canLoadMore = page.canLoadMore
                )
            } catch (e: Exception) {
                _state.value = current.copy(
                    isLoadingMore = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }
}
