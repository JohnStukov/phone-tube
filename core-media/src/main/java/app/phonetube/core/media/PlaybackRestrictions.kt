package app.phonetube.core.media

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo

object PlaybackRestrictions {
    const val LIVE_UNAVAILABLE = "live_unavailable"

    fun isLivePlayback(isLive: Boolean, isLiveContent: Boolean = false): Boolean =
        isLive || isLiveContent

    fun isLivePlayback(metadata: VideoMetadata): Boolean =
        isLivePlayback(metadata.isLive, metadata.isLiveContent)

    fun isLivePlayback(format: MediaItemFormatInfo): Boolean =
        isLivePlayback(format.isLive, format.isLiveContent)

    /** Live and broadcasts are playable; keep for legacy call sites. */
    fun blocksPlayback(isLive: Boolean, isLiveContent: Boolean = false): Boolean = false

    fun blocksPlayback(metadata: VideoMetadata): Boolean = false

    fun blocksPlayback(format: MediaItemFormatInfo): Boolean = false
}
