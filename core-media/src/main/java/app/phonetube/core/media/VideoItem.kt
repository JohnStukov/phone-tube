package app.phonetube.core.media

data class VideoItem(
    val videoId: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val isLive: Boolean,
    val subtitle: String? = null,
    val durationLabel: String? = null,
    val channelId: String? = null,
    val channelAvatarUrl: String? = null,
    /** 0–100 desde YouTube si hay sesión; -1 si no aplica. */
    val percentWatched: Int = -1,
    val isPlaylist: Boolean = false,
    val playlistId: String? = null
) {
    val hasWatchProgress: Boolean get() = percentWatched in 1..99

    val stableListKey: String
        get() = when {
            isPlaylist -> "pl_${playlistId ?: videoId}"
            else -> videoId
        }
}
