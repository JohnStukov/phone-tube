package app.phonetube.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.AuthState
import app.phonetube.core.media.MediaErrors
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val auth: AuthState = AuthState(),
    val historyVideos: List<VideoItem> = emptyList(),
    val playlistVideos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository(application)
    private val authRepository = AuthRepository.get(application)
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
                    _state.value.playlistVideos.isEmpty(),
                error = null
            )
            try {
                val history = repository.loadHistoryVideos()
                val playlists = runCatching { repository.loadUserPlaylists() }.getOrElse { emptyList() }
                _state.value = _state.value.copy(
                    historyVideos = history,
                    playlistVideos = playlists,
                    isLoading = false
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
                val playlists = runCatching { repository.loadUserPlaylists() }.getOrElse { emptyList() }
                _state.value = _state.value.copy(
                    historyVideos = history,
                    playlistVideos = playlists,
                    isRefreshing = false
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
