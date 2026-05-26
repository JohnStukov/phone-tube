package app.phonetube.ui.shorts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import app.phonetube.R
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.VideoMetadata
import app.phonetube.core.playback.PhonePlayerController
import app.phonetube.ui.components.KeepScreenOnEffect
import app.phonetube.ui.components.PortraitLockEffect
import app.phonetube.ui.components.createTouchTransparentPlayerView
import app.phonetube.ui.components.YouTubeAsyncImage
import app.phonetube.ui.components.YouTubeSubscribePill
import app.phonetube.util.ShareHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ShortsScreen(
    onChannelClick: (channelId: String, channelName: String?) -> Unit = { _, _ -> },
    viewModel: ShortsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    KeepScreenOnEffect(enabled = true)
    PortraitLockEffect()
    val context = LocalContext.current
    val controller = remember { PhonePlayerController(context) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var videoReady by remember { mutableStateOf(false) }
    var activeVideoId by remember { mutableStateOf<String?>(null) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        controller.setLooping(true)
        controller.onError = { playbackError = it.message }
        controller.onVideoReady = { videoReady = true }
        controller.onProgressUpdate = { positionMs, durationMs ->
            playbackProgress = if (durationMs > 0L) {
                positionMs.toFloat() / durationMs.toFloat()
            } else {
                0f
            }
            isPlaying = controller.isPlaying()
        }
        onDispose {
            controller.release()
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(2_500)
            controlsVisible = false
        }
    }

    if (state.showComments && state.activeDetails != null) {
        ShortsCommentsSheet(
            details = state.activeDetails!!,
            onDismiss = { viewModel.dismissComments() },
            onPostComment = { viewModel.postComment(it) },
            onClearMessage = { viewModel.clearActionMessage() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            state.isLoading && state.videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.error != null && state.videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            state.videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.shorts_empty),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            else -> {
                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { state.videos.size }
                )

                LaunchedEffect(state.videos) {
                    if (pagerState.currentPage >= state.videos.size && state.videos.isNotEmpty()) {
                        pagerState.scrollToPage(0)
                    }
                }

                LaunchedEffect(pagerState, state.videos.size, state.canLoadMore) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            val video = state.videos.getOrNull(page) ?: return@collect
                            if (state.canLoadMore && page >= state.videos.size - 3) {
                                viewModel.loadMore()
                            }
                            playbackError = null
                            videoReady = false
                            playbackProgress = 0f
                            controlsVisible = false
                            activeVideoId = video.videoId
                            viewModel.onActiveVideoChanged(video.videoId)
                            state.videos.getOrNull(page + 1)?.let { controller.prefetch(it.videoId) }
                            state.videos.getOrNull(page - 1)?.let { controller.prefetch(it.videoId) }
                            if (controller.getCurrentVideoId() != video.videoId) {
                                controller.play(video.videoId, video.isLive)
                            }
                        }
                }

                val pullRefreshState = rememberPullRefreshState(
                    refreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(
                            state = pullRefreshState,
                            enabled = pagerState.currentPage == 0
                        )
                ) {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondBoundsPageCount = 2
                    ) { page ->
                        val video = state.videos[page]
                        val isSettled = pagerState.settledPage == page
                        val details = if (isSettled && state.activeVideoId == video.videoId) {
                            state.activeDetails
                        } else {
                            null
                        }
                        val showPlayer = isSettled && video.videoId == activeVideoId
                        ShortVideoPage(
                            video = video,
                            metadata = details?.metadata,
                            showPlayer = showPlayer,
                            videoReady = videoReady,
                            showLoading = showPlayer && !videoReady,
                            controlsVisible = showPlayer && controlsVisible,
                            isPlaying = isPlaying,
                            playbackError = if (isSettled) playbackError else null,
                            progress = if (isSettled) playbackProgress else 0f,
                            controller = controller,
                            onVideoAreaTap = {
                                controlsVisible = true
                                controller.togglePlayPause()
                                isPlaying = controller.isPlaying()
                            },
                            onLikeClick = { viewModel.toggleLike() },
                            onDislikeClick = { viewModel.toggleDislike() },
                            onCommentsClick = { viewModel.openComments() },
                            onShareClick = { ShareHelper.shareVideo(context, video.videoId) },
                            onSubscribeClick = { viewModel.toggleSubscribe() },
                            onChannelClick = {
                                val channelId = details?.metadata?.channelId
                                    ?: video.channelId
                                if (!channelId.isNullOrBlank()) {
                                    val name = details?.metadata?.author ?: video.author
                                    onChannelClick(channelId, name)
                                }
                            }
                        )
                    }

                    PullRefreshIndicator(
                        refreshing = state.isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortVideoPage(
    video: VideoItem,
    metadata: VideoMetadata?,
    showPlayer: Boolean,
    videoReady: Boolean,
    showLoading: Boolean,
    controlsVisible: Boolean,
    isPlaying: Boolean,
    playbackError: String?,
    progress: Float,
    controller: PhonePlayerController,
    onVideoAreaTap: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onChannelClick: () -> Unit
) {
    val playerAlpha by animateFloatAsState(
        targetValue = if (showPlayer && videoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "shortsPlayerAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        YouTubeAsyncImage(
            url = video.thumbnailUrl,
            videoId = video.videoId,
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (showPlayer) {
            AndroidView(
                factory = { ctx -> createTouchTransparentPlayerView(ctx, controller) },
                update = { view ->
                    view.isClickable = false
                    view.isFocusable = false
                    if (view.player !== controller.getPlayer()) {
                        controller.attachPlayerView(view)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(playerAlpha)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 64.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onVideoAreaTap() })
                }
        )

        AnimatedVisibility(
            visible = controlsVisible && showPlayer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        if (showLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp, bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ShortActionButton(
                icon = {
                    Icon(
                        imageVector = if (metadata?.isLiked == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                },
                label = metadata?.likeCount?.takeIf { it.isNotBlank() } ?: stringResource(R.string.like),
                onClick = onLikeClick
            )
            ShortActionButton(
                icon = {
                    Icon(
                        imageVector = if (metadata?.isDisliked == true) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                },
                label = stringResource(R.string.dislike),
                onClick = onDislikeClick
            )
            ShortActionButton(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                },
                label = metadata?.commentsKey?.let {
                    stringResource(R.string.comments)
                } ?: stringResource(R.string.comments),
                onClick = onCommentsClick
            )
            ShortActionButton(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                },
                label = stringResource(R.string.share),
                onClick = onShareClick
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 72.dp, bottom = 12.dp)
                .navigationBarsPadding()
        ) {
            if (!playbackError.isNullOrBlank()) {
                Text(
                    text = playbackError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val subscribed = metadata?.isSubscribed == true
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onChannelClick
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YouTubeAsyncImage(
                        url = metadata?.authorImageUrl ?: video.channelAvatarUrl,
                        videoId = null,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = channelHandle(metadata?.author ?: video.author),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                YouTubeSubscribePill(
                    subscribed = subscribed,
                    label = stringResource(
                        if (subscribed) R.string.subscribed else R.string.subscribe
                    ),
                    onClick = onSubscribeClick
                )
            }

            Text(
                text = metadata?.title?.takeIf { it.isNotBlank() } ?: video.title,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = Color(0xFFFF0000),
            trackColor = Color.White.copy(alpha = 0.25f)
        )
    }
}

@Composable
private fun ShortActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick
        )
    ) {
        icon()
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun channelHandle(author: String?): String {
    val name = author?.trim().orEmpty()
    if (name.isEmpty()) return "@channel"
    return if (name.startsWith("@")) name else "@$name"
}
