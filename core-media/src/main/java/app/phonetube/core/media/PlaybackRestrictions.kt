package app.phonetube.core.media

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo

object PlaybackRestrictions {
    const val LIVE_UNAVAILABLE = "live_unavailable"

    fun blocksPlayback(isLive: Boolean, isLiveContent: Boolean = false): Boolean =
        isLive || isLiveContent

    fun blocksPlayback(metadata: VideoMetadata): Boolean =
        blocksPlayback(metadata.isLive, metadata.isLiveContent)

    fun blocksPlayback(format: MediaItemFormatInfo): Boolean =
        blocksPlayback(format.isLive, format.isLiveContent)
}
