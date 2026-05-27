package app.phonetube.core.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser2
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.youtubeapi.formatbuilders.utils.ITagUtils
import com.liskovsoft.youtubeapi.formatbuilders.utils.MediaFormatUtils
import java.io.InputStream

class SimpleExoMediaSourceFactory(private val context: Context) {

    private val tag = "SimpleExoMediaSourceFactory"
    private var playbackFormatInfo: MediaItemFormatInfo? = null
    private var preferLiveManifest = false

    fun setPlaybackFormatInfo(formatInfo: MediaItemFormatInfo?) {
        playbackFormatInfo = formatInfo
    }

    fun setPreferLiveManifest(value: Boolean) {
        preferLiveManifest = value
    }

    fun fromFormatInfo(formatInfo: MediaItemFormatInfo): MediaSource? {
        playbackFormatInfo = formatInfo
        val isLive = formatInfo.isLive || formatInfo.isLiveContent

        if (isLive) {
            // Live/DVR must use manifest timelines first. Progressive live URLs are unstable and may EOF quickly.
            val dashUrl = formatInfo.dashManifestUrl
            val hlsUrl = formatInfo.hlsManifestUrl
            if (formatInfo.containsDashUrl() && !dashUrl.isNullOrEmpty()) {
                Log.d(tag, if (preferLiveManifest) "live source=dash-manifest-recovery" else "live source=dash-manifest")
                return buildDash(Uri.parse(dashUrl))
            }
            buildLiveMergedProgressive(formatInfo, preferLowestBitrate = true)?.let {
                Log.d(tag, "live source=merged-low-fallback video=${streamTag(it)}")
                return it
            }
            buildLiveMergedProgressive(formatInfo, preferLowestBitrate = false)?.let {
                Log.d(tag, "live source=merged-high-fallback video=${streamTag(it)}")
                return it
            }
            hlsUrl?.takeIf { it.isNotEmpty() }?.let {
                Log.d(tag, if (preferLiveManifest) "live source=hls-recovery-last" else "live source=hls-last")
                return buildHls(Uri.parse(it))
            }
            Log.w(tag, "live source=unavailable formats=${allStreamFormats(formatInfo).size}")
            return null
        } else {
            formatInfo.hlsManifestUrl?.takeIf { it.isNotEmpty() }?.let {
                return buildHls(Uri.parse(it))
            }
            val dashUrl = formatInfo.dashManifestUrl
            if (formatInfo.containsDashUrl() && !dashUrl.isNullOrEmpty()) {
                return buildDash(Uri.parse(dashUrl))
            }
            formatInfo.createMpdStream()?.let { stream ->
                buildDashFromMpdStream(stream)?.let { return it }
            }
        }

        buildLiveMergedProgressive(formatInfo, preferLowestBitrate = false)?.let { return it }

        pickBestProgressiveUrl(formatInfo)?.let { bestUrl ->
            return buildProgressive(Uri.parse(bestUrl))
        }

        val urls = formatInfo.createUrlList()
        if (!urls.isNullOrEmpty()) {
            val first = urls.firstOrNull { it.isNotBlank() } ?: return null
            return buildProgressive(Uri.parse(first))
        }
        return null
    }

    fun fromStreamUrl(url: String): MediaSource? {
        if (url.isBlank()) return null
        return when {
            url.contains(".m3u8", ignoreCase = true) || url.contains("manifest/hls", ignoreCase = true) ->
                buildHls(Uri.parse(url))
            url.contains(".mpd", ignoreCase = true) -> buildDash(Uri.parse(url))
            else -> buildProgressive(Uri.parse(url))
        }
    }

    fun liveProgressiveUrls(
        formatInfo: MediaItemFormatInfo,
        preferLowestBitrate: Boolean = true
    ): Pair<String, String?>? = pickLiveStreamPair(formatInfo, preferLowestBitrate)

    fun fromStreamUrls(videoUrl: String?, audioUrl: String?): MediaSource? {
        val videoSource = videoUrl?.let { fromStreamUrl(it) } ?: return null
        val audio = audioUrl?.takeIf { it.isNotBlank() } ?: return videoSource
        val audioSource = fromStreamUrl(audio) ?: return videoSource
        return MergingMediaSource(videoSource, audioSource)
    }

    private fun buildDashFromAdaptiveFormats(formatInfo: MediaItemFormatInfo): MediaSource? {
        if (!hasPlayableAdaptiveFormats(formatInfo)) return null
        return try {
            val manifest = DashManifestParser2().parse(formatInfo)
            DashMediaSource.Factory(dataSourceFactory()).createMediaSource(manifest)
        } catch (_: Exception) {
            null
        }
    }

    private fun hasPlayableAdaptiveFormats(formatInfo: MediaItemFormatInfo): Boolean {
        return formatInfo.adaptiveFormats.orEmpty().any { !it.url.isNullOrBlank() }
    }

    private fun buildLiveMergedProgressive(
        formatInfo: MediaItemFormatInfo,
        preferLowestBitrate: Boolean = true
    ): MediaSource? {
        val pair = pickLiveStreamPair(formatInfo, preferLowestBitrate) ?: return null
        return fromStreamUrls(pair.first, pair.second)
    }

    private fun pickLiveStreamPair(
        formatInfo: MediaItemFormatInfo,
        preferLowestBitrate: Boolean
    ): Pair<String, String?>? {
        val formats = allStreamFormats(formatInfo)
        if (formats.isEmpty()) return null

        val videos = formats.filter { isVideoFormat(it) && !it.url.isNullOrBlank() }
        if (videos.isEmpty()) return null

        val preferredVideos = videos.filter { isPreferredLiveVideoFormat(it) }
        val videoPool = preferredVideos.ifEmpty { videos }
        val video = selectByBitrate(videoPool, preferLowestBitrate) ?: return null

        val audios = formats.filter { isAudioFormat(it) && !it.url.isNullOrBlank() }
        val audio = selectByBitrate(audios, preferLowestBitrate)

        return video.url!! to audio?.url
    }

