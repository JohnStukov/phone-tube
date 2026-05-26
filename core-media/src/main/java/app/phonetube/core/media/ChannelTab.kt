package app.phonetube.core.media

object ChannelTabIds {
    const val VIDEOS = "videos"
    const val SHORTS = "shorts"
    const val PLAYLISTS = "playlists"
    const val LIVE = "live"
    const val HOME = "home"
}

data class ChannelTab(
    val id: String,
    val title: String
)

data class ChannelSortOption(
    val id: String,
    val label: String
)
