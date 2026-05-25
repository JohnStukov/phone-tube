package app.phonetube.core.media

data class VideoItem(
    val videoId: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val isLive: Boolean,
    val subtitle: String? = null,
    val durationLabel: String? = null,
    val channelId: String? = null,
    val channelAvatarUrl: String? = null
)
