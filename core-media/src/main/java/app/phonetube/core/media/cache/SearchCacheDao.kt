package app.phonetube.core.media.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchCacheDao {
    @Query("SELECT query FROM search_queries ORDER BY searchedAtMs DESC LIMIT :limit")
    suspend fun recentQueries(limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuery(entity: SearchQueryEntity)

    @Query("DELETE FROM search_queries")
    suspend fun clearAll()
}
