package app.phonetube.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.phonetube.PhoneTubeApp
import app.phonetube.R
import app.phonetube.cast.CastSessionState
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.PlaybackRestrictions
import app.phonetube.core.media.VideoComment
import app.phonetube.core.playback.PhonePlayerController
import app.phonetube.core.playback.SeekSegment
import app.phonetube.ui.components.ChannelAvatar
import app.phonetube.ui.components.YouTubeActionPill
import app.phonetube.ui.components.YouTubePlayerTopBar
import app.phonetube.ui.components.YouTubeSubscribePill
import app.phonetube.ui.components.YouTubeVideoCard
import app.phonetube.ui.theme.ContrastColors

@Composable
fun PlayerScreen(
    videoId: String,
    isLive: Boolean,
    playbackHost: PlaybackHostViewModel,
    onBack: () -> Unit,
    onRelatedVideoClick: (videoId: String, isLive: Boolean) -> Unit,
    onSearchClick: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onChannelClick: (channelId: String, channelName: String?) -> Unit = { _, _ -> },
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val controller = remember(playbackHost) { playbackHost.getOrCreateController() }
    val castController = remember { (context.applicationContext as PhoneTubeApp).castController }
    val castState by castController.sessionState.collectAsState()
    val playerState by viewModel.state.collectAsState()
    val metadata = playerState.metadata
    val playbackIsLive = isLive || metadata.isLive || metadata.isLiveContent
    var playbackError by remember { mutableStateOf<String?>(null) }
    var segments by remember { mutableStateOf<List<SeekSegment>>(emptyList()) }
    var isFullscreen by remember { mutableStateOf(false) }
    var userExitedFullscreen by remember { mutableStateOf(false) }
    CollectPlayerActions(viewModel.actionEvents)

    fun setImmersive(enabled: Boolean) {
        if (enabled) {
            userExitedFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isFullscreen = true
        } else {
            userExitedFullscreen = true
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isFullscreen = false
        }
    }

    LaunchedEffect(isLandscape) {
        if (userExitedFullscreen) {
            if (!isLandscape) userExitedFullscreen = false
            return@LaunchedEffect
        }
        isFullscreen = isLandscape
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(playerState.actionMessage) {
        val msg = resolveActionMessage(context, playerState.actionMessage)
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearActionMessage()
        }
    }

    LaunchedEffect(videoId) {
        viewModel.resetAutoplayCursor()
        playbackError = null
        controller.onError = { err ->
            playbackError = when (err.message) {
                PlaybackRestrictions.LIVE_UNAVAILABLE ->
                    context.getString(R.string.playback_live_unavailable)
                else -> err.message
            }
        }
        controller.onSegmentsChanged = { segments = it }
        viewModel.loadDetails(videoId)
    }

    LaunchedEffect(
        videoId,
        playerState.isLoading,
        playerState.metadata.videoId,
        playerState.metadata.title,
        playerState.metadata.isLive
    ) {
        if (playerState.isLoading || playerState.metadata.videoId != videoId) return@LaunchedEffect
        playbackHost.onEnterPlayerScreen(
            videoId = videoId,
            isLive = playbackIsLive,
            title = playerState.metadata.title,
            author = playerState.metadata.author
        )
        controller.setPlaybackSpeed(playerState.playbackSpeed)
        controller.applyCaptionSize(playerState.captionSize)
        val quality = playerState.qualityOptions
            .firstOrNull { it.label == playerState.selectedQualityLabel }
        val subtitle = viewModel.selectedSubtitle()
        if (controller.getCurrentVideoId() != videoId) {
            controller.play(
                videoId,
                playbackIsLive,
                quality?.streamUrl,
                quality?.audioStreamUrl,
                subtitle,
                percentWatched = if (playbackIsLive) -1 else playerState.metadata.percentWatched
            )
        } else if (playbackIsLive) {
            controller.alignLivePlaybackToEdge()
        }
    }

    LaunchedEffect(playerState.playbackSpeed) {
        controller.setPlaybackSpeed(playerState.playbackSpeed)
    }

    LaunchedEffect(playerState.captionSize) {
        controller.applyCaptionSize(playerState.captionSize)
    }

    LaunchedEffect(videoId, playerState.metadata.relatedVideos) {
        val next = viewModel.findNextRelatedVideo(videoId) ?: return@LaunchedEffect
        controller.prefetch(next.videoId)
    }

    DisposableEffect(controller, playerState.autoplayEnabled, videoId, playbackIsLive) {
        var lastEndedAt = 0L
        controller.onBufferingChanged = { buffering -> viewModel.setBuffering(buffering) }
        controller.onRetryAttempt = { attempt ->
            if (attempt >= 2) viewModel.setReconnecting(true)
        }
        controller.onPlaybackEnded = {
            if (!playbackIsLive && playerState.autoplayEnabled) {
                val now = System.currentTimeMillis()
                if (now - lastEndedAt >= 900L) {
                    lastEndedAt = now
                    viewModel.findNextRelatedVideo(videoId)?.let { next ->
                        onRelatedVideoClick(next.videoId, next.isLive)
                    }
                }
            }
        }
        onDispose {
            controller.onPlaybackEnded = null
            controller.onBufferingChanged = null
            controller.onRetryAttempt = null
            viewModel.setBuffering(false)
            viewModel.setReconnecting(false)
        }
    }

    LaunchedEffect(playerState.reconnecting) {
        if (playerState.reconnecting) {
            Toast.makeText(context, R.string.player_reconnecting, Toast.LENGTH_SHORT).show()
            viewModel.setReconnecting(false)
        }
    }

    val exitPlayer: () -> Unit = {
        if (playbackHost.canMinimize(controller)) {
            playbackHost.minimize(
                videoId = videoId,
                isLive = playbackIsLive,
                title = metadata.title,
                author = metadata.author
            )
        } else {
            playbackHost.stop()
        }
        onBack()
    }

    BackHandler(enabled = isFullscreen) { setImmersive(false) }
    BackHandler(enabled = !isFullscreen) { exitPlayer() }

    ImmersiveFullscreenEffect(enabled = isFullscreen)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFullscreen) {
                YouTubePlayerTopBar(
                    onBack = exitPlayer,
                    onCastClick = onCastClick,
                    onSearchClick = onSearchClick
                )
                when (castState) {
                    CastSessionState.Connecting -> {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(stringResource(R.string.cast_connecting)) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    CastSessionState.Connected -> {
                        val deviceName = castController.connectedDeviceName().orEmpty()
                        AssistChip(
                            onClick = { castController.disconnect() },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.cast_connected,
                                        deviceName.ifBlank { "Cast" }
                                    )
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    else -> Unit
                }
                if (playerState.pendingSyncCount > 0) {
                    Text(
                        text = stringResource(R.string.action_pending_sync),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                if (playbackIsLive) {
                    Text(
                        text = stringResource(R.string.live_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                if (playerState.isBuffering) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            VideoPlayerSurface(
                controller = controller,
                videoId = videoId,
                isLive = playbackIsLive,
                isFullscreen = isFullscreen,
                onFullscreenChange = { setImmersive(it) },
                sponsorSegments = segments,
                settingsOpen = playerState.showSettings,
                onSettingsClick = { viewModel.setShowSettings(true) },
                modifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
            )

            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
            val displayError = playbackError ?: when (playerState.error) {
                PlaybackRestrictions.LIVE_UNAVAILABLE ->
                    stringResource(R.string.playback_live_unavailable)
                else -> playerState.error
            }
            if (displayError != null) {
                Text(
                    text = displayError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Text(
                text = metadata.title.ifBlank { videoId },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )

            metadata.viewCount?.let { views ->
                Text(
                    text = views,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val openChannel = {
                    if (isFullscreen) setImmersive(false)
                    if (playbackHost.canMinimize(controller)) {
                        playbackHost.minimize(
                            videoId = videoId,
                            isLive = playbackIsLive,
                            title = metadata.title,
                            author = metadata.author
                        )
                    }
                    viewModel.openChannel(videoId) { channelId, channelName ->
                        onChannelClick(channelId, channelName)
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = openChannel
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChannelAvatar(
                        name = metadata.author,
                        imageUrl = metadata.authorImageUrl,
                        size = 40.dp
                    )
                    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            text = metadata.author.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        metadata.subscriberCount?.let { subs ->
                            Text(
                                text = subs,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        metadata.publishedDate?.let { date ->
                            Text(
                                text = date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                YouTubeSubscribePill(
                    subscribed = metadata.isSubscribed,
                    label = stringResource(
                        if (metadata.isSubscribed) R.string.subscribed else R.string.subscribe
                    ),
                    onClick = { viewModel.toggleSubscribe() }
                )
            }

            metadata.description?.takeIf { it.isNotBlank() }?.let { description ->
                ExpandableDescription(text = description)
            }

            PlayerActionRow(
                modifier = Modifier.padding(vertical = 8.dp),
                likeCount = metadata.likeCount,
                isLiked = metadata.isLiked,
                isDisliked = metadata.isDisliked,
                onLikeClick = { viewModel.toggleLike(videoId) },
                onDislikeClick = { viewModel.toggleDislike(videoId) },
                onShareClick = { viewModel.share(videoId) },
                onDownloadClick = { viewModel.download(videoId) }
            )

            if (segments.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.sponsor_segments_count, segments.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = if (playerState.comments.isNotEmpty()) {
                    stringResource(R.string.comments_count, playerState.comments.size)
                } else {
                    stringResource(R.string.comments)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            CommentComposer(
                signedIn = AuthRepository.get(context).isSignedIn(),
                canPost = playerState.canPostComment,
                isPosting = playerState.commentPosting,
                onPost = { text -> viewModel.postComment(videoId, text) }
            )

            when {
                playerState.commentsLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                playerState.comments.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.comments_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                else -> {
                    playerState.comments.forEach { comment ->
                        CommentRow(comment = comment)
                        Divider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                }
            }

            if (metadata.relatedVideos.isNotEmpty()) {
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = stringResource(R.string.related_videos),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                metadata.relatedVideos.take(12).forEach { video ->
                    YouTubeVideoCard(
                        video = video,
                        onClick = { onRelatedVideoClick(video.videoId, video.isLive) }
                    )
                }
            }
                }
            }
        }

        if (playerState.showSettings) {
            PlayerSettingsSheet(
                qualityOptions = playerState.qualityOptions,
                selectedQualityLabel = playerState.selectedQualityLabel,
                audioTrackOptions = playerState.audioTrackOptions,
                selectedAudioTrackId = playerState.selectedAudioTrackId,
                subtitleOptions = playerState.subtitleOptions,
                selectedSubtitleId = playerState.selectedSubtitleId,
                playbackSpeed = playerState.playbackSpeed,
                captionSize = playerState.captionSize,
                autoplayEnabled = playerState.autoplayEnabled,
                onQualitySelected = { option ->
                    viewModel.selectQuality(option)
                    controller.setStreamUrl(
                        videoId,
                        playbackIsLive,
                        option.streamUrl,
                        option.audioStreamUrl
                    )
                },
                onAudioTrackSelected = { option ->
                    controller.setAudioLanguage(option.languageCode)
                    viewModel.selectAudioTrack(option) { quality ->
                        if (quality != null && quality.streamUrl != null) {
                            controller.setStreamUrl(
                                videoId,
                                playbackIsLive,
                                quality.streamUrl,
                                quality.audioStreamUrl
                            )
                        } else {
                            controller.play(
                                videoId,
                                playbackIsLive,
                                subtitle = viewModel.selectedSubtitle()
                            )
                        }
                    }
                },
                onSubtitleSelected = { option ->
                    viewModel.selectSubtitle(option)
                    controller.setSubtitle(option)
                },
                onSpeedSelected = { speed ->
                    viewModel.setPlaybackSpeed(speed)
                    controller.setPlaybackSpeed(speed)
                },
                onCaptionSizeSelected = { size ->
                    viewModel.setCaptionSize(size)
                    controller.applyCaptionSize(size)
                },
                onAutoplayChanged = { viewModel.setAutoplayEnabled(it) },
                onDismiss = { viewModel.setShowSettings(false) }
            )
        }
    }
}

@Composable
private fun CommentComposer(
    signedIn: Boolean,
    canPost: Boolean,
    isPosting: Boolean,
    onPost: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }

    when {
        !signedIn -> {
            Text(
                text = stringResource(R.string.comments_sign_in_to_post),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        !canPost -> {
            Text(
                text = stringResource(R.string.comments_post_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.add_comment_hint)) },
                    maxLines = 4,
                    enabled = !isPosting
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onPost(draft)
                            draft = ""
                        }
                    },
                    enabled = !isPosting && draft.isNotBlank()
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Outlined.Send,
                            contentDescription = stringResource(R.string.post_comment)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableDescription(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val maxCollapsedLines = 3
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else maxCollapsedLines,
            overflow = TextOverflow.Ellipsis
        )
        if (text.length > 120 || text.lines().size > maxCollapsedLines) {
            Text(
                text = stringResource(if (expanded) R.string.show_less else R.string.show_more),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = !expanded }
            )
        }
    }
}

@Composable
private fun CommentRow(comment: VideoComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        ChannelAvatar(
            name = comment.authorName,
            imageUrl = comment.authorPhotoUrl,
            size = 36.dp
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = comment.authorName.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = comment.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            val meta = listOfNotNull(
                comment.publishedDate,
                comment.likeCount?.let { stringResource(R.string.comment_likes, it) }
            ).joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerActionRow(
    modifier: Modifier = Modifier,
    likeCount: String? = null,
    isLiked: Boolean = false,
    isDisliked: Boolean = false,
    onLikeClick: () -> Unit = {},
    onDislikeClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val likeLabel = if (!likeCount.isNullOrBlank()) {
            stringResource(R.string.like) + " ($likeCount)"
        } else {
            stringResource(R.string.like)
        }
        YouTubeActionPill(
            label = likeLabel,
            onClick = onLikeClick,
            selected = isLiked,
            leadingIcon = {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = null,
                    tint = if (isLiked) ContrastColors.actionSelectedText else ContrastColors.actionText,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        YouTubeActionPill(
            label = stringResource(R.string.dislike),
            onClick = onDislikeClick,
            selected = isDisliked,
            leadingIcon = {
                Icon(
                    imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = null,
                    tint = if (isDisliked) ContrastColors.actionSelectedText else ContrastColors.actionText,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        YouTubeActionPill(
            label = stringResource(R.string.share),
            onClick = onShareClick,
            leadingIcon = {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = null,
                    tint = ContrastColors.actionText,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        YouTubeActionPill(
            label = stringResource(R.string.download),
            onClick = onDownloadClick,
            leadingIcon = {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = null,
                    tint = ContrastColors.actionText,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}
