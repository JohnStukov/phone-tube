package app.phonetube.core.playback

data class SeekSegment(
    val startMs: Long,
    val endMs: Long,
    val category: String
)
