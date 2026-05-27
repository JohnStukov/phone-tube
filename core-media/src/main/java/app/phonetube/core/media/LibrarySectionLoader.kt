package app.phonetube.core.media

import com.liskovsoft.youtubeapi.browse.v2.BrowseService2

internal class LibrarySectionLoader {
    private val browse = BrowseService2()

    fun loadWatchLater(): List<VideoItem> =
        flatten(browse.getGridChannel(WATCH_LATER_CHANNEL_ID))

    fun loadLikedVideos(): List<VideoItem> =
        flatten(browse.getGridChannel(LIKED_CHANNEL_ID))

    private fun flatten(group: com.liskovsoft.mediaserviceinterfaces.data.MediaGroup?): List<VideoItem> =
        VideoItemMapper.fromGroups(listOf(group))

    companion object {
        private const val WATCH_LATER_CHANNEL_ID = "VLWL"
        private const val LIKED_CHANNEL_ID = "VLLL"
    }
}
