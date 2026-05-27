package app.phonetube.core.media

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.youtubeapi.formatbuilders.utils.MediaFormatUtils
import java.net.URLDecoder
import java.util.Locale

object AudioLanguageOptionsHelper {
    const val PREFERRED_ORIGINAL = "original"

    private const val VARIANT_MAIN = "main"
    private const val VARIANT_ORIGINAL = "original"
    private const val VARIANT_DUBBED = "dubbed"
    private const val VARIANT_DUBBED_AUTO = "dubbed-auto"
    private const val VARIANT_SECONDARY = "secondary"
    private const val VARIANT_DESCRIPTIVE = "descriptive"

    val ORDERED_LANGUAGE_CODES = listOf(
        "en", "es", "pt", "fr", "de", "it", "ja", "ko", "ru", "ar", "hi", "zh"
    )

    fun audioTrackOptions(formatInfo: MediaItemFormatInfo): List<AudioTrackOption> {
        val parsed = parseAllFormats(formatInfo)
        if (parsed.isEmpty()) return emptyList()

        val options = LinkedHashMap<String, AudioTrackOption>()

        if (hasOriginalTrack(parsed)) {
            options[PREFERRED_ORIGINAL] = AudioTrackOption(
                id = PREFERRED_ORIGINAL,
                label = "Original",
                languageCode = PREFERRED_ORIGINAL
            )
        }

        val languagesInVideo = parsed
            .mapNotNull { it.code?.substringBefore('-')?.takeIf { code -> code.isNotBlank() } }
            .distinct()

        val ordered = ORDERED_LANGUAGE_CODES.filter { it in languagesInVideo } +
            languagesInVideo.filter { it !in ORDERED_LANGUAGE_CODES }.sorted()

        for (lang in ordered) {
            options[lang] = AudioTrackOption(
                id = lang,
                label = languageDisplayName(lang),
                languageCode = lang
            )
        }

        return options.values.toList()
    }

    fun resolveSelection(
        options: List<AudioTrackOption>,
        preferredLanguage: String?
    ): AudioTrackOption? {
        if (options.isEmpty()) return null
        if (preferredLanguage.isNullOrBlank()) {
            return options.firstOrNull { it.languageCode == PREFERRED_ORIGINAL } ?: options.first()
        }

        val preferred = normalizePreferred(preferredLanguage)

        options.firstOrNull { it.languageCode == preferred }?.let { return it }
        options.firstOrNull { it.id == preferred }?.let { return it }
        options.firstOrNull { matchesLanguage(it.languageCode, preferred) }?.let { return it }
        return options.firstOrNull { it.languageCode == PREFERRED_ORIGINAL } ?: options.first()
    }

    fun formatMatchesLanguage(format: MediaFormat, preferredLanguage: String?): Boolean {
        if (preferredLanguage.isNullOrBlank()) return true
        val preferred = normalizePreferred(preferredLanguage)
        val parsed = parseFormat(format)
        return matchesPreferred(parsed, preferred)
    }

    private fun enrichParsedFormats(formats: List<MediaFormat>): Map<MediaFormat, ParsedAudioFormat> {
        return formats.associateWith { parseFormat(it) }
    }

    private fun matchesPreferred(
        parsed: ParsedAudioFormat,
        preferred: String
    ): Boolean {
        if (preferred == PREFERRED_ORIGINAL) {
            return isOriginalTrack(parsed)
        }
        val actualCode = parsed.code ?: return false
        return matchesLanguage(actualCode, preferred)
    }

    fun pickBestMatchingFormat(
        formats: List<MediaFormat>,
        preferredLanguage: String?
    ): MediaFormat? {
        if (formats.isEmpty()) return null
        if (preferredLanguage.isNullOrBlank()) {
            return formats.maxByOrNull { parseBitrate(it.bitrate) }
        }

        val parsedByFormat = enrichParsedFormats(formats)
        val preferred = normalizePreferred(preferredLanguage)
        val matching = formats.filter { format ->
            matchesPreferred(parsedByFormat[format] ?: parseFormat(format), preferred)
        }
        if (matching.isEmpty()) return null

        return matching.maxWithOrNull(
            compareBy(
                { variantPriority(parsedByFormat[it] ?: parseFormat(it), preferred) },
                { parseBitrate(it.bitrate) }
            )
        )
    }

