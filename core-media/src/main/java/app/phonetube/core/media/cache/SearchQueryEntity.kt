package app.phonetube.core.media.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_queries")
data class SearchQueryEntity(
    @PrimaryKey
    val query: String,
    val searchedAtMs: Long
)
