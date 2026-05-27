package app.phonetube.core.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.phonetube.core.media.PlaybackRestrictions
import app.phonetube.core.media.SubtitleOption
import app.phonetube.core.media.YouTubeRepository
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.lang.ref.WeakReference
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhonePlayerController(
    context: Context,
    private val repository: YouTubeRepository = YouTubeRepository(context),
    private val sponsorBlockPrefs: SponsorBlockPrefs = SponsorBlockPrefs(context),
    private val playerPrefs: PlayerPrefs = PlayerPrefs(context),
    private val positionStore: PlaybackPositionStore = PlaybackPositionStore.get(context)
) {
    private val tag = "PhonePlayerController"
    private val maxPrepareRetries = 2
    private val initialRetryDelayMs = 800L
    private val bufferingTimeoutMs = 12_000L
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val trackSelector = DefaultTrackSelector()
    private val player: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(appContext, trackSelector)
    private val mediaSourceFactory = SimpleExoMediaSourceFactory(appContext)
    private val subtitleMediaSourceFactory = SubtitleMediaSourceFactory(appContext)
    private var sponsorBlockEngine: SponsorBlockEngine? = null
    private var currentVideoId: String? = null
    private var acceptedProgressVideoId: String? = null
    private var isLive: Boolean = false
    private var playbackSpeed: Float = 1f
    private var selectedStreamUrl: String? = null
    private var selectedAudioStreamUrl: String? = null
    private var currentCastUrl: String? = null
    private var currentCastMimeType: String? = null
    private var selectedSubtitle: SubtitleOption? = null
    private var attachedPlayerView = WeakReference<PlayerView>(null)
    private val formatCache = mutableMapOf<String, MediaItemFormatInfo>()
    private var bufferingVideoId: String? = null
    private var bufferingRecoveryTriggered = false
    private var pendingLiveEdgeSeek = false
    private var forceLowBitrateMode = false
    private var liveManifestRecoveryTriggered = false
    private var playbackErrorRecoveryTriggered = false

    var onSegmentsChanged: ((List<SeekSegment>) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    var onProgressUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onVideoReady: (() -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    var onRetryAttempt: ((Int) -> Unit)? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            sponsorBlockEngine?.tick(player.currentPosition, player.isPlaying)
            notifyProgress()
            mainHandler.postDelayed(this, 1_000)
        }
    }

    private val bufferingStallRunnable = Runnable {
        val videoId = bufferingVideoId ?: return@Runnable
        if (bufferingRecoveryTriggered) return@Runnable
        if (currentVideoId != videoId || player.playbackState != Player.STATE_BUFFERING) return@Runnable
        bufferingRecoveryTriggered = true
        Log.w(tag, "Buffer stall detected for $videoId, forcing reload")
        scope.launch {
            runCatching {
                formatCache.remove(videoId)
                selectedStreamUrl = null
                selectedAudioStreamUrl = null
                forceLowBitrateMode = true
                prepareAndPlayWithRetry(videoId, isLive = isLive, keepPositionMs = -1L)
            }.onFailure { error ->
                onError?.invoke(error as? Exception ?: RuntimeException(error))
            }
        }
    }

    private fun scheduleBufferingWatchdog() {
        bufferingVideoId = currentVideoId
        mainHandler.removeCallbacks(bufferingStallRunnable)
        mainHandler.postDelayed(bufferingStallRunnable, bufferingTimeoutMs)
    }

    private fun clearBufferingWatchdog() {
        mainHandler.removeCallbacks(bufferingStallRunnable)
    }

    private fun notifyProgress() {
        val videoId = currentVideoId
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        if (videoId != null && videoId == acceptedProgressVideoId) {
            if (duration > 0L) {
                onProgressUpdate?.invoke(position, duration)
            } else {
                onProgressUpdate?.invoke(position, 0L)
            }
        }
        persistProgress(position, duration)
    }

    private fun notifyProgressReset() {
        acceptedProgressVideoId = null
        onProgressUpdate?.invoke(0L, 0L)
    }

    private fun persistProgress(positionMs: Long, durationMs: Long) {
        if (isLive) return
        val videoId = currentVideoId ?: return
        positionStore.savePositionMs(videoId, positionMs, durationMs)
    }

    init {
        applyPreferredAudioLanguage(playerPrefs.resolvePreferredAudioLanguage())
        player.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        onBufferingChanged?.invoke(true)
                        scheduleBufferingWatchdog()
                    }
                    Player.STATE_READY -> {
                        clearBufferingWatchdog()
                        bufferingRecoveryTriggered = false
                        liveManifestRecoveryTriggered = false
                        playbackErrorRecoveryTriggered = false
                        onBufferingChanged?.invoke(false)
                        acceptedProgressVideoId = currentVideoId
                        if (isLive && pendingLiveEdgeSeek) {
                            seekToLiveEdge()
                            pendingLiveEdgeSeek = false
                        }
                        if (playWhenReady) {
                            mainHandler.removeCallbacks(tickRunnable)
                            mainHandler.post(tickRunnable)
                        }
                        notifyProgress()
                        onVideoReady?.invoke()
                    }
                    Player.STATE_ENDED -> {
                        clearBufferingWatchdog()
                        acceptedProgressVideoId = null
                        if (isLive) {
                            val endedTooSoon = player.currentPosition in 0L..3_000L
                            val stillSameVideo = currentVideoId != null
                            if (!liveManifestRecoveryTriggered && endedTooSoon && stillSameVideo) {
                                liveManifestRecoveryTriggered = true
                                val videoId = currentVideoId ?: return
                                Log.w(tag, "Live ended too soon for $videoId, retrying with manifest-first source")
                                scope.launch {
                                    runCatching {
                                        selectedStreamUrl = null
                                        selectedAudioStreamUrl = null
                                        forceLowBitrateMode = false
                                        mediaSourceFactory.setPreferLiveManifest(true)
                                        prepareAndPlayWithRetry(videoId, isLive = true, keepPositionMs = -1L)
                                    }.onFailure { error ->
                                        onError?.invoke(error as? Exception ?: RuntimeException(error))
                                    }
                                }
                            }
                        } else {
                            onPlaybackEnded?.invoke()
                        }
                    }
                }
            }

            override fun onPlayerError(error: com.google.android.exoplayer2.ExoPlaybackException) {
                val videoId = currentVideoId
                if (videoId.isNullOrBlank()) {
                    onError?.invoke(error)
                    return
                }
                if (!isForbiddenPlaybackException(error) || playbackErrorRecoveryTriggered) {
                    onError?.invoke(error)
                    return
                }

                playbackErrorRecoveryTriggered = true
                Log.w(tag, "Playback 403 for $videoId, reloading source")
                scope.launch {
                    runCatching {
                        formatCache.remove(videoId)
                        selectedStreamUrl = null
                        selectedAudioStreamUrl = null
                        mediaSourceFactory.setPreferLiveManifest(isLive)
                        prepareAndPlayWithRetry(
                            videoId = videoId,
                            isLive = isLive,
                            keepPositionMs = if (isLive) -1L else player.currentPosition,
                        )
                    }.onFailure { recoveryError ->
                        onError?.invoke(recoveryError as? Exception ?: RuntimeException(recoveryError))
                    }
                }
            }
        })
    }

    fun attachPlayerView(playerView: PlayerView) {
        attachedPlayerView = WeakReference(playerView)
        playerView.player = player
        playerView.subtitleView?.let { configureSubtitleView(it) }
    }

    fun applyCaptionSize(size: CaptionSize) {
        playerPrefs.captionSize = size
        attachedPlayerView.get()?.subtitleView?.setFractionalTextSize(size.fraction, false)
    }

    private fun configureSubtitleView(subtitleView: com.google.android.exoplayer2.ui.SubtitleView) {
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setBottomPaddingFraction(0.1f)
        val padPx = (16f * appContext.resources.displayMetrics.density).toInt()
        subtitleView.setPadding(padPx, 0, padPx, 0)
        subtitleView.setFractionalTextSize(playerPrefs.captionSize.fraction, false)
    }

    fun getCaptionSize(): CaptionSize = playerPrefs.captionSize

    fun play(
        videoId: String,
        isLive: Boolean = false,
        streamUrl: String? = null,
        audioStreamUrl: String? = null,
        subtitle: SubtitleOption? = selectedSubtitle,
        percentWatched: Int = -1
    ) {
        currentVideoId = videoId
        bufferingRecoveryTriggered = false
        playbackErrorRecoveryTriggered = false
        clearBufferingWatchdog()
        this.isLive = isLive
        pendingLiveEdgeSeek = isLive
        notifyProgressReset()
        if (streamUrl != null) {
            selectedStreamUrl = streamUrl
            selectedAudioStreamUrl = audioStreamUrl
        } else {
            selectedStreamUrl = null
            selectedAudioStreamUrl = null
        }
        if (subtitle != null) {
            selectedSubtitle = subtitle
        }
        scope.launch {
            try {
                prepareAndPlayWithRetry(videoId, isLive, keepPositionMs = -1L, percentWatched = percentWatched)
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    fun setStreamUrl(
        videoId: String,
        isLive: Boolean,
        streamUrl: String?,
        audioStreamUrl: String? = null
    ) {
        selectedStreamUrl = streamUrl
        selectedAudioStreamUrl = audioStreamUrl
        val keepMs = if (isLive) -1L else player.currentPosition
        scope.launch {
            try {
                prepareAndPlayWithRetry(videoId, isLive, keepPositionMs = keepMs)
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    fun prefetch(videoId: String) {
        if (videoId.isBlank() || formatCache.containsKey(videoId)) return
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    formatCache[videoId] = repository.getFormatInfo(videoId)
                }
            }
        }
    }

    fun setSubtitle(subtitle: SubtitleOption?) {
        selectedSubtitle = subtitle
        playerPrefs.preferredSubtitleId = subtitle?.id
        val videoId = currentVideoId ?: return
        val keepMs = if (isLive) -1L else player.currentPosition
        scope.launch {
            try {
                prepareAndPlayWithRetry(videoId, isLive, keepPositionMs = keepMs)
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    private suspend fun prepareAndPlayWithRetry(
        videoId: String,
        isLive: Boolean,
        keepPositionMs: Long,
        percentWatched: Int = -1
    ) {
        var retryDelayMs = initialRetryDelayMs
        var attempt = 0
        while (true) {
            try {
                prepareAndPlay(videoId, isLive, keepPositionMs, percentWatched)
                return
            } catch (e: Exception) {
                if (attempt >= maxPrepareRetries || !isRecoverablePlaybackError(e)) {
                    throw e
                }
                if (isForbiddenStreamError(e)) {
                    formatCache.remove(videoId)
                    selectedStreamUrl = null
                    selectedAudioStreamUrl = null
                }
                onRetryAttempt?.invoke(attempt + 1)
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(3_000L)
                attempt++
            }
        }
    }

    private fun isRecoverablePlaybackError(error: Exception): Boolean {
        if (error is IOException) return true
        val message = error.message.orEmpty()
        if (message == PlaybackRestrictions.LIVE_UNAVAILABLE) return false
        if (message.equals("No playable stream", ignoreCase = true)) return false
        if (message.contains("403")) return true
        return message.contains("network", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true)
    }

    private fun isForbiddenStreamError(error: Exception): Boolean =
        error.message?.contains("403") == true ||
            error.cause?.message?.contains("403") == true

    private fun isForbiddenPlaybackException(error: com.google.android.exoplayer2.ExoPlaybackException): Boolean {
        if (error.message?.contains("403") == true) return true
        val source = error.sourceException
        if (source?.message?.contains("403") == true) return true
        return source?.cause?.message?.contains("403") == true
    }

    /** Al volver a la pantalla del reproductor con el mismo directo ya cargado. */
    fun alignLivePlaybackToEdge() {
        if (!isLive) return
        seekToLiveEdge()
    }

    fun resolvePreferredSubtitle(options: List<SubtitleOption>): SubtitleOption? {
        if (options.isEmpty()) return null
        val preferredId = playerPrefs.preferredSubtitleId
        return options.firstOrNull { it.id == preferredId } ?: options.first()
    }

    fun applyPreferredAudioLanguage(languageCode: String?) {
        val normalized = languageCode?.let { Util.normalizeLanguageCode(it) }
        trackSelector.parameters = trackSelector.parameters.buildUpon()
            .setPreferredAudioLanguage(normalized)
            .build()
    }

    fun setAudioLanguage(languageCode: String) {
        playerPrefs.audioLanguageMode = AudioLanguageMode.MANUAL
        playerPrefs.audioLanguageCode = languageCode
        applyPreferredAudioLanguage(languageCode)
    }

    private suspend fun prepareAndPlay(
        videoId: String,
        isLive: Boolean,
        keepPositionMs: Long,
        percentWatched: Int = -1
    ) {
        val formatInfo = loadFormatInfo(videoId)
        val resolvedLive = resolveLivePlayback(isLive, formatInfo)
        if (resolvedLive) {
            throw IllegalStateException(PlaybackRestrictions.LIVE_UNAVAILABLE)
        }
        this.isLive = resolvedLive
        pendingLiveEdgeSeek = resolvedLive
        if (!resolvedLive) {
            mediaSourceFactory.setPreferLiveManifest(false)
        }
        if (forceLowBitrateMode) {
            trackSelector.parameters = trackSelector.parameters
                .buildUpon()
                .setForceLowestBitrate(true)
                .build()
        } else {
            trackSelector.parameters = trackSelector.parameters
                .buildUpon()
                .setForceLowestBitrate(false)
                .build()
        }
        val forcedLow = if (forceLowBitrateMode) {
            mediaSourceFactory.liveProgressiveUrls(formatInfo, preferLowestBitrate = true)
        } else {
            null
        }
        val url = forcedLow?.first ?: selectedStreamUrl
        val audioUrl = forcedLow?.second ?: selectedAudioStreamUrl
        if (resolvedLive) {
            val merged = if (url.isNullOrBlank()) {
                mediaSourceFactory.liveProgressiveUrls(formatInfo, preferLowestBitrate = forceLowBitrateMode)
            } else {
                url to audioUrl
            }
            Log.d(
                tag,
                "prepare live videoId=$videoId forceLow=$forceLowBitrateMode " +
                    "selected video=${streamTag(url)} audio=${streamTag(audioUrl)} " +
                    "merged video=${streamTag(merged?.first)} audio=${streamTag(merged?.second)}"
            )
        }
        val castTarget = resolveCastTarget(formatInfo, url)
        currentCastUrl = castTarget.first
        currentCastMimeType = castTarget.second
        applyPreferredAudioLanguage(playerPrefs.resolvePreferredAudioLanguage())
        mediaSourceFactory.setPlaybackFormatInfo(formatInfo)
        val durationMs = formatLengthMs(formatInfo.lengthSeconds)
        val videoSource = when {
            resolvedLive && url.isNullOrBlank() ->
                mediaSourceFactory.fromFormatInfo(formatInfo)
            !url.isNullOrBlank() ->
                mediaSourceFactory.fromStreamUrls(url, audioUrl)
                    ?: mediaSourceFactory.fromFormatInfo(formatInfo)
            else ->
                mediaSourceFactory.fromFormatInfo(formatInfo)
        }
        if (videoSource == null) {
            onError?.invoke(IllegalStateException("No playable stream"))
            return
        }
        val source = subtitleMediaSourceFactory.mergeWithSubtitle(videoSource, selectedSubtitle)
        notifyProgressReset()
        player.prepare(source)
        player.playbackParameters = PlaybackParameters(playbackSpeed)
        player.playWhenReady = true
        val startMs = when {
            keepPositionMs >= 0L -> keepPositionMs
            else -> positionStore.resolveStartMs(videoId, durationMs, percentWatched)
        }
        if (resolvedLive) {
            seekToLiveEdge()
        } else if (startMs > 0L) {
            player.seekTo(startMs)
        }
        startSponsorBlock(videoId, isLive = resolvedLive)
    }

    private fun streamTag(url: String?): String {
        if (url.isNullOrBlank()) return "none"
        val itag = Regex("itag=(\\d+)").find(url)?.groupValues?.getOrNull(1)
        val mime = Regex("mime=([^&]+)").find(url)?.groupValues?.getOrNull(1)
        return when {
            itag != null -> "itag=$itag"
            mime != null -> mime
            else -> "url"
        }
    }

    private fun seekToLiveEdge() {
        if (!isLive) return
        val duration = player.duration
        when {
            duration > 0 && duration != C.TIME_UNSET -> player.seekTo(duration)
            else -> player.seekToDefaultPosition()
        }
    }

    private fun formatLengthMs(lengthSeconds: String?): Long {
        val seconds = lengthSeconds?.trim()?.toLongOrNull() ?: return 0L
        return seconds.coerceAtLeast(0L) * 1_000L
    }

    private fun resolveLivePlayback(requestedLive: Boolean, formatInfo: MediaItemFormatInfo): Boolean {
        val durationSec = formatInfo.lengthSeconds?.trim()?.toLongOrNull() ?: 0L
        val formatLive = formatInfo.isLive || formatInfo.isLiveContent
        val liveUrlHint = hasLiveUrlHint(formatInfo)
        val hasLiveManifest = hasManifest(formatInfo.hlsManifestUrl) || hasManifest(formatInfo.dashManifestUrl)

        if (formatLive) {
            // Hay respuestas de YouTube donde marca live aunque el stream ya es VOD.
            if (durationSec > 0L && !liveUrlHint && !hasLiveManifest) {
                Log.w(tag, "Ignoring stale live flags for VOD videoId=${formatInfo.videoId}")
                return false
            }
            return true
        }

        if (liveUrlHint) return true

        if (requestedLive && durationSec > 0L) {
            Log.w(tag, "Live hint ignored for VOD format videoId=${formatInfo.videoId}")
            return false
        }
        if (requestedLive && hasLiveManifest && durationSec <= 0L) return true

        return requestedLive
    }

    private fun hasLiveUrlHint(formatInfo: MediaItemFormatInfo): Boolean {
        val adaptiveHasLive = formatInfo.adaptiveFormats.orEmpty()
            .asSequence()
            .mapNotNull { it.url }
            .any { url ->
                url.contains("live=1", ignoreCase = true) ||
                    url.contains("yt_live_broadcast", ignoreCase = true)
            }
        if (adaptiveHasLive) return true

        val hls = formatInfo.hlsManifestUrl.orEmpty()
        if (hls.contains("live=1", ignoreCase = true) || hls.contains("yt_live_broadcast", ignoreCase = true)) {
            return true
        }

        val dash = formatInfo.dashManifestUrl.orEmpty()
        return dash.contains("live=1", ignoreCase = true) || dash.contains("yt_live_broadcast", ignoreCase = true)
    }

    private fun hasManifest(url: String?): Boolean = !url.isNullOrBlank()

    private suspend fun loadFormatInfo(videoId: String): MediaItemFormatInfo {
        formatCache[videoId]?.let { return it }
        return repository.getFormatInfo(videoId).also { formatCache[videoId] = it }
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun getPlaybackSpeed(): Float = playbackSpeed

    private fun startSponsorBlock(videoId: String, isLive: Boolean) {
        sponsorBlockEngine?.release()
        val engine = SponsorBlockEngine(appContext, repository, sponsorBlockPrefs) { skipMs ->
            mainHandler.post { player.seekTo(skipMs) }
        }
        sponsorBlockEngine = engine
        engine.loadForVideo(videoId, isLive)
        scope.launch {
            try {
                val segments = repository.getSponsorSegments(videoId, sponsorBlockPrefs.getEnabledCategories())
                onSegmentsChanged?.invoke(segments.map { SeekSegment(it.startMs, it.endMs, it.category.orEmpty()) })
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        mainHandler.removeCallbacks(tickRunnable)
        clearBufferingWatchdog()
        forceLowBitrateMode = false
        liveManifestRecoveryTriggered = false
        pendingLiveEdgeSeek = false
        mediaSourceFactory.setPreferLiveManifest(false)
        sponsorBlockEngine?.release()
        sponsorBlockEngine = null
        scope.cancel()
        player.release()
    }

    fun getSponsorBlockPrefs(): SponsorBlockPrefs = sponsorBlockPrefs

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = player.currentPosition

    fun getDuration(): Long = player.duration

    fun isPlaying(): Boolean = player.isPlaying

    fun seekBy(deltaMs: Long) {
        val duration = player.duration
        val maxPosition = when {
            isLive && duration > 0 && duration != C.TIME_UNSET -> duration
            !isLive && duration > 0 -> duration
            else -> if (isLive) return else Long.MAX_VALUE
        }
        val target = (player.currentPosition + deltaMs).coerceIn(0L, maxPosition)
        player.seekTo(target)
    }

    fun seekForward(seconds: Int = 10) {
        seekBy(seconds * 1_000L)
    }

    fun seekBackward(seconds: Int = 10) {
        seekBy(-seconds * 1_000L)
    }

    fun setLooping(enabled: Boolean) {
        player.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun togglePlayPause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun getCurrentVideoId(): String? = currentVideoId

    fun getCastPlaybackInfo(title: String, author: String?): CastPlaybackInfo? {
        val videoId = currentVideoId ?: return null
        val streamUrl = currentCastUrl ?: return null
        val mimeType = currentCastMimeType ?: guessMimeTypeFromUrl(streamUrl)
        return CastPlaybackInfo(
            videoId = videoId,
            title = title.ifBlank { videoId },
            subtitle = author,
            streamUrl = streamUrl,
            mimeType = mimeType,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            isLive = isLive
        )
    }

    private fun resolveCastTarget(
        formatInfo: MediaItemFormatInfo,
        streamUrl: String?
    ): Pair<String?, String?> {
        streamUrl?.takeIf { it.isNotBlank() }?.let { url ->
            return url to guessMimeTypeFromUrl(url)
        }
        formatInfo.hlsManifestUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            return url to "application/x-mpegURL"
        }
        val dashUrl = formatInfo.dashManifestUrl
        if (formatInfo.containsDashUrl() && !dashUrl.isNullOrEmpty()) {
            return dashUrl to "application/dash+xml"
        }
        formatInfo.createUrlList()?.firstOrNull()?.let { url ->
            return url to "video/mp4"
        }
        return null to null
    }

    private fun guessMimeTypeFromUrl(url: String): String = when {
        url.contains(".m3u8", ignoreCase = true) || url.contains("manifest", ignoreCase = true) ->
            "application/x-mpegURL"
        url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
        else -> "video/mp4"
    }

    fun getPlayer(): SimpleExoPlayer = player
}
