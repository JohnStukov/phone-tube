package app.phonetube.core.playback

import android.content.Context
import app.phonetube.core.media.YouTubeRepository
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit

class SponsorBlockEngine(
    context: Context,
    private val repository: YouTubeRepository,
    private val prefs: SponsorBlockPrefs = SponsorBlockPrefs(context),
    private val onSeek: (Long) -> Unit
) {
    private var segments: List<SponsorSegment> = emptyList()
    private var pollDisposable: Disposable? = null
    private var lastSkipPosMs: Long = 0
    private var videoId: String? = null

    fun release() {
        pollDisposable?.dispose()
        pollDisposable = null
        segments = emptyList()
        videoId = null
    }

    fun loadForVideo(videoId: String, isLive: Boolean) {
        release()
        this.videoId = videoId
        if (!prefs.isEnabled || isLive || prefs.getEnabledCategories().isEmpty()) {
            return
        }

        pollDisposable = repository.getSponsorSegmentsObserve(videoId, prefs.getEnabledCategories())
            .subscribe({ list ->
                segments = list.orEmpty()
                if (segments.isNotEmpty()) {
                    startPolling()
                }
            }, { })
    }

    fun getSeekSegments(): List<SeekSegment> = segments.map {
        SeekSegment(it.startMs, it.endMs, it.category.orEmpty())
    }

    fun onPositionMs(positionMs: Long, isPlaying: Boolean) {
        if (!isPlaying || segments.isEmpty()) return
        val match = findSegmentAt(positionMs, false) ?: return
        val skipTo = match.endMs
        if (lastSkipPosMs == skipTo) return
        lastSkipPosMs = skipTo
        onSeek(skipTo)
    }

    private fun startPolling() {
        pollDisposable?.dispose()
        pollDisposable = io.reactivex.Observable.interval(1, TimeUnit.SECONDS)
            .subscribe { /* position checked from player */ }
    }

    fun tick(positionMs: Long, isPlaying: Boolean) {
        onPositionMs(positionMs, isPlaying)
    }

    private fun findSegmentAt(positionMs: Long, fullMatch: Boolean): SponsorSegment? {
        for (segment in segments) {
            if (isInside(positionMs, segment, fullMatch)) return segment
        }
        return null
    }

    private fun isInside(positionMs: Long, segment: SponsorSegment, fullMatch: Boolean): Boolean {
        return if (fullMatch) {
            positionMs in segment.startMs..segment.endMs
        } else {
            val window = 2_000L
            positionMs >= segment.startMs && positionMs <= minOf(segment.startMs + window, segment.endMs)
        }
    }
}
