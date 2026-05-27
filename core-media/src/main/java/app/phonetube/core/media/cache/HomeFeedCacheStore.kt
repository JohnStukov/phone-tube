package app.phonetube.core.media.cache

import app.phonetube.core.media.FeedPage
import app.phonetube.core.media.HomeFeedKind
import app.phonetube.core.media.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class HomeFeedCacheStore(
    private val dao: HomeFeedCacheDao
) {

    suspend fun save(kind: HomeFeedKind, page: FeedPage) = withContext(Dispatchers.IO) {
        saveByKey(kind.name, page)
    }

    suspend fun saveByKey(feedKey: String, page: FeedPage) = withContext(Dispatchers.IO) {
        val pageEntity = HomeFeedPageEntity(
            feed = feedKey,
            nextPageKey = page.nextPageKey,
            groupType = page.groupType,
            fetchedAtMs = System.currentTimeMillis()
        )
        val videoEntities = page.videos.mapIndexed { index, video ->
            HomeFeedVideoEntity(
                feed = feedKey,
                position = index,
                videoId = video.videoId,
                title = video.title,
                author = video.author,
                thumbnailUrl = video.thumbnailUrl,
                durationMs = video.durationMs,
                isLive = video.isLive,
                subtitle = video.subtitle,
                durationLabel = video.durationLabel,
                channelId = video.channelId,
                channelAvatarUrl = video.channelAvatarUrl,
                percentWatched = video.percentWatched,
                isPlaylist = video.isPlaylist,
                playlistId = video.playlistId
            )
        }
        dao.replace(feedKey, pageEntity, videoEntities)
        enforceLruLimit()
    }

    private suspend fun enforceLruLimit() {
        val feeds = dao.listFeedsByAge()
        val overflow = feeds.size - CachePolicy.MAX_FEED_ENTRIES
        if (overflow <= 0) return
        feeds.take(overflow).forEach { feed ->
            dao.deletePage(feed)
            dao.clearVideos(feed)
        }
    }

    suspend fun load(kind: HomeFeedKind): FeedPage? = withContext(Dispatchers.IO) {
        loadByKey(kind.name)
    }

    suspend fun loadByKey(feedKey: String, allowStale: Boolean = true): FeedPage? = withContext(Dispatchers.IO) {
        val page = dao.getPage(feedKey) ?: return@withContext null
        val ageMs = System.currentTimeMillis() - page.fetchedAtMs
        val maxAgeMs = CachePolicy.maxAgeMsFor(feedKey)
        if (!allowStale && ageMs > maxAgeMs) {
            return@withContext null
        }
        val videos = dao.getVideos(feedKey).map { entity ->
            VideoItem(
                videoId = entity.videoId,
                title = entity.title,
                author = entity.author,
                thumbnailUrl = entity.thumbnailUrl,
                durationMs = entity.durationMs,
                isLive = entity.isLive,
                subtitle = entity.subtitle,
                durationLabel = entity.durationLabel,
                channelId = entity.channelId,
                channelAvatarUrl = entity.channelAvatarUrl,
                percentWatched = entity.percentWatched,
                isPlaylist = entity.isPlaylist,
                playlistId = entity.playlistId
            )
        }
        if (videos.isEmpty()) return@withContext null
        FeedPage(
            videos = videos,
            nextPageKey = page.nextPageKey,
            groupType = page.groupType,
            isFromCache = true
        )
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
