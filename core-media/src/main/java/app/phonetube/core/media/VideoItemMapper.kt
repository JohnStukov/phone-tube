package app.phonetube.core.media



import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem



object VideoItemMapper {

    fun fromGroups(groups: List<MediaGroup?>?): List<VideoItem> {

        if (groups.isNullOrEmpty()) return emptyList()

        val result = ArrayList<VideoItem>()

        val seen = LinkedHashSet<String>()

        for (group in groups) {

            val mediaItems = group?.mediaItems ?: continue

            for (item in mediaItems) {

                mapItem(item)?.let { video ->

                    if (seen.add(video.videoId)) {

                        result.add(video)

                    }

                }

            }

        }

        return result

    }

    fun dedupe(videos: List<VideoItem>): List<VideoItem> {
        if (videos.isEmpty()) return videos
        val seen = LinkedHashSet<String>(videos.size)
        val result = ArrayList<VideoItem>(videos.size)
        for (video in videos) {
            if (seen.add(video.videoId)) {
                result.add(video)
            }
        }
        return result
    }

    fun merge(existing: List<VideoItem>, more: List<VideoItem>): List<VideoItem> {
        if (more.isEmpty()) return existing
        val seen = existing.map { it.videoId }.toMutableSet()
        val result = ArrayList<VideoItem>(existing.size + more.size)
        result.addAll(existing)
        for (video in more) {
            if (seen.add(video.videoId)) {
                result.add(video)
            }
        }
        return result
    }

    private fun mapItem(item: MediaItem): VideoItem? {

        val videoId = item.videoId ?: return null

        if (videoId.isEmpty()) return null

        val durationLabel = item.badgeText?.takeIf { it.isNotBlank() }

            ?: formatDurationLabel(item.durationMs)

        return VideoItem(

            videoId = videoId,

            title = item.title?.toString().orEmpty().ifEmpty { videoId },

            author = item.author,

            thumbnailUrl = ImageUrlHelper.resolveVideoThumbnail(

                item.cardImageUrl,

                item.backgroundImageUrl,

                videoId

            ),

            durationMs = item.durationMs,

            isLive = item.isLive,

            subtitle = buildSubtitle(item),

            durationLabel = durationLabel.takeIf { it.isNotBlank() },

            channelId = item.channelId

        )

    }



    private fun buildSubtitle(item: MediaItem): String? {

        val second = item.secondTitle?.toString()?.trim()

        if (!second.isNullOrBlank()) return second

        return listOfNotNull(

            item.author?.trim()?.takeIf { it.isNotEmpty() },

            item.productionDate?.trim()?.takeIf { it.isNotEmpty() }

        ).joinToString(" • ").takeIf { it.isNotBlank() }

    }



    private fun formatDurationLabel(durationMs: Long): String {

        if (durationMs <= 0L) return ""

        val totalSeconds = durationMs / 1000

        val hours = totalSeconds / 3600

        val minutes = (totalSeconds % 3600) / 60

        val seconds = totalSeconds % 60

        return if (hours > 0) {

            String.format("%d:%02d:%02d", hours, minutes, seconds)

        } else {

            String.format("%d:%02d", minutes, seconds)

        }

    }

}

