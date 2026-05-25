package app.phonetube.core.media

object ImageUrlHelper {
    private const val YT_THUMB_BASE = "https://i.ytimg.com/vi/"

    fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> trimmed
        }
    }

    fun videoThumbnail(videoId: String?, quality: String = "hqdefault"): String? {
        if (videoId.isNullOrBlank()) return null
        return "$YT_THUMB_BASE$videoId/$quality.jpg"
    }

    fun resolveVideoThumbnail(
        cardUrl: String?,
        backgroundUrl: String?,
        videoId: String?
    ): String? {
        if (!videoId.isNullOrBlank()) {
            return videoThumbnail(videoId, "hqdefault")
                ?: normalize(cardUrl)
                ?: normalize(backgroundUrl)
        }
        return normalize(cardUrl)
            ?: normalize(backgroundUrl)
    }
}
