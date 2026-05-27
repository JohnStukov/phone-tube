package app.phonetube.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoItemMapperTest {

    @Test
    fun dedupe_keepsFirstOccurrence() {
        val a = sample(videoId = "a")
        val b = sample(videoId = "b")
        val duplicate = sample(videoId = "a", title = "duplicate")
        val result = VideoItemMapper.dedupe(listOf(a, b, duplicate))
        assertEquals(2, result.size)
        assertEquals("a", result[0].videoId)
        assertEquals("title", result[0].title)
        assertEquals("b", result[1].videoId)
    }

    @Test
    fun merge_appendsOnlyNewKeys() {
        val existing = listOf(sample("one"), sample("two"))
        val more = listOf(sample("two", title = "two-again"), sample("three"))
        val merged = VideoItemMapper.merge(existing, more)
        assertEquals(3, merged.size)
        assertEquals("one", merged[0].videoId)
        assertEquals("two", merged[1].videoId)
        assertEquals("three", merged[2].videoId)
    }

    @Test
    fun stableListKey_usesPlaylistIdForPlaylists() {
        val playlist = sample("vid", playlistId = "PL123", isPlaylist = true)
        assertTrue(playlist.stableListKey.startsWith("pl_"))
    }

    private fun sample(
        videoId: String,
        title: String = "title",
        playlistId: String? = null,
        isPlaylist: Boolean = false
    ) = VideoItem(
        videoId = videoId,
        title = title,
        author = "author",
        thumbnailUrl = null,
        durationMs = 60_000L,
        isLive = false,
        isPlaylist = isPlaylist,
        playlistId = playlistId
    )
}
