package app.phonetube.core.media.cache

object CachePolicy {
    const val FEED_TTL_MS = 30 * 60 * 1_000L
    const val LIBRARY_TTL_MS = 15 * 60 * 1_000L
    const val SEARCH_TTL_MS = 10 * 60 * 1_000L
    const val MAX_FEED_ENTRIES = 24

    fun maxAgeMsFor(feedKey: String): Long = when {
        feedKey.startsWith(CacheKeys.PREFIX_LIBRARY) -> LIBRARY_TTL_MS
        feedKey.startsWith(CacheKeys.PREFIX_SEARCH) -> SEARCH_TTL_MS
        else -> FEED_TTL_MS
    }
}
