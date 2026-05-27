package app.phonetube.core.media.cache

import app.phonetube.core.media.FeedPage
import app.phonetube.core.media.HomeFeedKind
import app.phonetube.core.media.VideoItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFeedCacheStoreTest {
    @Test
    fun `save and load keeps feed payload`() = runTest {
        val dao = FakeHomeFeedCacheDao()
        val store = HomeFeedCacheStore(dao)
        val original = FeedPage(
            videos = listOf(
                VideoItem(
                    videoId = "abc",
                    title = "Titulo",
                    author = "Canal",
                    thumbnailUrl = "https://img",
                    durationMs = 120_000,
                    isLive = false,
                    subtitle = "sub",
                    durationLabel = "2:00",
                    channelId = "ch",
                    channelAvatarUrl = "https://avatar",
                    percentWatched = 35
                )
            ),
            nextPageKey = "NEXT",
            groupType = 7
        )

        store.save(HomeFeedKind.NEWS, original)
        val cached = store.load(HomeFeedKind.NEWS)

        requireNotNull(cached)
        assertEquals(original.nextPageKey, cached.nextPageKey)
        assertEquals(original.groupType, cached.groupType)
        assertEquals(original.videos.first().videoId, cached.videos.first().videoId)
        assertEquals(original.videos.first().percentWatched, cached.videos.first().percentWatched)
    }

    @Test
    fun `load returns null when feed is not cached`() = runTest {
        val dao = FakeHomeFeedCacheDao()
        val store = HomeFeedCacheStore(dao)
        assertNull(store.load(HomeFeedKind.MOVIES))
    }
}

private class FakeHomeFeedCacheDao : HomeFeedCacheDao {
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
        videosByFeed.getOrPut(feed) { mutableListOf() }.apply {
            removeAll { existing -> videos.any { it.position == existing.position } }
            addAll(videos)
        }
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
