package app.phonetube.core.playback

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import java.io.InputStream

class SimpleExoMediaSourceFactory(private val context: Context) {
    fun fromFormatInfo(formatInfo: MediaItemFormatInfo): MediaSource? {
        formatInfo.hlsManifestUrl?.takeIf { it.isNotEmpty() }?.let {
            return buildHls(Uri.parse(it))
        }
        val dashUrl = formatInfo.dashManifestUrl
        if (formatInfo.containsDashUrl() && !dashUrl.isNullOrEmpty()) {
            return buildDash(Uri.parse(dashUrl))
        }
        if (formatInfo.isLive || formatInfo.isLiveContent) {
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
        val userAgent = Util.getUserAgent(context, context.packageName)
        val dataSourceFactory = DefaultDataSourceFactory(context, DefaultHttpDataSourceFactory(userAgent))
        return ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
    }

    private fun dataSourceFactory(): DefaultDataSourceFactory {
        val userAgent = Util.getUserAgent(context, context.packageName)
        return DefaultDataSourceFactory(context, userAgent)
    }
}
