package app.phonetube.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
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
            _state.value = NotificationsUiState(isLoading = true)
            try {
                val videos = repository.loadNotifications()
                _state.value = NotificationsUiState(videos = videos, isLoading = false)
            } catch (e: NotSignedInException) {
                _state.value = NotificationsUiState(needsSignIn = true, isLoading = false)
            } catch (e: Exception) {
                _state.value = NotificationsUiState(
                    isLoading = false,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }
}
