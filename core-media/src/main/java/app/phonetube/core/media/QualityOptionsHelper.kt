package app.phonetube.core.media



import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo

import com.liskovsoft.youtubeapi.formatbuilders.utils.MediaFormatUtils



object QualityOptionsHelper {

    fun qualityOptions(
        formatInfo: MediaItemFormatInfo,
        preferredAudioLanguage: String? = null
    ): List<StreamQualityOption> {

        val result = LinkedHashMap<String, StreamQualityOption>()

        result["Auto"] = StreamQualityOption("Auto", null)



        val adaptive = formatInfo.adaptiveFormats.orEmpty()

        val audioFormats = adaptive.filter { isAudioFormat(it) && !it.url.isNullOrBlank() }



        formatInfo.urlFormats.orEmpty()

            .filter { format ->

                val url = format.url

                !url.isNullOrBlank() && isVideoFormat(format)

            }

            .sortedByDescending { it.height }

            .forEach { format ->

                addOption(result, format, audioUrl = null)

            }



        adaptive

            .filter { format ->

                val url = format.url

                !url.isNullOrBlank() && isVideoFormat(format) && (format.height > 0 || !format.qualityLabel.isNullOrBlank())

            }

            .sortedByDescending { it.height }

            .forEach { format ->

                val audioUrl = pickAudioUrl(audioFormats, format, preferredAudioLanguage)

                addOption(result, format, audioUrl)

            }



        return result.values.toList()

    }



    private fun addOption(

        result: LinkedHashMap<String, StreamQualityOption>,

        format: MediaFormat,

        audioUrl: String?

    ) {

        val label = format.qualityLabel?.takeIf { it.isNotBlank() }

            ?: format.quality?.takeIf { it.isNotBlank() }

            ?: if (format.height > 0) "${format.height}p" else "Video"

        if (!result.containsKey(label)) {

            result[label] = StreamQualityOption(label, format.url, audioUrl)

        }

    }



    private fun pickAudioUrl(
        audioFormats: List<MediaFormat>,
        videoFormat: MediaFormat,
        preferredAudioLanguage: String?
    ): String? {

        if (audioFormats.isEmpty()) return null

        val videoMime = formatMime(videoFormat)

        val compatible = audioFormats.filter { audio ->

            mimeCompatible(videoMime, formatMime(audio))

        }

        var pool = if (compatible.isNotEmpty()) compatible else audioFormats

        if (!preferredAudioLanguage.isNullOrBlank()) {
            val localized = pool.filter {
                AudioLanguageOptionsHelper.formatMatchesLanguage(it, preferredAudioLanguage)
            }
            if (localized.isNotEmpty()) pool = localized
        }

        return pool.maxByOrNull { parseBitrate(it.bitrate) }?.url

    }



    private fun isVideoFormat(format: MediaFormat): Boolean {

        val mime = formatMime(format)

        if (mime != null) {

            return MediaFormatUtils.isVideo(mime)

        }

        return format.height > 0

    }



    private fun isAudioFormat(format: MediaFormat): Boolean {

        val mime = formatMime(format)

        if (mime != null) {

            return MediaFormatUtils.isAudio(mime)

        }

        return format.height <= 0 && format.width <= 0

    }



    private fun formatMime(format: MediaFormat): String? {

        val raw = format.mimeType ?: return null

        return MediaFormatUtils.extractMimeType(format) ?: raw.substringBefore(';').trim()

    }



    private fun mimeCompatible(videoMime: String?, audioMime: String?): Boolean {

        if (videoMime == null || audioMime == null) return true

        val videoFamily = containerFamily(videoMime)

        val audioFamily = containerFamily(audioMime)

        return videoFamily == audioFamily

    }



    private fun containerFamily(mime: String): String = when {

        mime.contains("webm") -> "webm"

        mime.contains("mp4") -> "mp4"

        else -> mime.substringBefore('/')

    }



    private fun parseBitrate(value: String?): Int {

        if (value.isNullOrBlank()) return 0

        return value.toIntOrNull() ?: 0

    }

}

