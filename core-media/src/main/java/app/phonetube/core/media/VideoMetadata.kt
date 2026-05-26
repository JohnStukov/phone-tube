package app.phonetube.core.media

data class VideoMetadata(
    val videoId: String = "",
    val title: String,
    val channelId: String? = null,
    val author: String? = null,
    val viewCount: String? = null,
    val subscriberCount: String? = null,
    val authorImageUrl: String? = null,
    val publishedDate: String? = null,
    val likeCount: String? = null,
    val description: String? = null,
    val commentsKey: String? = null,
    val isSubscribed: Boolean = false,
    val isLive: Boolean = false,
    val isLiveContent: Boolean = false,
    val likeStatus: Int = LIKE_STATUS_NONE,
    val relatedVideos: List<VideoItem> = emptyList(),
    val percentWatched: Int = -1
) {
    companion object {
        const val LIKE_STATUS_NONE = 0
        const val LIKE_STATUS_LIKED = 1
        const val LIKE_STATUS_DISLIKED = 2
    }

    val isLiked: Boolean get() = likeStatus == LIKE_STATUS_LIKED
    val isDisliked: Boolean get() = likeStatus == LIKE_STATUS_DISLIKED
}
