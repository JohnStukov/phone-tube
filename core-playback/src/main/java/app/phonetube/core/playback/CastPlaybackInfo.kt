package app.phonetube.core.playback

data class CastPlaybackInfo(
    val videoId: String,
    val title: String,
    val subtitle: String?,
    val streamUrl: String,
    val mimeType: String,
    val positionMs: Long,
    val isLive: Boolean
)

interface CastPlaybackSource {
    fun playbackInfo(): CastPlaybackInfo?
    fun pauseLocal()
    fun resumeLocal(positionMs: Long)
}
