package app.phonetube.core.media

data class FeedPage(
    val videos: List<VideoItem>,
    val nextPageKey: String?,
    val groupType: Int
) {
    val canLoadMore: Boolean get() = !nextPageKey.isNullOrBlank()

    companion object {
        val EMPTY = FeedPage(emptyList(), null, -1)
    }
}
