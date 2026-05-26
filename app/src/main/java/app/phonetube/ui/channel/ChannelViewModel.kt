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

    private var loadedChannelId: String? = null
    private var loadedFallbackName: String? = null

    fun load(channelId: String, fallbackName: String?) {
        loadedChannelId = channelId
        loadedFallbackName = fallbackName
        reloadChannel(tabId = null, sortId = null)
    }

    fun selectTab(tabId: String) {
        val channel = _state.value.channel ?: return
        reloadChannel(tabId = tabId, sortId = channel.selectedSortId)
    }

    fun selectSort(sortId: String) {
        val channel = _state.value.channel ?: return
        reloadChannel(tabId = channel.selectedTabId, sortId = sortId)
    }

    private fun reloadChannel(tabId: String?, sortId: String?) {
        val channelId = loadedChannelId ?: return
        val previous = _state.value.channel
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                channel = previous?.copy(videos = emptyList())
            )
            try {
                val channel = repository.loadChannel(
                    channelId,
                    loadedFallbackName,
                    tabId ?: previous?.selectedTabId,
                    sortId ?: previous?.selectedSortId
                )
                _state.value = ChannelUiState(channel = channel, isLoading = false)
            } catch (e: Exception) {
                _state.value = ChannelUiState(
                    channel = previous,
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
                val updated = repository.loadChannel(
                    channelId,
                    channel.name,
                    channel.selectedTabId,
                    channel.selectedSortId
                )
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
