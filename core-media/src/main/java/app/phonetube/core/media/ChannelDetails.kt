package app.phonetube.core.media

data class ChannelDetails(
    val channelId: String,
    val name: String,
    val avatarUrl: String? = null,
    val subscriberCount: String? = null,
    val description: String? = null,
    val videos: List<VideoItem> = emptyList(),
    val isSubscribed: Boolean = false
)
