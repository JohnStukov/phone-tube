package app.phonetube.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.CommentPostException
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoComment
import app.phonetube.core.media.PlaybackRestrictions
import app.phonetube.core.media.VideoMetadata
import app.phonetube.core.media.YouTubeRepository
import app.phonetube.core.media.AudioLanguageOptionsHelper
import app.phonetube.core.media.AudioTrackOption
import app.phonetube.core.media.StreamQualityOption
import app.phonetube.core.media.SubtitleOption
import app.phonetube.core.media.SubtitleOptionsHelper
import app.phonetube.core.playback.AudioLanguageMode
import app.phonetube.core.playback.CaptionSize
import app.phonetube.core.playback.PlayerPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val metadata: VideoMetadata = VideoMetadata(title = ""),
    val comments: List<VideoComment> = emptyList(),
    val isLoading: Boolean = true,
    val commentsLoading: Boolean = false,
    val canPostComment: Boolean = false,
    val commentPosting: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,
    val qualityOptions: List<StreamQualityOption> = emptyList(),
    val selectedQualityLabel: String = "Auto",
    val subtitleOptions: List<SubtitleOption> = emptyList(),
    val selectedSubtitleId: String = SubtitleOptionsHelper.OFF_ID,
    val audioTrackOptions: List<AudioTrackOption> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val playbackSpeed: Float = 1f,
    val captionSize: CaptionSize = CaptionSize.MEDIUM,
    val autoplayEnabled: Boolean = true,
    val showSettings: Boolean = false
)

sealed class PlayerActionEvent {
    data class Share(val url: String) : PlayerActionEvent()
    data class Download(val url: String, val title: String) : PlayerActionEvent()
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository(application)
    private val playerPrefs = PlayerPrefs(application)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _actionEvents = MutableSharedFlow<PlayerActionEvent>()
    val actionEvents: SharedFlow<PlayerActionEvent> = _actionEvents.asSharedFlow()

    fun loadDetails(videoId: String) {
        viewModelScope.launch {
            val previous = _state.value
            _state.value = PlayerUiState(
                isLoading = true,
                metadata = VideoMetadata(videoId = videoId, title = ""),
                playbackSpeed = previous.playbackSpeed,
                captionSize = playerPrefs.captionSize,
                autoplayEnabled = previous.autoplayEnabled,
                selectedSubtitleId = playerPrefs.preferredSubtitleId ?: SubtitleOptionsHelper.OFF_ID
            )
            try {
                val metadata = repository.getVideoMetadata(videoId)
                if (PlaybackRestrictions.blocksPlayback(metadata)) {
                    _state.value = _state.value.copy(
                        metadata = metadata,
                        isLoading = false,
                        error = PlaybackRestrictions.LIVE_UNAVAILABLE
                    )
                    return@launch
                }
                val preferredAudio = playerPrefs.resolvePreferredAudioLanguage()
                val audioTracks = repository.getAudioTrackOptions(videoId)
                val selectedAudio = resolveAudioSelection(audioTracks, preferredAudio)
                val qualities = repository.getQualityOptions(videoId, preferredAudio)
                val subtitles = repository.getSubtitleOptions(videoId)
                val preferredSubtitle = resolveSubtitleSelection(subtitles)
                _state.value = _state.value.copy(
                    metadata = metadata,
                    isLoading = false,
                    qualityOptions = qualities,
                    selectedQualityLabel = "Auto",
                    subtitleOptions = subtitles,
                    selectedSubtitleId = preferredSubtitle.id,
                    audioTrackOptions = audioTracks,
                    selectedAudioTrackId = selectedAudio?.id
                )

                val commentsKey = metadata.commentsKey
                if (!commentsKey.isNullOrBlank()) {
                    _state.value = _state.value.copy(commentsLoading = true)
                    val comments = repository.loadComments(commentsKey)
                    val canPost = repository.canPostComment(commentsKey)
                    _state.value = _state.value.copy(
                        comments = comments,
                        commentsLoading = false,
                        canPostComment = canPost
                    )
                } else {
                    _state.value = _state.value.copy(canPostComment = false)
                }
            } catch (e: Exception) {
                _state.value = PlayerUiState(
                    metadata = VideoMetadata(videoId = videoId, title = videoId),
                    isLoading = false,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    fun toggleLike(videoId: String) {
        viewModelScope.launch {
            try {
                val current = _state.value.metadata
                if (current.isLiked) {
                    repository.removeLike(videoId)
                } else {
                    repository.setLike(videoId)
                }
                refreshMetadata(videoId)
            } catch (e: NotSignedInException) {
                postActionError("sign_in_required_action")
            } catch (e: Exception) {
                postActionError(e.message)
            }
        }
    }

    fun toggleDislike(videoId: String) {
        viewModelScope.launch {
            try {
                val current = _state.value.metadata
                if (current.isDisliked) {
                    repository.removeDislike(videoId)
                } else {
                    repository.setDislike(videoId)
                }
                refreshMetadata(videoId)
            } catch (e: NotSignedInException) {
                postActionError("sign_in_required_action")
            } catch (e: Exception) {
                postActionError(e.message)
            }
        }
    }

    fun openChannel(videoId: String, onOpen: (channelId: String, channelName: String?) -> Unit) {
        val meta = _state.value.metadata
        val existing = meta.channelId?.trim()?.takeIf { it.isNotEmpty() }
        if (existing != null) {
            onOpen(existing, meta.author)
            return
        }
        viewModelScope.launch {
            val resolved = repository.resolveChannelIdForVideo(videoId)
            if (!resolved.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    metadata = meta.copy(channelId = resolved)
                )
                onOpen(resolved, meta.author)
            }
        }
    }

    fun toggleSubscribe() {
        val channelId = _state.value.metadata.channelId
        if (channelId.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                if (_state.value.metadata.isSubscribed) {
                    repository.unsubscribe(channelId)
                } else {
                    repository.subscribe(channelId)
                }
                refreshMetadata(_state.value.metadata.videoId)
            } catch (e: NotSignedInException) {
                postActionError("sign_in_required_action")
            } catch (e: Exception) {
                postActionError(e.message)
            }
        }
    }

    fun share(videoId: String) {
        viewModelScope.launch {
            _actionEvents.emit(PlayerActionEvent.Share(repository.shareUrl(videoId)))
        }
    }

    fun download(videoId: String) {
        viewModelScope.launch {
            try {
                val url = repository.getDownloadUrl(videoId)
                if (url.isNullOrBlank()) {
                    postActionError("download_unavailable")
                    return@launch
                }
                val title = _state.value.metadata.title.ifBlank { videoId }
                _actionEvents.emit(PlayerActionEvent.Download(url, title))
            } catch (e: Exception) {
                postActionError(e.message)
            }
        }
    }

    fun clearActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }

    fun postComment(videoId: String, text: String) {
        val commentsKey = _state.value.metadata.commentsKey ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(commentPosting = true)
            try {
                repository.postComment(commentsKey, text)
                val comments = repository.loadComments(commentsKey)
                val canPost = repository.canPostComment(commentsKey)
                _state.value = _state.value.copy(
                    comments = comments,
                    canPostComment = canPost,
                    commentPosting = false,
                    actionMessage = "comment_posted"
                )
            } catch (e: NotSignedInException) {
                _state.value = _state.value.copy(
                    commentPosting = false,
                    actionMessage = "sign_in_required_action"
                )
            } catch (e: CommentPostException) {
                _state.value = _state.value.copy(
                    commentPosting = false,
                    actionMessage = e.message ?: "comment_post_failed"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    commentPosting = false,
                    actionMessage = e.message ?: "comment_post_failed"
                )
            }
        }
    }

