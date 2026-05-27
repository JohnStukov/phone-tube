package app.phonetube.core.media

data class CachedListResult(
    val items: List<VideoItem>,
    val isFromCache: Boolean = false
)
