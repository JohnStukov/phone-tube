package app.phonetube.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.phonetube.cast.PhoneTubeCastController
import app.phonetube.core.media.YouTubeRepository
import app.phonetube.core.playback.CastPlaybackInfo
import app.phonetube.core.playback.CastPlaybackSource
import app.phonetube.core.playback.PhonePlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.update

enum class PlayerDisplayMode {
    NONE,
    FULL,
    MINI
}

data class ActivePlayback(
    val videoId: String,
    val isLive: Boolean,
    val title: String,
    val author: String?,
    val mode: PlayerDisplayMode,
    val offsetInitialized: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

@HiltViewModel
class PlaybackHostViewModel @Inject constructor(
    application: Application,
    private val repository: YouTubeRepository,
    private val castController: PhoneTubeCastController
) : AndroidViewModel(application) {

    private var controller: PhonePlayerController? = null
    private val _session = MutableStateFlow<ActivePlayback?>(null)
    val session: StateFlow<ActivePlayback?> = _session.asStateFlow()

    init {
        castController.registerPlaybackSource(object : CastPlaybackSource {
            override fun playbackInfo(): CastPlaybackInfo? {
                val session = _session.value ?: return null
                return getOrCreateController().getCastPlaybackInfo(session.title, session.author)
            }

            override fun pauseLocal() {
                controller?.pause()
            }

            override fun resumeLocal(positionMs: Long) {
                val active = controller ?: return
                active.seekTo(positionMs)
                active.getPlayer().playWhenReady = true
            }
        })
    }

    fun getOrCreateController(): PhonePlayerController {
        val existing = controller
        if (existing != null) return existing
        return PhonePlayerController(getApplication(), repository).also { controller = it }
    }

    fun onEnterPlayerScreen(
        videoId: String,
        isLive: Boolean,
        title: String,
        author: String?
    ) {
        val previous = _session.value
        _session.value = ActivePlayback(
            videoId = videoId,
            isLive = isLive,
            title = title,
            author = author,
            mode = PlayerDisplayMode.FULL,
            offsetInitialized = previous?.offsetInitialized == true,
            offsetX = previous?.offsetX ?: 0f,
            offsetY = previous?.offsetY ?: 0f
        )
    }

    fun minimize(videoId: String, isLive: Boolean, title: String, author: String?) {
        val previous = _session.value
        _session.value = ActivePlayback(
            videoId = videoId,
            isLive = isLive,
            title = title.ifBlank { videoId },
            author = author,
            mode = PlayerDisplayMode.MINI,
            offsetInitialized = previous?.offsetInitialized == true,
            offsetX = previous?.offsetX ?: 0f,
            offsetY = previous?.offsetY ?: 0f
        )
    }

    /** Al salir de la pantalla del reproductor sin detener la reproducción. */
    fun minimizeIfLeavingPlayerScreen() {
        val current = _session.value ?: return
        if (current.mode != PlayerDisplayMode.FULL) return
        if (!canMinimize(getOrCreateController())) return
        minimize(
            videoId = current.videoId,
            isLive = current.isLive,
            title = current.title,
            author = current.author
        )
    }

    fun setFullMode() {
        _session.update { current ->
            current?.copy(mode = PlayerDisplayMode.FULL) ?: current
        }
    }

    fun updateMiniOffset(x: Float, y: Float, initialized: Boolean = true) {
        _session.update { current ->
            current?.copy(
                offsetX = x,
                offsetY = y,
                offsetInitialized = initialized
            ) ?: current
        }
    }

    fun canMinimize(controller: PhonePlayerController): Boolean {
        return controller.getCurrentVideoId() != null
    }

    fun stop() {
        controller?.release()
        controller = null
        _session.value = null
    }

    override fun onCleared() {
        castController.registerPlaybackSource(null)
        stop()
        super.onCleared()
    }
}
