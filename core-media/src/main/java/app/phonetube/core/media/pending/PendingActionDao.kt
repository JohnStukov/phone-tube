package app.phonetube.core.media.pending

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions ORDER BY createdAtMs ASC")
    suspend fun all(): List<PendingActionEntity>

    @Insert
    suspend fun insert(entity: PendingActionEntity): Long

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun count(): Int

    @Query("DELETE FROM pending_actions")
    suspend fun clearAll()
}
