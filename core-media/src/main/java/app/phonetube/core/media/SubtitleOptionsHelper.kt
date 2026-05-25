package app.phonetube.core.media

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle

object SubtitleOptionsHelper {
    const val OFF_ID = "off"

    fun subtitleOptions(formatInfo: MediaItemFormatInfo): List<SubtitleOption> {
        val result = ArrayList<SubtitleOption>()
        result.add(SubtitleOption(id = OFF_ID, label = "Off", baseUrl = null))

        val tracks = formatInfo.subtitles?.toList().orEmpty()
        val seen = HashSet<String>()
        for (track in tracks) {
            val option = mapTrack(track) ?: continue
            if (seen.add(option.id)) {
                result.add(option)
            }
        }
        return result
    }

    private fun mapTrack(track: MediaSubtitle): SubtitleOption? {
        val baseUrl = track.baseUrl?.takeIf { it.isNotBlank() } ?: return null
        val label = track.name?.takeIf { it.isNotBlank() }
            ?: track.languageCode?.takeIf { it.isNotBlank() }
            ?: return null
        val id = track.vssId?.takeIf { it.isNotBlank() }
            ?: track.languageCode?.takeIf { it.isNotBlank() }
            ?: label
        return SubtitleOption(
            id = id,
            label = label,
            baseUrl = normalizeTimedTextUrl(baseUrl, track.mimeType),
            languageCode = track.languageCode,
            mimeType = track.mimeType
        )
    }

    fun normalizeTimedTextUrl(url: String, mimeType: String?): String {
        if (!url.contains("timedtext")) return url
        if (url.contains("fmt=")) return url
        val fmt = when {
            mimeType?.contains("vtt", ignoreCase = true) == true -> "vtt"
            mimeType?.contains("ttml", ignoreCase = true) == true -> "ttml"
            mimeType?.contains("srv3", ignoreCase = true) == true -> "srv3"
            else -> "vtt"
        }
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}fmt=$fmt"
    }
}
