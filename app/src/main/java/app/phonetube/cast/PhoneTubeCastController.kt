package app.phonetube.cast

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import app.phonetube.R
import app.phonetube.core.playback.CastPlaybackInfo
import app.phonetube.core.playback.CastPlaybackSource
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CastSessionState {
    Idle,
    Connecting,
    Connected,
    Error
}

@Singleton
class PhoneTubeCastController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appContext = context.applicationContext
    private var castContext: CastContext? = null
    private var initialized = false
    private var playbackSource: CastPlaybackSource? = null
    private var pendingCastInfo: CastPlaybackInfo? = null
    private var lastCastPositionMs = 0L
    private val _sessionState = MutableStateFlow(CastSessionState.Idle)
    val sessionState: StateFlow<CastSessionState> = _sessionState.asStateFlow()

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _sessionState.value = CastSessionState.Connecting
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _sessionState.value = CastSessionState.Connected
            val info = pendingCastInfo ?: playbackSource?.playbackInfo()
            if (info != null) {
                loadOnRemote(session, info)
            }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            pendingCastInfo = null
            _sessionState.value = CastSessionState.Error
            Toast.makeText(appContext, R.string.cast_failed, Toast.LENGTH_SHORT).show()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            val resumeMs = lastCastPositionMs
            pendingCastInfo = null
            _sessionState.value = CastSessionState.Idle
            playbackSource?.resumeLocal(resumeMs)
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _sessionState.value = CastSessionState.Connected
            val info = playbackSource?.playbackInfo()
            if (info != null) {
                loadOnRemote(session, info)
            }
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _sessionState.value = CastSessionState.Error
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit

        override fun onSessionEnding(session: CastSession) {
            lastCastPositionMs = session.remoteMediaClient?.approximateStreamPosition ?: lastCastPositionMs
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        runCatching {
            castContext = CastContext.getSharedInstance(appContext)
            castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        }
    }

    fun isAvailable(): Boolean = castContext != null

    fun connectedDeviceName(): String? =
        castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        _sessionState.value = CastSessionState.Idle
    }

    fun registerPlaybackSource(source: CastPlaybackSource?) {
        playbackSource = source
    }

    fun openCastPicker(activity: Activity) {
        initialize()
        if (castContext == null) {
            showUnavailable(activity)
            return
        }
        playbackSource?.playbackInfo()?.let { pendingCastInfo = it }
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity == null) {
            showUnavailable(activity)
            return
        }
        runCatching {
            val tag = CAST_CHOOSER_TAG
            val fm = fragmentActivity.supportFragmentManager
            if (fm.findFragmentByTag(tag) != null) return
            MediaRouteChooserDialogFragment().show(fm, tag)
        }.onFailure {
            showUnavailable(activity)
        }
    }

    private fun loadOnRemote(session: CastSession, info: CastPlaybackInfo) {
        val client = session.remoteMediaClient ?: run {
            Toast.makeText(appContext, R.string.cast_failed, Toast.LENGTH_SHORT).show()
            return
        }
        playbackSource?.pauseLocal()
        lastCastPositionMs = info.positionMs
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, info.title)
            info.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
        }
        val streamType = if (info.isLive) {
            MediaInfo.STREAM_TYPE_LIVE
        } else {
            MediaInfo.STREAM_TYPE_BUFFERED
        }
        val mediaInfo = MediaInfo.Builder(info.streamUrl)
            .setStreamType(streamType)
            .setContentType(info.mimeType)
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(info.positionMs)
            .build()
        client.load(request).setResultCallback { result ->
            pendingCastInfo = null
            if (!result.status.isSuccess) {
                playbackSource?.resumeLocal(info.positionMs)
                Toast.makeText(appContext, R.string.cast_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUnavailable(context: Context) {
        Toast.makeText(context, R.string.cast_unavailable, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val CAST_CHOOSER_TAG = "phonetube_cast_chooser"
    }
}