    private fun selectByBitrate(
        formats: List<MediaFormat>,
        preferLowestBitrate: Boolean
    ): MediaFormat? {
        if (formats.isEmpty()) return null
        val withKnownBitrate = formats.filter { parseBitrate(it.bitrate) != Int.MAX_VALUE }
        val pool = withKnownBitrate.ifEmpty { formats }
        return if (preferLowestBitrate) {
            pool.minByOrNull { parseBitrate(it.bitrate) }
        } else {
            pool.maxByOrNull { parseBitrate(it.bitrate) }
        }
    }

    private fun allStreamFormats(formatInfo: MediaItemFormatInfo): List<MediaFormat> {
        val merged = LinkedHashMap<String, MediaFormat>()
        formatInfo.adaptiveFormats.orEmpty().forEach { format ->
            val url = format.url ?: return@forEach
            if (url.isNotBlank()) merged[url] = format
        }
        formatInfo.urlFormats.orEmpty().forEach { format ->
            val url = format.url ?: return@forEach
            if (url.isNotBlank()) merged[url] = format
        }
        return merged.values.toList()
    }

    private fun pickBestProgressiveUrl(formatInfo: MediaItemFormatInfo): String? {
        val formats = allStreamFormats(formatInfo)
        if (formats.isEmpty()) return null

        val video = formats
            .filter { isVideoFormat(it) && !it.url.isNullOrBlank() }
            .maxByOrNull { parseBitrate(it.bitrate) }
            ?.url
        if (!video.isNullOrBlank()) return video

        return formats.firstOrNull { !it.url.isNullOrBlank() }?.url
    }

    private fun isVideoFormat(format: MediaFormat): Boolean {
        if (isAudioItag(format)) return false
        val mime = resolvedMime(format)
        if (mime != null && MediaFormatUtils.isVideo(mime)) return true
        return format.height > 0 || format.width > 0
    }

    private fun isAudioFormat(format: MediaFormat): Boolean {
        if (isVideoFormat(format)) return false
        val mime = resolvedMime(format)
        if (mime != null && MediaFormatUtils.isAudio(mime)) return true
        return isAudioItag(format)
    }

    private fun isAudioItag(format: MediaFormat): Boolean {
        return when (format.iTag.orEmpty()) {
            ITagUtils.AUDIO_48K_AAC,
            ITagUtils.AUDIO_128K_AAC,
            ITagUtils.AUDIO_68K_WEBM,
            ITagUtils.AUDIO_89K_WEBM,
            ITagUtils.AUDIO_133K_WEBM,
            ITagUtils.AUDIO_156K_WEBM -> true
            else -> false
        }
    }

    private fun isPreferredLiveVideoFormat(format: MediaFormat): Boolean {
        val mime = resolvedMime(format)?.lowercase().orEmpty()
        val itag = format.iTag.orEmpty()
        if (!mime.startsWith("video/")) return false
        if (!mime.contains("mp4")) return false
        if (itag == "394" || itag == "395" || itag == "396" || itag == "397" || itag == "398" || itag == "399") {
            return false
        }
        return true
    }

    private fun resolvedMime(format: MediaFormat): String? {
        val raw = format.mimeType ?: return MediaFormatUtils.extractMimeType(format)
        return MediaFormatUtils.extractMimeType(format)
            ?: raw.substringBefore(';').trim().takeIf { it.isNotBlank() }
    }

    private fun streamTag(source: MediaSource): String {
        return source.javaClass.simpleName
    }

    private fun parseBitrate(value: String?): Int {
        if (value.isNullOrBlank()) return Int.MAX_VALUE
        return value.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun buildHls(uri: Uri): MediaSource {
        return HlsMediaSource.Factory(dataSourceFactory()).createMediaSource(uri)
    }

    private fun buildDash(uri: Uri): MediaSource {
        return DashMediaSource.Factory(dataSourceFactory()).createMediaSource(uri)
    }

    private fun buildDashFromMpdStream(stream: InputStream, allowDynamic: Boolean = false): MediaSource? {
        return try {
            val manifest = DashManifestParser().parse(
                Uri.parse("https://www.youtube.com"),
                stream
            )
            if (manifest.dynamic && !allowDynamic) return null
            DashMediaSource.Factory(dataSourceFactory()).createMediaSource(manifest)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun buildProgressive(uri: Uri): MediaSource {
        return ExtractorMediaSource.Factory(dataSourceFactory()).createMediaSource(uri)
    }

    private fun dataSourceFactory(): DefaultDataSourceFactory {
        val formatInfo = playbackFormatInfo
        val userAgent = DefaultHeaders.USER_AGENT_ANDROID
        val httpFactory = DefaultHttpDataSourceFactory(userAgent)
        val props = httpFactory.defaultRequestProperties
        val videoId = formatInfo?.videoId?.trim().orEmpty()
        props.set("Accept", "*/*")
        props.set(
            "Referer",
            if (videoId.isNotEmpty()) "https://www.youtube.com/watch?v=$videoId"
            else DefaultHeaders.REFERER
        )
        props.set("Origin", "https://www.youtube.com")
        formatInfo?.visitorCookie?.takeIf { it.isNotBlank() }?.let { props.set("Cookie", it) }
        return DefaultDataSourceFactory(context, httpFactory)
    }
}
