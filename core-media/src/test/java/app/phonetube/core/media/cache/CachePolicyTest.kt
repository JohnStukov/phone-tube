package app.phonetube.core.media.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class CachePolicyTest {
    @Test
    fun `ttl varies by cache key prefix`() {
        assertEquals(CachePolicy.LIBRARY_TTL_MS, CachePolicy.maxAgeMsFor(CacheKeys.LIBRARY_HISTORY))
        assertEquals(CachePolicy.SEARCH_TTL_MS, CachePolicy.maxAgeMsFor(CacheKeys.searchResults("test")))
        assertEquals(CachePolicy.FEED_TTL_MS, CachePolicy.maxAgeMsFor("HOME"))
    }
}
