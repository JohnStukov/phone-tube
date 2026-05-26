package app.phonetube.core.media

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.youtubeapi.formatbuilders.utils.MediaFormatUtils
import java.util.Locale

object AudioLanguageOptionsHelper {

    fun audioTrackOptions(formatInfo: MediaItemFormatInfo): List<AudioTrackOption> {
        val tracks = LinkedHashMap<String, AudioTrackOption>()
        formatInfo.adaptiveFormats.orEmpty()
            .filter { isAudioFormat(it) }
            .forEach { format ->
                val raw = format.language?.trim().orEmpty()
                val code = normalizeLanguageCode(raw) ?: return@forEach
                if (!tracks.containsKey(code)) {
                    tracks[code] = AudioTrackOption(
                        id = code,
                        label = displayLabel(raw, code),
                        languageCode = code
                    )
                }
            }
        return tracks.values.toList()
    }

    fun resolveSelection(
        options: List<AudioTrackOption>,
        preferredLanguage: String?
    ): AudioTrackOption? {
        if (options.isEmpty()) return null
        if (preferredLanguage.isNullOrBlank()) return options.first()
        val preferred = normalizeLanguageCode(preferredLanguage) ?: preferredLanguage.lowercase()
        return options.firstOrNull { matchesLanguage(it.languageCode, preferred) }
            ?: options.firstOrNull { matchesLanguage(it.label, preferred) }
            ?: options.first()
    }

    fun formatMatchesLanguage(format: MediaFormat, preferredLanguage: String?): Boolean {
        if (preferredLanguage.isNullOrBlank()) return true
        val raw = format.language ?: return false
        return matchesLanguage(raw, preferredLanguage)
    }

    fun normalizeLanguageCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .substringBefore('(')
            .trim()
            .lowercase()
            .replace('_', '-')
        if (cleaned.isBlank()) return null
        val tag = cleaned.substringBefore('-')
        return tag.takeIf { it.length in 2..3 }
    }

    private fun displayLabel(raw: String, code: String): String {
        if (raw.isNotBlank() && raw != code) return raw
        return runCatching {
            Locale.forLanguageTag(code).getDisplayName(Locale.getDefault())
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: code.uppercase()
    }

    private fun matchesLanguage(track: String, preferred: String): Boolean {
        val trackCode = normalizeLanguageCode(track) ?: track.lowercase()
        val prefCode = normalizeLanguageCode(preferred) ?: preferred.lowercase()
        return trackCode == prefCode ||
            trackCode.startsWith(prefCode) ||
            prefCode.startsWith(trackCode)
    }

    private fun isAudioFormat(format: MediaFormat): Boolean {
        val mime = format.mimeType?.let { MediaFormatUtils.extractMimeType(format) ?: it.substringBefore(';').trim() }
        if (mime != null) return MediaFormatUtils.isAudio(mime)
        return format.height <= 0 && format.width <= 0
    }
}
