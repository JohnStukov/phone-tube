package app.phonetube.core.media

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem

data class FeedPage(
    val videos: List<VideoItem>,
    val nextPageKey: String?,
    val groupType: Int,
    val notificationSources: Map<String, MediaItem> = emptyMap(),
    val isFromCache: Boolean = false
) {
    val canLoadMore: Boolean get() = !nextPageKey.isNullOrBlank()

    companion object {
        val EMPTY = FeedPage(emptyList(), null, -1)
    }
}