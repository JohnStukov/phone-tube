package app.phonetube.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.phonetube.R
import app.phonetube.core.playback.PhonePlayerController
import app.phonetube.core.playback.SeekSegment
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEEK_SECONDS = 10
private const val CONTROLS_HIDE_MS = 3_000L
private const val SINGLE_TAP_DELAY_MS = 300L
private enum class SeekRippleSide { LEFT, RIGHT }

@Composable
fun VideoPlayerSurface(
    controller: PhonePlayerController,
    videoId: String,
    isLive: Boolean,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    sponsorSegments: List<SeekSegment> = emptyList(),
    settingsOpen: Boolean = false,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var seekHint by remember { mutableStateOf<String?>(null) }
    var seekRippleSide by remember { mutableStateOf<SeekRippleSide?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var pendingSingleTapJob by remember { mutableStateOf<Job?>(null) }
    val isScrubbingState = rememberUpdatedState(isScrubbing)

    LaunchedEffect(videoId) {
        positionMs = 0L
        durationMs = 0L
        scrubPositionMs = 0L
        isScrubbing = false
    }

    DisposableEffect(controller, videoId) {
        val activeVideoId = videoId
        controller.onProgressUpdate = { position, duration ->
            if (controller.getCurrentVideoId() == activeVideoId) {
                if (!isScrubbingState.value) {
                    positionMs = position.coerceAtLeast(0L)
                }
                if (duration > 0L) {
                    durationMs = duration
                }
                isPlaying = controller.isPlaying()
            }
        }
        onDispose {
            controller.onProgressUpdate = null
        }
    }

    fun showControls() {
        controlsVisible = true
    }

    fun showSeekHint(text: String, side: SeekRippleSide) {
        seekHint = text
        seekRippleSide = side
    }

    fun seekForward() {
        if (isLive && durationMs <= 0) return
        controller.seekForward(SEEK_SECONDS)
        showSeekHint(
            context.getString(R.string.seek_forward, SEEK_SECONDS),
            SeekRippleSide.RIGHT
        )
    }

    fun seekBackward() {
        if (isLive && durationMs <= 0) return
        controller.seekBackward(SEEK_SECONDS)
        showSeekHint(
            context.getString(R.string.seek_backward, SEEK_SECONDS),
            SeekRippleSide.LEFT
        )
    }

    fun toggleControls() {
        controlsVisible = !controlsVisible
    }

    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(500)
            seekHint = null
            seekRippleSide = null
        }
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            controlsVisible = true
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isScrubbing, settingsOpen) {
        if (settingsOpen) {
            controlsVisible = true
            return@LaunchedEffect
        }
        if (controlsVisible && isPlaying && !isScrubbing) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    val displayPositionMs = if (isScrubbing) scrubPositionMs else positionMs
    val sliderValue = when {
        durationMs <= 0L -> 0f
        else -> (displayPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    val playerContent: @Composable (Modifier) -> Unit = { boxModifier ->
        Box(modifier = boxModifier.background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        isClickable = false
                        isFocusable = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        controller.attachPlayerView(this)
                    }
                },
                update = { view ->
                    view.isClickable = false
                    view.isFocusable = false
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    if (view.player !== controller.getPlayer()) {
                        controller.attachPlayerView(view)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isLive, controlsVisible) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                pendingSingleTapJob?.cancel()
                                pendingSingleTapJob = null
                                if (isLive && durationMs <= 0) return@detectTapGestures
                                val width = size.width.toFloat()
                                when {
                                    offset.x < width * 0.4f -> seekBackward()
                                    offset.x > width * 0.6f -> seekForward()
                                }
                            },
                            onTap = {
                                pendingSingleTapJob?.cancel()
                                pendingSingleTapJob = scope.launch {
                                    delay(SINGLE_TAP_DELAY_MS)
                                    toggleControls()
                                }
                            }
                        )
                    }
            )

            AnimatedVisibility(
                visible = seekHint != null,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = if (seekRippleSide == SeekRippleSide.LEFT) {
                        Icons.Filled.Replay10
                    } else {
                        Icons.Filled.Forward10
                    },
                    contentDescription = seekHint,
                    tint = Color.White,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(10.dp)
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                pendingSingleTapJob?.cancel()
                                showControls()
                                onSettingsClick()
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.player_settings),
                                tint = Color.White
                            )
                        }
                    }

                    if (seekHint == null) {
                        IconButton(
                            onClick = {
                                pendingSingleTapJob?.cancel()
                                controller.togglePlayPause()
                                isPlaying = controller.isPlaying()
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(72.dp)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (durationMs > 0) {
                                if (isLive) {
                                    Text(
                                        text = stringResource(R.string.live_badge),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                    Text(
                                        text = formatPlayerTime(displayPositionMs),
                                        color = Color.White.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${formatPlayerTime(displayPositionMs)} / ${formatPlayerTime(durationMs)}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            } else if (isLive) {
                                Text(
                                    text = stringResource(R.string.live_badge),
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    pendingSingleTapJob?.cancel()
                                    onFullscreenChange(!isFullscreen)
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) {
                                        Icons.Filled.FullscreenExit
                                    } else {
                                        Icons.Filled.Fullscreen
                                    },
                                    contentDescription = stringResource(
                                        if (isFullscreen) R.string.exit_fullscreen else R.string.fullscreen
                                    ),
                                    tint = Color.White
                                )
                            }
                        }

                        if (durationMs > 0) {
                            key(videoId) {
                                SponsorBlockProgressSlider(
                                    value = sliderValue,
                                    durationMs = durationMs,
                                    sponsorSegments = if (isLive) emptyList() else sponsorSegments,
                                    onValueChange = { value ->
                                        pendingSingleTapJob?.cancel()
                                        isScrubbing = true
                                        scrubPositionMs = (value * durationMs).toLong()
                                        showControls()
                                    },
                                    onValueChangeFinished = {
                                        controller.seekTo(scrubPositionMs)
                                        positionMs = scrubPositionMs
                                        isScrubbing = false
                                        if (isLive && scrubPositionMs >= durationMs - 2_000) {
                                            controller.alignLivePlaybackToEdge()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    playerContent(modifier)
}

@Composable
fun ImmersiveFullscreenEffect(enabled: Boolean) {
    if (!enabled) return
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previousStatusColor = window.statusBarColor
        val previousNavColor = window.navigationBarColor
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = previousStatusColor
            window.navigationBarColor = previousNavColor
        }
    }
}
