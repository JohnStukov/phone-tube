package app.phonetube.core.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.phonetube.core.media.SubtitleOption
import app.phonetube.core.media.YouTubeRepository
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.PlayerView
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
    private val playerPrefs: PlayerPrefs = PlayerPrefs(context)
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val player: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(appContext)
    private val mediaSourceFactory = SimpleExoMediaSourceFactory(appContext)
    private val subtitleMediaSourceFactory = SubtitleMediaSourceFactory(appContext)
    private var sponsorBlockEngine: SponsorBlockEngine? = null
    private var currentVideoId: String? = null
    private var isLive: Boolean = false
    private var playbackSpeed: Float = 1f
    private var selectedStreamUrl: String? = null
    private var selectedAudioStreamUrl: String? = null
    private var selectedSubtitle: SubtitleOption? = null
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
        onProgressUpdate?.invoke(player.currentPosition, player.duration)
    }

    init {
        player.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
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
        playerView.player = player
        playerView.subtitleView?.let { subtitleView ->
            subtitleView.setApplyEmbeddedStyles(true)
            subtitleView.setApplyEmbeddedFontSizes(false)
            subtitleView.setFractionalTextSize(playerPrefs.captionSize.fraction, false)
        }
    }

    fun applyCaptionSize(size: CaptionSize) {
        playerPrefs.captionSize = size
    }

    fun getCaptionSize(): CaptionSize = playerPrefs.captionSize

    fun play(
        videoId: String,
        isLive: Boolean = false,
        streamUrl: String? = null,
        audioStreamUrl: String? = null,
        subtitle: SubtitleOption? = selectedSubtitle
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
                prepareAndPlay(videoId, isLive, keepPositionMs = 0L)
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
        val position = player.currentPosition
        scope.launch {
            try {
                prepareAndPlay(videoId, isLive, keepPositionMs = position)
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
        val position = player.currentPosition
        scope.launch {
            try {
                prepareAndPlay(videoId, isLive, keepPositionMs = position)
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    fun resolvePreferredSubtitle(options: List<SubtitleOption>): SubtitleOption? {
        if (options.isEmpty()) return null
        val preferredId = playerPrefs.preferredSubtitleId
        return options.firstOrNull { it.id == preferredId } ?: options.first()
    }

    private suspend fun prepareAndPlay(videoId: String, isLive: Boolean, keepPositionMs: Long) {
        val formatInfo = loadFormatInfo(videoId)
        val effectiveLive = isLive || formatInfo.isLive || formatInfo.isLiveContent
        this.isLive = effectiveLive
        val url = selectedStreamUrl
        val audioUrl = selectedAudioStreamUrl
        val videoSource = if (effectiveLive) {
            mediaSourceFactory.fromFormatInfo(formatInfo)
        } else if (!url.isNullOrBlank()) {
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
        if (keepPositionMs > 0 && !effectiveLive) {
            player.seekTo(keepPositionMs)
        }
        startSponsorBlock(videoId, effectiveLive)
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
        if (isLive) return
        val duration = player.duration
        val maxPosition = if (duration > 0) duration else Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, maxPosition)
        player.seekTo(target)
    }

    fun seekForward(seconds: Int = 10) {
        seekBy(seconds * 1_000L)
    }

    fun seekBackward(seconds: Int = 10) {
        seekBy(-seconds * 1_000L)
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
