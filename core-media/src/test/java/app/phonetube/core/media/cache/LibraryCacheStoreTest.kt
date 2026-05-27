package app.phonetube.core.media.cache

import app.phonetube.core.media.FeedPage
import app.phonetube.core.media.VideoItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCacheStoreTest {
    @Test
    fun `library key round trip marks cache`() = runTest {
        val dao = InMemoryHomeFeedCacheDao()
        val store = HomeFeedCacheStore(dao)
        val videos = listOf(
            VideoItem(
                videoId = "v1",
                title = "Title",
                author = "Author",
                thumbnailUrl = "https://thumb",
                durationMs = 60_000,
                isLive = false
            )
        )
        store.saveByKey(
            CacheKeys.LIBRARY_HISTORY,
            FeedPage(videos = videos, nextPageKey = null, groupType = 0)
        )
        val loaded = store.loadByKey(CacheKeys.LIBRARY_HISTORY)
        requireNotNull(loaded)
        assertTrue(loaded.isFromCache)
        assertEquals("v1", loaded.videos.first().videoId)
    }
}

private class InMemoryHomeFeedCacheDao : HomeFeedCacheDao {
    private val pages = mutableMapOf<String, HomeFeedPageEntity>()
    private val videosByFeed = mutableMapOf<String, MutableList<HomeFeedVideoEntity>>()

    override suspend fun getPage(feed: String): HomeFeedPageEntity? = pages[feed]

    override suspend fun getVideos(feed: String): List<HomeFeedVideoEntity> =
        videosByFeed[feed].orEmpty().sortedBy { it.position }

    override suspend fun upsertPage(page: HomeFeedPageEntity) {
        pages[page.feed] = page
    }

    override suspend fun upsertVideos(videos: List<HomeFeedVideoEntity>) {
        if (videos.isEmpty()) return
        val feed = videos.first().feed
        videosByFeed.getOrPut(feed) { mutableListOf() }.addAll(videos)
    }

    override suspend fun clearVideos(feed: String) {
        videosByFeed.remove(feed)
    }

    override suspend fun replace(feed: String, page: HomeFeedPageEntity, videos: List<HomeFeedVideoEntity>) {
        clearVideos(feed)
        upsertPage(page)
        if (videos.isNotEmpty()) upsertVideos(videos)
    }

    override suspend fun listFeedsByAge(): List<String> =
        pages.values.sortedBy { it.fetchedAtMs }.map { it.feed }

    override suspend fun deletePage(feed: String) {
        pages.remove(feed)
    }

    override suspend fun clearAllPages() {
        pages.clear()
    }

    override suspend fun clearAllVideos() {
        videosByFeed.clear()
    }

    override suspend fun clearAll() {
        clearAllVideos()
        clearAllPages()
    }
}