    fun normalizeLanguageCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .substringBefore('(')
            .trim()
            .lowercase()
            .replace('_', '-')
        if (cleaned.isBlank()) return null
        val parts = cleaned.split('-')
        val primary = parts.firstOrNull().orEmpty()
        if (primary.length !in 2..3) return null
        if (parts.drop(1).any { it.isBlank() || it.length > 8 }) return null
        return cleaned
    }

    private fun parseAllFormats(formatInfo: MediaItemFormatInfo): List<ParsedAudioFormat> {
        return formatInfo.adaptiveFormats.orEmpty()
            .filter { isAudioFormat(it) }
            .map { parseFormat(it) }
    }

    private fun parseFormat(format: MediaFormat): ParsedAudioFormat {
        val raw = format.language?.trim().orEmpty()
        val urlTags = parseXtagsFromUrl(format.url)

        val code = normalizeLanguageCode(raw) ?: urlTags.lang?.let { normalizeLanguageCode(it) }
        val variantFromRaw = detectVariant(raw)
        val variant = when {
            variantFromRaw != VARIANT_MAIN -> variantFromRaw
            urlTags.acont != null -> normalizeVariant(urlTags.acont)
            else -> VARIANT_MAIN
        }

        return ParsedAudioFormat(code = code, variant = variant)
    }

    private data class ParsedAudioFormat(
        val code: String?,
        val variant: String
    )

    private data class XtagInfo(
        val lang: String?,
        val acont: String?
    )

    private fun parseXtagsFromUrl(url: String?): XtagInfo {
        if (url.isNullOrBlank()) return XtagInfo(null, null)
        val encoded = Regex("[?&]xtags=([^&]+)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?: return XtagInfo(null, null)

        val decoded = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrDefault(encoded)
        val normalized = decoded.replace(':', '&').replace(';', '&')
        val params = normalized.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2) pieces[0].trim() to pieces[1].trim() else null
            }
            .toMap()

        return XtagInfo(
            lang = params["lang"],
            acont = params["acont"]
        )
    }

    private fun hasOriginalTrack(parsed: List<ParsedAudioFormat>): Boolean {
        if (parsed.any { it.variant == VARIANT_ORIGINAL }) return true
        if (parsed.any { it.variant == VARIANT_DUBBED || it.variant == VARIANT_DUBBED_AUTO }) return true
        return parsed.all { it.code == null }
    }

    private fun isOriginalTrack(track: ParsedAudioFormat): Boolean {
        return track.variant == VARIANT_ORIGINAL
    }

    private fun variantPriority(parsed: ParsedAudioFormat, preferred: String): Int {
        if (preferred == PREFERRED_ORIGINAL) {
            return when (parsed.variant) {
                VARIANT_ORIGINAL -> 3
                VARIANT_MAIN -> 1
                else -> 0
            }
        }
        return when (parsed.variant) {
            VARIANT_DUBBED_AUTO -> 4
            VARIANT_DUBBED -> 3
            VARIANT_MAIN -> 2
            VARIANT_ORIGINAL -> 1
            else -> 0
        }
    }

    private fun normalizePreferred(raw: String): String {
        val lowered = raw.trim().lowercase()
        return when {
            lowered == "und" || lowered.startsWith("und:") -> PREFERRED_ORIGINAL
            lowered == PREFERRED_ORIGINAL || lowered.endsWith(":$VARIANT_ORIGINAL") -> PREFERRED_ORIGINAL
            else -> lowered.substringBefore(':')
        }
    }

    private fun normalizeVariant(raw: String): String = when (raw.lowercase()) {
        VARIANT_DUBBED_AUTO -> VARIANT_DUBBED_AUTO
        VARIANT_DUBBED -> VARIANT_DUBBED
        VARIANT_ORIGINAL -> VARIANT_ORIGINAL
        VARIANT_SECONDARY -> VARIANT_SECONDARY
        VARIANT_DESCRIPTIVE -> VARIANT_DESCRIPTIVE
        else -> VARIANT_MAIN
    }

    private fun languageDisplayName(code: String): String =
        runCatching {
            Locale.forLanguageTag(code).getDisplayName(Locale.getDefault())
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: code.uppercase()

    private fun matchesLanguage(actualCode: String, preferred: String): Boolean {
        val actualPrimary = actualCode.substringBefore('-')
        val prefPrimary = preferred.substringBefore('-')
        return actualCode == preferred ||
            actualCode.startsWith("$preferred-") ||
            preferred.startsWith("$actualPrimary-") ||
            actualPrimary == prefPrimary
    }

    private fun detectVariant(raw: String): String {
        if (raw.isBlank()) return VARIANT_MAIN
        val lowered = raw.lowercase()
        return when {
            lowered.contains(VARIANT_DUBBED_AUTO) -> VARIANT_DUBBED_AUTO
            lowered.contains(VARIANT_DUBBED) -> VARIANT_DUBBED
            lowered.contains(VARIANT_SECONDARY) -> VARIANT_SECONDARY
            lowered.contains(VARIANT_DESCRIPTIVE) -> VARIANT_DESCRIPTIVE
            lowered.contains(VARIANT_ORIGINAL) -> VARIANT_ORIGINAL
            else -> VARIANT_MAIN
        }
    }

    private fun parseBitrate(raw: String?): Int = raw?.toIntOrNull() ?: 0

    private fun isAudioFormat(format: MediaFormat): Boolean {
        val mime = format.mimeType?.let { MediaFormatUtils.extractMimeType(format) ?: it.substringBefore(';').trim() }
        if (mime != null) return MediaFormatUtils.isAudio(mime)
        return format.height <= 0 && format.width <= 0
    }
}
