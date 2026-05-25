package app.phonetube.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.AuthState
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
    val isLoading: Boolean = false,
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
                    loadHistory()
                } else {
                    _state.value = _state.value.copy(
                        historyVideos = emptyList(),
                        isLoading = false,
                        error = null
                    )
                }
            }
        }
    }

    fun loadHistory() {
        if (!authRepository.isSignedIn()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val videos = repository.loadHistoryVideos()
                _state.value = _state.value.copy(historyVideos = videos, isLoading = false)
            } catch (e: NotSignedInException) {
                _state.value = _state.value.copy(isLoading = false, historyVideos = emptyList())
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }
}
