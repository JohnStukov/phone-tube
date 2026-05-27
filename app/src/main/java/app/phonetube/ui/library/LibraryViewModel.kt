package app.phonetube.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.AuthState
import app.phonetube.core.media.CachedListResult
import app.phonetube.core.media.MediaErrors
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val auth: AuthState = AuthState(),
    val historyVideos: List<VideoItem> = emptyList(),
    val watchLaterVideos: List<VideoItem> = emptyList(),
    val likedVideos: List<VideoItem> = emptyList(),
    val playlistVideos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showingCachedData: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val repository: YouTubeRepository,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(LibraryUiState(auth = authRepository.state.value))
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.state.collect { auth ->
                _state.value = _state.value.copy(auth = auth)
                if (auth.isSignedIn) {
                    loadLibrary()
                } else {
                    _state.value = _state.value.copy(
                        historyVideos = emptyList(),
                        watchLaterVideos = emptyList(),
                        likedVideos = emptyList(),
                        playlistVideos = emptyList(),
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    fun loadLibrary() {
        if (!authRepository.isSignedIn()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = _state.value.historyVideos.isEmpty() &&
                    _state.value.watchLaterVideos.isEmpty() &&
                    _state.value.likedVideos.isEmpty() &&
                    _state.value.playlistVideos.isEmpty(),
                error = null
            )
            try {
                val history = repository.loadHistoryVideos()
                val watchLater = runCatching { repository.loadWatchLaterVideos() }
                    .getOrElse { CachedListResult(emptyList()) }
                val liked = runCatching { repository.loadLikedVideos() }
                    .getOrElse { CachedListResult(emptyList()) }
                val playlists = runCatching { repository.loadUserPlaylists() }
                    .getOrElse { CachedListResult(emptyList()) }
                val fromCache = history.isFromCache || watchLater.isFromCache ||
                    liked.isFromCache || playlists.isFromCache
                _state.value = _state.value.copy(
                    historyVideos = history.items,
                    watchLaterVideos = watchLater.items,
                    likedVideos = liked.items,
                    playlistVideos = playlists.items,
                    isLoading = false,
                    showingCachedData = fromCache
                )
            } catch (e: NotSignedInException) {
                _state.value = _state.value.copy(isLoading = false, historyVideos = emptyList())
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun refresh() {
        if (!authRepository.isSignedIn() || _state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val history = repository.loadHistoryVideos()
                val watchLater = runCatching { repository.loadWatchLaterVideos() }
                    .getOrElse { CachedListResult(emptyList()) }
                val liked = runCatching { repository.loadLikedVideos() }
                    .getOrElse { CachedListResult(emptyList()) }
                val playlists = runCatching { repository.loadUserPlaylists() }
                    .getOrElse { CachedListResult(emptyList()) }
                val fromCache = history.isFromCache || watchLater.isFromCache ||
                    liked.isFromCache || playlists.isFromCache
                _state.value = _state.value.copy(
                    historyVideos = history.items,
                    watchLaterVideos = watchLater.items,
                    likedVideos = liked.items,
                    playlistVideos = playlists.items,
                    isRefreshing = false,
                    showingCachedData = fromCache
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun openItem(
        item: VideoItem,
        onOpenVideo: (videoId: String, isLive: Boolean) -> Unit,
        onPlaylistUnavailable: () -> Unit
    ) {
        if (!item.isPlaylist) {
            onOpenVideo(item.videoId, item.isLive)
            return
        }
        val playlistId = item.playlistId?.trim().orEmpty().ifEmpty { item.videoId }
        viewModelScope.launch {
            try {
                val startVideoId = repository.resolvePlaylistStartVideoId(playlistId)
                if (startVideoId != null) {
                    onOpenVideo(startVideoId, false)
                } else {
                    onPlaylistUnavailable()
                }
            } catch (_: Exception) {
                onPlaylistUnavailable()
            }
        }
    }
}
