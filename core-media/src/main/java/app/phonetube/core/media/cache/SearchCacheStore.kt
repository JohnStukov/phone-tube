package app.phonetube.core.media.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SearchCacheStore(
    private val searchDao: SearchCacheDao
) {
    suspend fun recordQuery(query: String) = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@withContext
        searchDao.upsertQuery(
            SearchQueryEntity(
                query = normalized,
                searchedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun recentQueries(limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        searchDao.recentQueries(limit)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        searchDao.clearAll()
    }
}
