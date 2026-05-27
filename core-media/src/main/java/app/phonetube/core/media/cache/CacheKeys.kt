package app.phonetube.core.media.cache

object CacheKeys {
    const val PREFIX_LIBRARY = "LIBRARY_"
    const val PREFIX_SEARCH = "SEARCH_"

    const val LIBRARY_HISTORY = "${PREFIX_LIBRARY}HISTORY"
    const val LIBRARY_PLAYLISTS = "${PREFIX_LIBRARY}PLAYLISTS"
    const val LIBRARY_WATCH_LATER = "${PREFIX_LIBRARY}WATCH_LATER"
    const val LIBRARY_LIKED = "${PREFIX_LIBRARY}LIKED"

    fun searchResults(normalizedQuery: String): String = "$PREFIX_SEARCH$normalizedQuery"

    fun normalizeSearchQuery(query: String): String =
        query.trim().lowercase()
}
