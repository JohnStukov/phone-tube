package app.phonetube.core.media.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "home_feed_pages"
)
data class HomeFeedPageEntity(
    @PrimaryKey
    val feed: String,
    val nextPageKey: String?,
    val groupType: Int,
    val fetchedAtMs: Long
)

@Entity(
    tableName = "home_feed_videos",
    primaryKeys = ["feed", "position"],
    indices = [Index(value = ["feed"])]
)
data class HomeFeedVideoEntity(
    val feed: String,
    val position: Int,
    val videoId: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val isLive: Boolean,
    val subtitle: String?,
    val durationLabel: String?,
    val channelId: String?,
    val channelAvatarUrl: String?,
    val percentWatched: Int,
    val isPlaylist: Boolean,
    val playlistId: String?
)
