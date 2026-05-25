package app.phonetube.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import app.phonetube.R
import app.phonetube.core.playback.PhonePlayerController
import com.google.android.exoplayer2.ui.PlayerView
import kotlin.math.roundToInt

private val MiniPlayerWidth = 280.dp
private val MiniPlayerCorner = 12.dp

@Composable
fun MiniPlayerOverlay(
    session: ActivePlayback,
    controller: PhonePlayerController,
    showBottomBar: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onOffsetChange: (x: Float, y: Float, initialized: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val bottomInset = if (showBottomBar) 72.dp else 12.dp
    val cardWidthPx = with(density) { MiniPlayerWidth.toPx() }
    val videoHeightPx = cardWidthPx / (16f / 9f)
    val metaHeightPx = with(density) { 44.dp.toPx() }
    val cardHeightPx = videoHeightPx + metaHeightPx

    var posX by remember(session.videoId) { mutableFloatStateOf(session.offsetX) }
    var posY by remember(session.videoId) { mutableFloatStateOf(session.offsetY) }
    var isPlaying by remember { mutableStateOf(controller.isPlaying()) }
    var placed by remember(session.videoId) { mutableStateOf(session.offsetInitialized) }

    DisposableEffect(controller) {
        controller.onProgressUpdate = { _, _ ->
            isPlaying = controller.isPlaying()
        }
        onDispose {
            controller.onProgressUpdate = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        val maxX = with(density) { (maxWidth - MiniPlayerWidth).toPx() }.coerceAtLeast(0f)
        val maxY = with(density) {
            maxHeight.toPx() - cardHeightPx - bottomInset.toPx()
        }.coerceAtLeast(0f)

        LaunchedEffect(maxX, maxY, session.videoId, session.offsetInitialized) {
            if (!session.offsetInitialized) {
                posX = maxX
                posY = maxY
                placed = true
                onOffsetChange(posX, posY, true)
            } else {
                posX = session.offsetX.coerceIn(0f, maxX)
                posY = session.offsetY.coerceIn(0f, maxY)
                placed = true
            }
        }

        if (placed) {
            Card(
                modifier = Modifier
                    .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                    .width(MiniPlayerWidth)
                    .pointerInput(session.videoId) {
                        detectDragGestures(
                            onDragEnd = { onOffsetChange(posX, posY, true) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                posX = (posX + dragAmount.x).coerceIn(0f, maxX)
                                posY = (posY + dragAmount.y).coerceIn(0f, maxY)
                            }
                        )
                    },
                shape = RoundedCornerShape(MiniPlayerCorner),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = MiniPlayerCorner, topEnd = MiniPlayerCorner))
                            .background(Color.Black)
                            .pointerInput(session.videoId) {
                                detectTapGestures(onTap = { onExpand() })
                            }
                    ) {
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
                                    controller.attachPlayerView(this)
                                }
                            },
                            update = { view ->
                                view.isClickable = false
                                if (view.player !== controller.getPlayer()) {
                                    controller.attachPlayerView(view)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.mini_player_close),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                controller.togglePlayPause()
                                isPlaying = controller.isPlaying()
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.mini_player_play_pause),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(session.videoId) {
                                detectTapGestures(onTap = { onExpand() })
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        session.author?.let { author ->
                            Text(
                                text = author,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
