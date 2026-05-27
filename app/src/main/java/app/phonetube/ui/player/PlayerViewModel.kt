package app.phonetube.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.core.media.CommentPostException
import app.phonetube.core.media.MediaErrors
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoComment
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.PlaybackRestrictions
import app.phonetube.core.media.VideoMetadata
import app.phonetube.core.media.YouTubeRepository
import app.phonetube.core.media.network.ConnectivityMonitor
import app.phonetube.core.media.pending.PendingActionType
import app.phonetube.core.media.pending.PendingActionsRepository
import org.json.JSONObject
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val showSettings: Boolean = false,
    val actionInProgress: Boolean = false,
    val pendingSyncCount: Int = 0,
    val isBuffering: Boolean = false,
    val reconnecting: Boolean = false
)

sealed class PlayerActionEvent {
    data class Share(val url: String) : PlayerActionEvent()
    data class Download(val url: String, val title: String) : PlayerActionEvent()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val repository: YouTubeRepository,
    private val connectivity: ConnectivityMonitor,
    private val pendingActions: PendingActionsRepository
) : AndroidViewModel(application) {
    private val playerPrefs = PlayerPrefs(application)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _actionEvents = MutableSharedFlow<PlayerActionEvent>()
    val actionEvents: SharedFlow<PlayerActionEvent> = _actionEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            pendingActions.pendingCount.collect { count ->
                _state.value = _state.value.copy(pendingSyncCount = count)
            }
        }
    }

    fun setBuffering(buffering: Boolean) {
        _state.value = _state.value.copy(isBuffering = buffering)
    }

    fun setReconnecting(reconnecting: Boolean) {
        _state.value = _state.value.copy(reconnecting = reconnecting)
    }

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
                val preferredAudio = playerPrefs.resolvePreferredAudioLanguage()
                val audioTracks = repository.getAudioTrackOptions(videoId)
                val selectedAudio = resolveAudioSelection(audioTracks, preferredAudio)
                val audioForStream = selectedAudio?.languageCode ?: preferredAudio
                val qualities = repository.getQualityOptions(videoId, audioForStream)
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
                    error = MediaErrors.codeFor(e)
                )
            }
        }
    }

    fun toggleLike(videoId: String) {
        if (!canRunAction(videoId)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, actionMessage = null)
            try {
                val current = _state.value.metadata
                if (!connectivity.isOnline.value) {
                    val type = if (current.isLiked) PendingActionType.REMOVE_LIKE else PendingActionType.LIKE
                    pendingActions.enqueue(type, JSONObject().put("videoId", videoId))
                    _state.value = _state.value.copy(
                        metadata = current.copy(
                            likeStatus = if (current.isLiked) {
                                VideoMetadata.LIKE_STATUS_NONE
                            } else {
                                VideoMetadata.LIKE_STATUS_LIKED
                            }
                        ),
                        actionMessage = "action_pending_sync"
                    )
                    return@launch
                }
                if (current.isLiked) {
                    repository.removeLike(videoId)
                } else {
                    repository.setLike(videoId)
                }
                refreshMetadata(videoId)
            } catch (e: Exception) {
                postActionError(MediaErrors.codeFor(e))
            } finally {
                _state.value = _state.value.copy(actionInProgress = false)
            }
        }
    }

    fun toggleDislike(videoId: String) {
        if (!canRunAction(videoId)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, actionMessage = null)
            try {
                val current = _state.value.metadata
                if (!connectivity.isOnline.value) {
                    val type = if (current.isDisliked) PendingActionType.REMOVE_DISLIKE else PendingActionType.DISLIKE
                    pendingActions.enqueue(type, JSONObject().put("videoId", videoId))
                    _state.value = _state.value.copy(
                        metadata = current.copy(
                            likeStatus = if (current.isDisliked) {
                                VideoMetadata.LIKE_STATUS_NONE
                            } else {
                                VideoMetadata.LIKE_STATUS_DISLIKED
                            }
                        ),
                        actionMessage = "action_pending_sync"
                    )
                    return@launch
                }
                if (current.isDisliked) {
                    repository.removeDislike(videoId)
                } else {
                    repository.setDislike(videoId)
                }
                refreshMetadata(videoId)
            } catch (e: Exception) {
                postActionError(MediaErrors.codeFor(e))
            } finally {
                _state.value = _state.value.copy(actionInProgress = false)
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
        val videoId = _state.value.metadata.videoId
        if (channelId.isNullOrBlank() || !canRunAction(videoId)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInProgress = true, actionMessage = null)
            try {
                val subscribed = _state.value.metadata.isSubscribed
                if (!connectivity.isOnline.value) {
                    val type = if (subscribed) PendingActionType.UNSUBSCRIBE else PendingActionType.SUBSCRIBE
                    pendingActions.enqueue(type, JSONObject().put("channelId", channelId))
                    _state.value = _state.value.copy(
                        metadata = _state.value.metadata.copy(isSubscribed = !subscribed),
                        actionMessage = "action_pending_sync"
                    )
                    return@launch
                }
                if (subscribed) {
                    repository.unsubscribe(channelId)
                } else {
                    repository.subscribe(channelId)
                }
                refreshMetadata(videoId)
            } catch (e: Exception) {
                postActionError(MediaErrors.codeFor(e))
            } finally {
                _state.value = _state.value.copy(actionInProgress = false)
            }
        }
    }

    fun share(videoId: String) {
        viewModelScope.launch {
            _actionEvents.emit(PlayerActionEvent.Share(repository.shareUrl(videoId)))
        }
    }

    fun download(videoId: String) {
        if (videoId.isBlank()) return
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
                postActionError(MediaErrors.codeFor(e))
            }
        }
    }

    fun clearActionMessage() {
        _state.value = _state.value.copy(actionMessage = null)
    }

    fun postComment(videoId: String, text: String) {
        if (videoId.isBlank()) return
        val commentText = text.trim()
        if (commentText.isEmpty()) {
            postActionError("comment_empty")
            return
        }
        val commentsKey = _state.value.metadata.commentsKey ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(commentPosting = true, actionMessage = null)
            try {
                repository.postComment(commentsKey, commentText)
                val comments = repository.loadComments(commentsKey)
                val canPost = repository.canPostComment(commentsKey)
                _state.value = _state.value.copy(
                    comments = comments,
                    canPostComment = canPost,
                    commentPosting = false,
                    actionMessage = "comment_posted"
                )
            } catch (e: CommentPostException) {
                _state.value = _state.value.copy(
                    commentPosting = false,
                    actionMessage = e.message ?: "comment_post_failed"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(commentPosting = false)
                postActionError(
                    if (e is NotSignedInException) MediaErrors.SIGN_IN else MediaErrors.codeFor(e)
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

    private var autoplayCursor: Int = -1

    fun findNextRelatedVideo(currentVideoId: String): VideoItem? {
        val related = _state.value.metadata.relatedVideos
        if (related.isEmpty()) return null
        if (autoplayCursor < 0 || related.getOrNull(autoplayCursor)?.videoId == currentVideoId) {
            autoplayCursor = related.indexOfFirst { it.videoId != currentVideoId }
        } else {
            autoplayCursor = (autoplayCursor + 1).coerceAtMost(related.lastIndex)
            if (related.getOrNull(autoplayCursor)?.videoId == currentVideoId) {
                autoplayCursor = related.indexOfFirst { it.videoId != currentVideoId }
            }
        }
        return related.getOrNull(autoplayCursor)?.takeIf { it.videoId != currentVideoId }
    }

    fun resetAutoplayCursor() {
        autoplayCursor = -1
    }

    private suspend fun refreshMetadata(videoId: String) {
        if (videoId.isBlank()) return
        runCatching { repository.getVideoMetadata(videoId) }
            .onSuccess { metadata ->
                _state.value = _state.value.copy(metadata = metadata)
            }
            .onFailure { e ->
                postActionError(MediaErrors.codeFor(e))
            }
    }

    private fun postActionError(keyOrMessage: String?) {
        _state.value = _state.value.copy(actionMessage = keyOrMessage)
    }

    private fun canRunAction(videoId: String): Boolean {
        val state = _state.value
        if (videoId.isBlank()) return false
        if (state.actionInProgress) return false
        return state.metadata.videoId == videoId
    }
}
