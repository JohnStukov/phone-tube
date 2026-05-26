package app.phonetube.core.media

data class ChannelDetails(
    val channelId: String,
    val name: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val handle: String? = null,
    val subscriberCount: String? = null,
    val description: String? = null,
    val videos: List<VideoItem> = emptyList(),
    val tabs: List<ChannelTab> = emptyList(),
    val sortOptions: List<ChannelSortOption> = emptyList(),
    val selectedTabId: String = "Videos",
    val selectedSortId: String? = null,
    val isSubscribed: Boolean = false,
    val canLoadMore: Boolean = false
)
