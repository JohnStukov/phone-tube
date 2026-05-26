package app.phonetube.core.playback

import android.content.Context
import android.net.Uri
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
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import java.io.InputStream

class SimpleExoMediaSourceFactory(private val context: Context) {

    private var playbackFormatInfo: MediaItemFormatInfo? = null

    fun setPlaybackFormatInfo(formatInfo: MediaItemFormatInfo?) {
        playbackFormatInfo = formatInfo
    }

    fun fromFormatInfo(formatInfo: MediaItemFormatInfo): MediaSource? {
        playbackFormatInfo = formatInfo
        val isLive = formatInfo.isLive || formatInfo.isLiveContent

        if (isLive) {
            buildDashFromAdaptiveFormats(formatInfo)?.let { return it }
        }

        formatInfo.hlsManifestUrl?.takeIf { it.isNotEmpty() }?.let {
            return buildHls(Uri.parse(it))
        }
        val dashUrl = formatInfo.dashManifestUrl
        if (formatInfo.containsDashUrl() && !dashUrl.isNullOrEmpty()) {
            return buildDash(Uri.parse(dashUrl))
        }
        if (!isLive) {
            formatInfo.createMpdStream()?.let { stream ->
                buildDashFromMpdStream(stream)?.let { return it }
            }
        }
        val urls = formatInfo.createUrlList()
        if (!urls.isNullOrEmpty()) {
            return buildProgressive(Uri.parse(urls.first()))
        }
        return null
    }

    fun fromStreamUrl(url: String): MediaSource? {
        if (url.isBlank()) return null
        return when {
            url.contains(".m3u8", ignoreCase = true) || url.contains("manifest", ignoreCase = true) ->
                buildHls(Uri.parse(url))
            url.contains(".mpd", ignoreCase = true) -> buildDash(Uri.parse(url))
            else -> buildProgressive(Uri.parse(url))
        }
    }

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

    private fun buildHls(uri: Uri): MediaSource {
        return HlsMediaSource.Factory(dataSourceFactory()).createMediaSource(uri)
    }

    private fun buildDash(uri: Uri): MediaSource {
        return DashMediaSource.Factory(dataSourceFactory()).createMediaSource(uri)
    }

    private fun buildDashFromMpdStream(stream: InputStream): MediaSource? {
        return try {
            val manifest = DashManifestParser().parse(
                Uri.parse("https://www.youtube.com"),
                stream
            )
            if (manifest.dynamic) return null
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
        val userAgent = DefaultHeaders.USER_AGENT_WEB
        val httpFactory = DefaultHttpDataSourceFactory(userAgent)
        val props = httpFactory.defaultRequestProperties
        props.set("Referer", DefaultHeaders.REFERER)
        props.set("Origin", "https://www.youtube.com")
        formatInfo?.visitorCookie?.takeIf { it.isNotBlank() }?.let { props.set("Cookie", it) }
        return DefaultDataSourceFactory(context, httpFactory)
    }
}
