package app.phonetube.core.playback

import android.content.Context
import android.net.Uri
import app.phonetube.core.media.SubtitleOption
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util

class SubtitleMediaSourceFactory(private val context: Context) {
    private val userAgent = Util.getUserAgent(context, context.packageName)

    fun mergeWithSubtitle(
        videoSource: MediaSource,
        subtitle: SubtitleOption?
    ): MediaSource {
        if (subtitle == null || subtitle.isOff) return videoSource
        val url = subtitle.baseUrl ?: return videoSource
        val mimeType = resolveMimeType(subtitle.mimeType)
        val language = subtitle.languageCode ?: subtitle.label
        val textFormat = Format.createTextSampleFormat(
            subtitle.id,
            mimeType,
            C.SELECTION_FLAG_DEFAULT,
            language
        )
        val dataSourceFactory = DefaultHttpDataSourceFactory(userAgent)
        val subtitleSource = SingleSampleMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.parse(url), textFormat, C.TIME_UNSET)
        return MergingMediaSource(videoSource, subtitleSource)
    }

    private fun resolveMimeType(mimeType: String?): String {
        return when {
            mimeType?.contains("ttml", ignoreCase = true) == true -> MimeTypes.APPLICATION_TTML
            mimeType?.contains("vtt", ignoreCase = true) == true -> MimeTypes.TEXT_VTT
            mimeType?.contains("xml", ignoreCase = true) == true -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT
        }
    }
}
