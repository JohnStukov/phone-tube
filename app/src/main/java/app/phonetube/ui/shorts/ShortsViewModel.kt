package app.phonetube.ui.shorts

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phonetube.R
import app.phonetube.core.media.NotSignedInException
import app.phonetube.core.media.VideoComment
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.VideoItemMapper
import app.phonetube.core.media.VideoMetadata
import app.phonetube.core.media.YouTubeRepository
import app.phonetube.ui.player.resolveActionMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShortVideoDetails(
    val metadata: VideoMetadata,
    val comments: List<VideoComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentPosting: Boolean = false,
    val canPostComment: Boolean = false,
    val actionMessage: String? = null
)

data class ShortsUiState(
    val videos: List<VideoItem> = emptyList(),
    val activeVideoId: String? = null,
    val activeDetails: ShortVideoDetails? = null,
    val detailsLoading: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val showComments: Boolean = false
)

class ShortsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository(application)
    private val _state = MutableStateFlow(ShortsUiState(isLoading = true))
    val state: StateFlow<ShortsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            fetchShorts(setLoading = true)
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            fetchShorts(setLoading = false)
        }
    }

    fun onActiveVideoChanged(videoId: String) {
        if (_state.value.activeVideoId == videoId && _state.value.activeDetails != null) return
        _state.value = _state.value.copy(
            activeVideoId = videoId,
            activeDetails = null,
            detailsLoading = true,
            showComments = false
        )
        viewModelScope.launch {
            try {
                val metadata = repository.getVideoMetadata(videoId)
                _state.value = _state.value.copy(
                    activeDetails = ShortVideoDetails(metadata = metadata),
                    detailsLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(detailsLoading = false)
            }
        }
    }

    fun toggleLike() {
        val videoId = _state.value.activeVideoId ?: return
        viewModelScope.launch {
            try {
                val current = _state.value.activeDetails?.metadata ?: return@launch
                if (current.isLiked) repository.removeLike(videoId) else repository.setLike(videoId)
                refreshMetadata(videoId)
            } catch (e: NotSignedInException) {
                showToast("sign_in_required_action")
            } catch (_: Exception) {
            }
        }
    }

    fun toggleDislike() {
        val videoId = _state.value.activeVideoId ?: return
        viewModelScope.launch {
            try {
                val current = _state.value.activeDetails?.metadata ?: return@launch
                if (current.isDisliked) repository.removeDislike(videoId) else repository.setDislike(videoId)
                refreshMetadata(videoId)
            } catch (e: NotSignedInException) {
                showToast("sign_in_required_action")
            } catch (_: Exception) {
            }
        }
    }

    fun toggleSubscribe() {
        val channelId = _state.value.activeDetails?.metadata?.channelId ?: return
        viewModelScope.launch {
            try {
                val subscribed = _state.value.activeDetails?.metadata?.isSubscribed == true
                if (subscribed) repository.unsubscribe(channelId) else repository.subscribe(channelId)
                _state.value.activeVideoId?.let { refreshMetadata(it) }
            } catch (e: NotSignedInException) {
                showToast("sign_in_required_action")
            } catch (_: Exception) {
            }
        }
    }

    fun openComments() {
        val details = _state.value.activeDetails ?: return
        val key = details.metadata.commentsKey
        if (key.isNullOrBlank()) {
            showToast("comments_not_ready")
            return
        }
        _state.value = _state.value.copy(showComments = true)
        if (details.comments.isNotEmpty() || details.commentsLoading) return
        viewModelScope.launch {
            updateActiveDetails { it.copy(commentsLoading = true) }
            try {
                val comments = repository.loadComments(key)
                val canPost = repository.canPostComment(key)
                updateActiveDetails {
                    it.copy(
                        comments = comments,
                        commentsLoading = false,
                        canPostComment = canPost
                    )
                }
            } catch (_: Exception) {
                updateActiveDetails { it.copy(commentsLoading = false) }
            }
        }
    }

    fun dismissComments() {
        _state.value = _state.value.copy(showComments = false)
    }

    fun postComment(text: String) {
        val key = _state.value.activeDetails?.metadata?.commentsKey ?: return
        viewModelScope.launch {
            updateActiveDetails { it.copy(commentPosting = true) }
            try {
                repository.postComment(key, text)
                val comments = repository.loadComments(key)
                val canPost = repository.canPostComment(key)
                updateActiveDetails {
                    it.copy(
                        comments = comments,
                        canPostComment = canPost,
                        commentPosting = false,
                        actionMessage = "comment_posted"
                    )
                }
            } catch (e: NotSignedInException) {
                updateActiveDetails {
                    it.copy(commentPosting = false, actionMessage = "sign_in_required_action")
                }
            } catch (e: Exception) {
                updateActiveDetails {
                    it.copy(
                        commentPosting = false,
                        actionMessage = e.message ?: "comment_post_failed"
                    )
                }
            }
        }
    }

    fun clearActionMessage() {
        updateActiveDetails { it.copy(actionMessage = null) }
    }

    private suspend fun refreshMetadata(videoId: String) {
        val metadata = repository.getVideoMetadata(videoId)
        updateActiveDetails { current ->
            current.copy(metadata = metadata)
        }
    }

    private fun updateActiveDetails(transform: (ShortVideoDetails) -> ShortVideoDetails) {
        val current = _state.value.activeDetails ?: return
        _state.value = _state.value.copy(activeDetails = transform(current))
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.canLoadMore) {
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(isLoadingMore = true, error = null)
            try {
                val page = repository.loadShortsFeedMore()
                _state.value = current.copy(
                    videos = VideoItemMapper.merge(current.videos, page.videos),
                    isLoadingMore = false,
                    canLoadMore = page.canLoadMore
                )
            } catch (e: Exception) {
                _state.value = current.copy(
                    isLoadingMore = false,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    private suspend fun fetchShorts(setLoading: Boolean) {
        try {
            val page = repository.loadShortsFeed()
            _state.value = ShortsUiState(
                videos = page.videos,
                isLoading = false,
                isRefreshing = false,
                canLoadMore = page.canLoadMore
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = if (setLoading) false else _state.value.isLoading,
                isRefreshing = false,
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun showToast(messageKey: String) {
        val ctx = getApplication<Application>()
        val text = resolveActionMessage(ctx, messageKey) ?: messageKey
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    }
}
