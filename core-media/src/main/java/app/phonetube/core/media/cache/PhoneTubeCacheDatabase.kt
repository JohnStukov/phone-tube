package app.phonetube.core.media.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.phonetube.core.media.pending.PendingActionDao
import app.phonetube.core.media.pending.PendingActionEntity

@Database(
    entities = [
        HomeFeedPageEntity::class,
        HomeFeedVideoEntity::class,
        SearchQueryEntity::class,
        PendingActionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PhoneTubeCacheDatabase : RoomDatabase() {
    abstract fun homeFeedCacheDao(): HomeFeedCacheDao
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        @Volatile
        private var instance: PhoneTubeCacheDatabase? = null

        fun get(context: Context): PhoneTubeCacheDatabase {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                val again = instance
                if (again != null) {
                    again
                } else {
                    Room.databaseBuilder(
                        context.applicationContext,
                        PhoneTubeCacheDatabase::class.java,
                        "phonetube_cache.db"
                    ).fallbackToDestructiveMigration().build().also { instance = it }
                }
            }
        }
    }
}
