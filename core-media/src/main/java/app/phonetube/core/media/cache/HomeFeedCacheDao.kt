package app.phonetube.core.media.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HomeFeedCacheDao {
    @Query("SELECT * FROM home_feed_pages WHERE feed = :feed LIMIT 1")
    suspend fun getPage(feed: String): HomeFeedPageEntity?

    @Query("SELECT * FROM home_feed_videos WHERE feed = :feed ORDER BY position ASC")
    suspend fun getVideos(feed: String): List<HomeFeedVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: HomeFeedPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<HomeFeedVideoEntity>)

    @Query("DELETE FROM home_feed_videos WHERE feed = :feed")
    suspend fun clearVideos(feed: String)

    @Transaction
    suspend fun replace(feed: String, page: HomeFeedPageEntity, videos: List<HomeFeedVideoEntity>) {
        clearVideos(feed)
        upsertPage(page)
        if (videos.isNotEmpty()) {
            upsertVideos(videos)
        }
    }

    @Query("SELECT feed FROM home_feed_pages ORDER BY fetchedAtMs ASC")
    suspend fun listFeedsByAge(): List<String>

    @Query("DELETE FROM home_feed_pages WHERE feed = :feed")
    suspend fun deletePage(feed: String)

    @Query("DELETE FROM home_feed_pages")
    suspend fun clearAllPages()

    @Query("DELETE FROM home_feed_videos")
    suspend fun clearAllVideos()

    @Transaction
    suspend fun clearAll() {
        clearAllVideos()
        clearAllPages()
    }
}
