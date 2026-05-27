package app.phonetube.ui.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.ChannelDetails
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

data class ChannelUiState(
    val channel: ChannelDetails? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null
) {
    val canLoadMore: Boolean
        get() = channel?.canLoadMore == true && !isLoading && !isLoadingMore && !isRefreshing
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    application: Application,
    private val repository: YouTubeRepository
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    private var loadedChannelId: String? = null
    private var loadedFallbackName: String? = null

    fun load(channelId: String, fallbackName: String?) {
        loadedChannelId = channelId
        loadedFallbackName = fallbackName
        reloadChannel(tabId = null, sortId = null, isRefresh = false)
    }

    fun refresh() {
        val channel = _state.value.channel ?: return
        reloadChannel(
            tabId = channel.selectedTabId,
            sortId = channel.selectedSortId,
            isRefresh = true
        )
    }

    fun selectTab(tabId: String) {
        val channel = _state.value.channel ?: return
        reloadChannel(tabId = tabId, sortId = channel.selectedSortId, isRefresh = false)
    }

    fun selectSort(sortId: String) {
        val channel = _state.value.channel ?: return
        reloadChannel(tabId = channel.selectedTabId, sortId = sortId, isRefresh = false)
    }

    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        val channel = current.channel ?: return
        viewModelScope.launch {
            _state.value = current.copy(isLoadingMore = true, error = null)
            try {
                val more = repository.loadMoreChannelVideos()
                val merged = VideoItemMapper.merge(channel.videos, more)
                _state.value = current.copy(
                    channel = channel.copy(
                        videos = merged,
                        canLoadMore = repository.channelCanLoadMore()
                    ),
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                _state.value = current.copy(
                    isLoadingMore = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun openItem(video: VideoItem, onOpenVideo: (videoId: String, isLive: Boolean) -> Unit) {
        if (!video.isPlaylist) {
            onOpenVideo(video.videoId, video.isLive)
            return
        }
        val playlistId = video.playlistId?.trim().orEmpty().ifEmpty { video.videoId }
        viewModelScope.launch {
            try {
                val startVideoId = repository.resolvePlaylistStartVideoId(playlistId)
                if (startVideoId != null) {
                    onOpenVideo(startVideoId, false)
                } else {
                    _state.value = _state.value.copy(actionMessage = MediaErrors.PLAYLIST_UNAVAILABLE)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionMessage = MediaErrors.codeFor(e))
            }
        }
    }

    private fun reloadChannel(tabId: String?, sortId: String?, isRefresh: Boolean) {
        val channelId = loadedChannelId ?: return
        val previous = _state.value.channel
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = !isRefresh && previous == null,
                isRefreshing = isRefresh,
                error = null,
                channel = if (isRefresh) previous else previous?.copy(videos = emptyList())
            )
            try {
                val channel = repository.loadChannel(
                    channelId,
                    loadedFallbackName,
                    tabId ?: previous?.selectedTabId,
                    sortId ?: previous?.selectedSortId
                )
                _state.value = ChannelUiState(channel = channel, isLoading = false, isRefreshing = false)
            } catch (e: Exception) {
                _state.value = ChannelUiState(
                    channel = previous,
                    isLoading = false,
                    isRefreshing = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun toggleSubscribe() {
        val channel = _state.value.channel ?: return
        val channelId = channel.channelId
        viewModelScope.launch {
            try {
                if (channel.isSubscribed) {
                    repository.unsubscribe(channelId)
                } else {
                    repository.subscribe(channelId)
                }
                val updated = repository.loadChannel(
                    channelId,
                    channel.name,
                    channel.selectedTabId,
                    channel.selectedSortId
                )
                _state.value = _state.value.copy(channel = updated)
            } catch (e: NotSignedInException) {
                _state.value = _state.value.copy(actionMessage = MediaErrors.SIGN_IN)
            } catch (e: Exception) {
                _state.value = _state.value.copy(actionMessage = MediaErrors.codeFor(e))
            }
        }
    }

    fun clearActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }
}