    fun setShowSettings(show: Boolean) {
        _state.value = _state.value.copy(showSettings = show)
    }

    fun setPlaybackSpeed(speed: Float) {
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    fun selectQuality(option: StreamQualityOption) {
        _state.value = _state.value.copy(selectedQualityLabel = option.label)
    }

    fun selectSubtitle(option: SubtitleOption) {
        playerPrefs.preferredSubtitleId = option.id
        _state.value = _state.value.copy(selectedSubtitleId = option.id)
    }

    fun selectAudioTrack(option: AudioTrackOption, onApplied: (StreamQualityOption?) -> Unit) {
        val videoId = _state.value.metadata.videoId
        if (videoId.isBlank()) {
            onApplied(null)
            return
        }
        viewModelScope.launch {
            playerPrefs.audioLanguageMode = AudioLanguageMode.MANUAL
            playerPrefs.audioLanguageCode = option.languageCode
            val qualities = repository.getQualityOptions(videoId, option.languageCode)
            val selectedLabel = _state.value.selectedQualityLabel
            val quality = qualities.firstOrNull { it.label == selectedLabel } ?: qualities.firstOrNull()
            _state.value = _state.value.copy(
                qualityOptions = qualities,
                selectedAudioTrackId = option.id
            )
            onApplied(quality)
        }
    }

    fun setCaptionSize(size: CaptionSize) {
        playerPrefs.captionSize = size
        _state.value = _state.value.copy(captionSize = size)
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(autoplayEnabled = enabled)
    }

    fun selectedSubtitle(): SubtitleOption? =
        _state.value.subtitleOptions.firstOrNull { it.id == _state.value.selectedSubtitleId }

    private fun resolveSubtitleSelection(options: List<SubtitleOption>): SubtitleOption {
        if (options.isEmpty()) {
            return SubtitleOption(SubtitleOptionsHelper.OFF_ID, "Off", null)
        }
        val preferredId = playerPrefs.preferredSubtitleId
        return options.firstOrNull { it.id == preferredId } ?: options.first()
    }

    private fun resolveAudioSelection(
        options: List<AudioTrackOption>,
        preferredLanguage: String?
    ): AudioTrackOption? = AudioLanguageOptionsHelper.resolveSelection(options, preferredLanguage)

    fun findNextRelatedVideo(currentVideoId: String) =
        _state.value.metadata.relatedVideos.firstOrNull { it.videoId != currentVideoId }

    private suspend fun refreshMetadata(videoId: String) {
        if (videoId.isBlank()) return
        val metadata = repository.getVideoMetadata(videoId)
        _state.value = _state.value.copy(metadata = metadata)
    }

    private fun postActionError(keyOrMessage: String?) {
        _state.value = _state.value.copy(actionMessage = keyOrMessage)
    }
}
