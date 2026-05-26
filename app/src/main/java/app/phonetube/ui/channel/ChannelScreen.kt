package app.phonetube.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.phonetube.R
import app.phonetube.core.media.ChannelDetails
import app.phonetube.ui.components.ChannelAvatar
import app.phonetube.ui.components.YouTubeAsyncImage
import app.phonetube.ui.components.YouTubeFilterChip
import app.phonetube.ui.components.YouTubeSubscribePill
import app.phonetube.ui.player.resolveActionMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    channelName: String?,
    onBack: () -> Unit,
    onVideoClick: (videoId: String, isLive: Boolean) -> Unit,
    onSignIn: () -> Unit,
    viewModel: ChannelViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(channelId, channelName) {
        if (channelId.isNotBlank()) {
            viewModel.load(channelId, channelName)
        }
    }

    state.actionMessage?.let { key ->
        LaunchedEffect(key) {
            android.widget.Toast.makeText(
                context,
                resolveActionMessage(context, key) ?: key,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading && state.channel == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.channel == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = state.error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val channel = state.channel
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (channel != null) {
                        item {
                            ChannelHeaderSection(
                                channel = channel,
                                onSubscribe = { viewModel.toggleSubscribe() }
                            )
                        }
                        if (channel.tabs.size > 1) {
                            item {
                                ChannelTabsRow(
                                    tabs = channel.tabs,
                                    selectedTabId = channel.selectedTabId,
                                    onTabSelected = { viewModel.selectTab(it) }
                                )
                            }
                        }
                        if (channel.selectedTabId == app.phonetube.core.media.ChannelTabIds.VIDEOS &&
                            channel.sortOptions.isNotEmpty()
                        ) {
                            item {
                                ChannelSortChipsRow(
                                    options = channel.sortOptions,
                                    selectedSortId = channel.selectedSortId,
                                    onSortSelected = { viewModel.selectSort(it) }
                                )
                            }
                        }
                    }
                    if (state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (state.error != null) {
                        item {
                            Text(
                                text = state.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        val videos = channel?.videos.orEmpty()
                        if (videos.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.channel_videos_empty),
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(videos, key = { it.videoId }) { video ->
                                ChannelVideoRow(
                                    video = video,
                                    onClick = { onVideoClick(video.videoId, video.isLive) }
                                )
                                Divider(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeaderSection(
    channel: ChannelDetails,
    onSubscribe: () -> Unit
) {
    var descriptionExpanded by remember(channel.channelId) { mutableStateOf(false) }
    val description = channel.description?.trim().orEmpty()
    val showExpand = description.length > 120 && !descriptionExpanded
    val descriptionText = when {
        description.isEmpty() -> null
        showExpand -> description.take(120).trimEnd() + "…"
        else -> description
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            channel.bannerUrl?.let { url ->
                YouTubeAsyncImage(
                    url = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-28).dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ChannelAvatar(
                name = channel.name,
                size = 80.dp,
                imageUrl = channel.avatarUrl,
                modifier = Modifier.clip(CircleShape)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            channel.handle?.let { handle ->
                Text(
                    text = handle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            val stats = channelStatsText(channel)
            if (stats.isNotBlank()) {
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            descriptionText?.let { text ->
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showExpand) {
                        Text(
                            text = stringResource(R.string.channel_description_more),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable { descriptionExpanded = true },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            YouTubeSubscribePill(
                subscribed = channel.isSubscribed,
                label = stringResource(
                    if (channel.isSubscribed) R.string.subscribed else R.string.subscribe
                ),
                onClick = onSubscribe,
                fullWidth = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun channelStatsText(channel: ChannelDetails): String {
    val subs = channel.subscriberCount?.trim().orEmpty()
    val count = channel.videoCount
    return when {
        subs.isNotEmpty() && count > 0 ->
            stringResource(R.string.channel_stats, subs, count)
        subs.isNotEmpty() -> subs
        count > 0 -> stringResource(R.string.channel_video_count, count)
        else -> ""
    }
}

@Composable
private fun ChannelTabsRow(
    tabs: List<app.phonetube.core.media.ChannelTab>,
    selectedTabId: String,
    onTabSelected: (String) -> Unit
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    val onSurface = MaterialTheme.colorScheme.onSurface
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = onSurface,
        edgePadding = 12.dp,
        divider = {},
        indicator = { positions ->
            if (positions.isNotEmpty()) {
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selectedIndex]),
                    color = onSurface,
                    height = 2.dp
                )
            }
        }
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab.id == selectedTabId,
                onClick = { onTabSelected(tab.id) },
                text = {
                    Text(
                        text = channelTabLabel(tab.id),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (tab.id == selectedTabId) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                selectedContentColor = onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun channelTabLabel(tabId: String): String = when (tabId) {
    app.phonetube.core.media.ChannelTabIds.VIDEOS -> stringResource(R.string.channel_tab_videos)
    app.phonetube.core.media.ChannelTabIds.SHORTS -> stringResource(R.string.channel_tab_shorts)
    app.phonetube.core.media.ChannelTabIds.PLAYLISTS -> stringResource(R.string.channel_tab_playlists)
    app.phonetube.core.media.ChannelTabIds.LIVE -> stringResource(R.string.channel_tab_live)
    app.phonetube.core.media.ChannelTabIds.HOME -> stringResource(R.string.channel_tab_home)
    else -> tabId
}

@Composable
private fun ChannelSortChipsRow(
    options: List<app.phonetube.core.media.ChannelSortOption>,
    selectedSortId: String?,
    onSortSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            YouTubeFilterChip(
                label = option.label,
                selected = option.id == selectedSortId,
                onClick = { onSortSelected(option.id) }
            )
        }
    }
}
