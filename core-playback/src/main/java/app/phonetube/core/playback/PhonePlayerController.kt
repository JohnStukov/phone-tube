package app.phonetube.core.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import java.lang.ref.WeakReference
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhonePlayerController(
    context: Context,
    private val repository: YouTubeRepository = YouTubeRepository(context),
    private val sponsorBlockPrefs: SponsorBlockPrefs = SponsorBlockPrefs(context),
    private val playerPrefs: PlayerPrefs = PlayerPrefs(context),
    private val positionStore: PlaybackPositionStore = PlaybackPositionStore.get(context)
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val trackSelector = DefaultTrackSelector()
    private val player: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(appContext, trackSelector)
    private val mediaSourceFactory = SimpleExoMediaSourceFactory(appContext)
    private val subtitleMediaSourceFactory = SubtitleMediaSourceFactory(appContext)
    private var sponsorBlockEngine: SponsorBlockEngine? = null
    private var currentVideoId: String? = null
    private var isLive: Boolean = false
    private var playbackSpeed: Float = 1f
    private var selectedStreamUrl: String? = null
    private var selectedAudioStreamUrl: String? = null
    private var selectedSubtitle: SubtitleOption? = null
    private var attachedPlayerView = WeakReference<PlayerView>(null)
    private val formatCache = mutableMapOf<String, MediaItemFormatInfo>()

    var onSegmentsChanged: ((List<SeekSegment>) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    var onProgressUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onVideoReady: (() -> Unit)? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            sponsorBlockEngine?.tick(player.currentPosition, player.isPlaying)
            notifyProgress()
            mainHandler.postDelayed(this, 1_000)
        }
    }

    private fun notifyProgress() {
        val position = player.currentPosition
        val duration = player.duration
        onProgressUpdate?.invoke(position, duration)
        persistProgress(position, duration)
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
                    Player.STATE_READY -> {
                        if (isLive) {
                            seekToLiveEdge()
                        }
                        if (playWhenReady) {
                            mainHandler.removeCallbacks(tickRunnable)
                            mainHandler.post(tickRunnable)
                        }
                        notifyProgress()
                        onVideoReady?.invoke()
                    }
                    Player.STATE_ENDED -> onPlaybackEnded?.invoke()
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
        this.isLive = isLive
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
                prepareAndPlay(videoId, isLive, keepPositionMs = -1L, percentWatched = percentWatched)
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
                prepareAndPlay(videoId, isLive, keepPositionMs = keepMs)
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
                prepareAndPlay(videoId, isLive, keepPositionMs = keepMs)
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
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
        if (PlaybackRestrictions.blocksPlayback(formatInfo)) {
            onError?.invoke(IllegalStateException(PlaybackRestrictions.LIVE_UNAVAILABLE))
            return
        }
        this.isLive = false
        applyPreferredAudioLanguage(playerPrefs.resolvePreferredAudioLanguage())
        mediaSourceFactory.setPlaybackFormatInfo(formatInfo)
        val durationMs = formatLengthMs(formatInfo.lengthSeconds)
        val url = selectedStreamUrl
        val audioUrl = selectedAudioStreamUrl
        val videoSource = if (!url.isNullOrBlank()) {
            mediaSourceFactory.fromStreamUrls(url, audioUrl)
                ?: mediaSourceFactory.fromFormatInfo(formatInfo)
        } else {
            mediaSourceFactory.fromFormatInfo(formatInfo)
        }
        if (videoSource == null) {
            onError?.invoke(IllegalStateException("No playable stream"))
            return
        }
        val source = subtitleMediaSourceFactory.mergeWithSubtitle(videoSource, selectedSubtitle)
        player.prepare(source)
        player.playbackParameters = PlaybackParameters(playbackSpeed)
        player.playWhenReady = true
        val startMs = when {
            keepPositionMs >= 0L -> keepPositionMs
            else -> positionStore.resolveStartMs(videoId, durationMs, percentWatched)
        }
        if (startMs > 0L) {
            player.seekTo(startMs)
        }
        startSponsorBlock(videoId, isLive = false)
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

    fun getPlayer(): SimpleExoPlayer = player
}
