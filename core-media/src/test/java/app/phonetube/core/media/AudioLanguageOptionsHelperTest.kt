package app.phonetube.core.media

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLanguageOptionsHelperTest {

    @Test
    fun formatMatchesLanguage_originalSelectsExplicitOriginalTrack() {
        val original = audio("es-US (original)")
        val dubbed = audio("en-US (dubbed-auto)")

        assertTrue(
            AudioLanguageOptionsHelper.formatMatchesLanguage(
                original,
                AudioLanguageOptionsHelper.PREFERRED_ORIGINAL
            )
        )
        assertFalse(
            AudioLanguageOptionsHelper.formatMatchesLanguage(
                dubbed,
                AudioLanguageOptionsHelper.PREFERRED_ORIGINAL
            )
        )
    }

    @Test
    fun formatMatchesLanguage_originalFromUrlXtags() {
        val original = audio(
            language = "",
            url = "https://example.com/videoplayback?xtags=acont%3Doriginal%3Alang%3Des-US"
        )
        assertTrue(
            AudioLanguageOptionsHelper.formatMatchesLanguage(
                original,
                AudioLanguageOptionsHelper.PREFERRED_ORIGINAL
            )
        )
    }

    @Test
    fun formatMatchesLanguage_esMatchesOriginalSpanish() {
        val original = audio("es-US (original)")
        val english = audio("en-US (dubbed-auto)")

        assertTrue(AudioLanguageOptionsHelper.formatMatchesLanguage(original, "es"))
        assertFalse(AudioLanguageOptionsHelper.formatMatchesLanguage(original, "en"))
        assertTrue(AudioLanguageOptionsHelper.formatMatchesLanguage(english, "en"))
    }

    @Test
    fun pickBestMatchingFormat_prefersDubbedOverOriginalForSameLanguage() {
        val dubbedEs = audio("es (dubbed-auto)", bitrate = "64000")
        val originalEs = audio("es-US (original)", bitrate = "128000")

        val picked = AudioLanguageOptionsHelper.pickBestMatchingFormat(listOf(originalEs, dubbedEs), "es")
        assertEquals("64000", picked?.bitrate)
    }

    @Test
    fun pickBestMatchingFormat_originalPicksOriginalTrack() {
        val original = audio("es-US (original)", bitrate = "64000")
        val dubbed = audio("en-US (dubbed-auto)", bitrate = "128000")

        val picked = AudioLanguageOptionsHelper.pickBestMatchingFormat(
            listOf(original, dubbed),
            AudioLanguageOptionsHelper.PREFERRED_ORIGINAL
        )
        assertEquals("64000", picked?.bitrate)
    }

    @Test
    fun resolveSelection_mapsLegacyUndToOriginal() {
        val options = listOf(
            AudioTrackOption("original", "Original", AudioLanguageOptionsHelper.PREFERRED_ORIGINAL),
            AudioTrackOption("en", "English", "en")
        )

        assertEquals("original", AudioLanguageOptionsHelper.resolveSelection(options, "und")?.id)
    }

    @Test
    fun resolveSelection_prefersMatchingLanguage() {
        val options = listOf(
            AudioTrackOption("original", "Original", AudioLanguageOptionsHelper.PREFERRED_ORIGINAL),
            AudioTrackOption("es", "Español", "es"),
            AudioTrackOption("en", "English", "en")
        )

        assertEquals("es", AudioLanguageOptionsHelper.resolveSelection(options, "es")?.id)
        assertEquals("en", AudioLanguageOptionsHelper.resolveSelection(options, "en")?.id)
    }

    private fun audio(
        language: String,
        url: String? = null,
        bitrate: String = "128000"
    ): MediaFormat = TestAudioFormat(language, url, bitrate)

    private class TestAudioFormat(
        private val languageValue: String,
        private val urlValue: String?,
        private val bitrateValue: String
    ) : MediaFormat {
        override fun getFormatType(): Int = MediaFormat.FORMAT_TYPE_DASH
        override fun getUrl(): String? = urlValue
        override fun getMimeType(): String = "audio/mp4"
        override fun getITag(): String? = null
        override fun isDrc(): Boolean = false
        override fun getClen(): String? = null
        override fun getBitrate(): String? = bitrateValue
        override fun getProjectionType(): String? = null
        override fun getXtags(): String? = null
        override fun getWidth(): Int = 0
        override fun getHeight(): Int = 0
        override fun getIndex(): String? = null
        override fun getInit(): String? = null
        override fun getFps(): String? = null
        override fun getLmt(): String? = null
        override fun getQualityLabel(): String? = null
        override fun getFormat(): String? = null
        override fun isOtf(): Boolean = false
        override fun getOtfInitUrl(): String? = null
        override fun getOtfTemplateUrl(): String? = null
        override fun getLanguage(): String = languageValue
        override fun getTargetDurationSec(): Int = 0
        override fun getMaxDvrDurationSec(): Int = 0
        override fun getApproxDurationMs(): Int = 0
        override fun getQuality(): String? = "tiny"
        override fun getSignature(): String? = null
        override fun getAudioSamplingRate(): String? = null
        override fun getSourceUrl(): String? = null
        override fun getSegmentUrlList(): MutableList<String> = mutableListOf()
        override fun getGlobalSegmentList(): MutableList<String> = mutableListOf()
        override fun compareTo(other: MediaFormat?): Int = 0
    }
}
