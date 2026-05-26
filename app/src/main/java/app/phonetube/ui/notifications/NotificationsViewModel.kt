package app.phonetube.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.MediaErrors
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.VideoItemMapper
import app.phonetube.core.media.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val needsSignIn: Boolean = false
)

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository(application)
    private val _state = MutableStateFlow(NotificationsUiState(isLoading = true))
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = _state.value.videos.isEmpty(),
                isLoadingMore = false,
                canLoadMore = false,
                error = null
            )
            try {
                val page = repository.loadNotificationsFeed()
                _state.value = NotificationsUiState(
                    videos = page.videos,
                    isLoading = false,
                    canLoadMore = page.canLoadMore
                )
            } catch (e: NotSignedInException) {
                _state.value = NotificationsUiState(needsSignIn = true, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val page = repository.loadNotificationsFeed()
                _state.value = NotificationsUiState(
                    videos = page.videos,
                    isLoading = false,
                    isRefreshing = false,
                    canLoadMore = page.canLoadMore
                )
            } catch (e: NotSignedInException) {
                _state.value = NotificationsUiState(needsSignIn = true, isRefreshing = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.canLoadMore) return
        viewModelScope.launch {
            _state.value = current.copy(isLoadingMore = true, error = null)
            try {
                val page = repository.loadNotificationsFeedMore()
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
