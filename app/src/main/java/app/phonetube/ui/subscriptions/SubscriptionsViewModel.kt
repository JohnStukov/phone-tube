package app.phonetube.ui.subscriptions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.VideoItemMapper
import app.phonetube.core.media.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscriptionsUiState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val needsSignIn: Boolean = false
)

class SubscriptionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository(application)
    private val authRepository = AuthRepository.get(application)
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
                isLoading = true,
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
                    canLoadMore = page.canLoadMore
                )
            } catch (e: NotSignedInException) {
                _state.value = SubscriptionsUiState(needsSignIn = true)
            } catch (e: Exception) {
                _state.value = SubscriptionsUiState(
                    isLoading = false,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    fun loadMore() {
        if (!authRepository.isSignedIn()) return
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return
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
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }
}
