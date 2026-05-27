package app.phonetube.core.media.cache

import android.content.Context
import app.phonetube.core.media.CachedListResult
import app.phonetube.core.media.FeedPage
import app.phonetube.core.media.HomeFeedKind
import app.phonetube.core.media.VideoItem

class PhoneTubeCacheCoordinator private constructor(context: Context) {
    private val database = PhoneTubeCacheDatabase.get(context)
    private val feedStore = HomeFeedCacheStore(database.homeFeedCacheDao())
    private val searchStore = SearchCacheStore(database.searchCacheDao())

    suspend fun saveFeed(kind: HomeFeedKind, page: FeedPage) = feedStore.save(kind, page)

    suspend fun saveByKey(feedKey: String, page: FeedPage) = feedStore.saveByKey(feedKey, page)

    suspend fun loadFeed(kind: HomeFeedKind, allowStale: Boolean = true): FeedPage? =
        feedStore.loadByKey(kind.name, allowStale)

    suspend fun loadByKey(feedKey: String, allowStale: Boolean = true): FeedPage? =
        feedStore.loadByKey(feedKey, allowStale)

    suspend fun saveVideoList(feedKey: String, videos: List<VideoItem>) {
        feedStore.saveByKey(
            feedKey,
            FeedPage(videos = videos, nextPageKey = null, groupType = 0)
        )
    }

    suspend fun loadVideoList(feedKey: String, allowStale: Boolean = true): CachedListResult? {
        val page = feedStore.loadByKey(feedKey, allowStale) ?: return null
        return CachedListResult(items = page.videos, isFromCache = page.isFromCache)
    }

    suspend fun recordSearchQuery(query: String) = searchStore.recordQuery(query)

    suspend fun recentSearchQueries(limit: Int = 10): List<String> = searchStore.recentQueries(limit)

    suspend fun invalidateAll() {
        feedStore.clearAll()
        searchStore.clearAll()
    }

    companion object {
        @Volatile
        private var instance: PhoneTubeCacheCoordinator? = null

        fun get(context: Context): PhoneTubeCacheCoordinator {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: PhoneTubeCacheCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }
}
