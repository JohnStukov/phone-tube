package app.phonetube.ui.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.ChannelDetails
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelUiState(
    val channel: ChannelDetails? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null
)

class ChannelViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository(application)
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    fun load(channelId: String, fallbackName: String?) {
        viewModelScope.launch {
            _state.value = ChannelUiState(isLoading = true, error = null)
            try {
                val channel = repository.loadChannel(channelId, fallbackName)
                _state.value = ChannelUiState(channel = channel, isLoading = false)
            } catch (e: Exception) {
                _state.value = ChannelUiState(
                    isLoading = false,
                    error = e.message ?: e.javaClass.simpleName
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
                val updated = repository.loadChannel(channelId, channel.name)
                _state.value = _state.value.copy(channel = updated)
            } catch (e: NotSignedInException) {
                _state.value = _state.value.copy(actionMessage = "sign_in_required_action")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    actionMessage = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    fun clearActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }
}
