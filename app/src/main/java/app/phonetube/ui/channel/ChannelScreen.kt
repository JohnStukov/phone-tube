package app.phonetube.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.ExperimentalMaterialApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.phonetube.R
import app.phonetube.core.media.ChannelDetails
import app.phonetube.core.media.ChannelTabIds
import app.phonetube.ui.components.ChannelAvatar
import app.phonetube.ui.components.PullRefreshBox
import app.phonetube.ui.components.YouTubeAsyncImage
import app.phonetube.ui.components.YouTubeFilterChip
import app.phonetube.ui.components.YouTubeSubscribePill
import app.phonetube.ui.player.resolveActionMessage
import app.phonetube.util.resolveMediaErrorMessage
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val showToolbarTitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 72 }
    }

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (showToolbarTitle) {
                        Text(
                            text = state.channel?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading && state.channel == null -> {
                ChannelLoadingPlaceholder(modifier = Modifier.padding(padding))
            }
            state.error != null && state.channel == null -> {
                ChannelErrorState(
                    message = resolveMediaErrorMessage(state.error).orEmpty(),
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                val channel = state.channel
                val errorMessage = resolveMediaErrorMessage(state.error)
                LaunchedEffect(listState, channel?.videos?.size, state.canLoadMore) {
                    if (!state.canLoadMore) return@LaunchedEffect
                    snapshotFlow {
                        val info = listState.layoutInfo
                        val total = info.totalItemsCount
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 4
                    }
                        .distinctUntilChanged()
                        .collect { nearEnd ->
                            if (nearEnd) viewModel.loadMore()
                        }
                }
                PullRefreshBox(
                    refreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (channel != null) {
                        item(key = "channel_header") {
                            ChannelHeaderSection(
                                channel = channel,
                                onSubscribe = { viewModel.toggleSubscribe() }
                            )
                        }
                        val showTabs = channel.tabs.size > 1
                        val showSorts = channel.selectedTabId == ChannelTabIds.VIDEOS &&
                            channel.sortOptions.isNotEmpty()
                        if (showTabs || showSorts) {
                            item(key = "channel_sticky_filters") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    if (showTabs) {
                                        ChannelTabsRow(
                                            tabs = channel.tabs,
                                            selectedTabId = channel.selectedTabId,
                                            onTabSelected = { viewModel.selectTab(it) }
                                        )
                                    }
                                    if (showSorts) {
                                        ChannelSortChipsRow(
                                            options = channel.sortOptions,
                                            selectedSortId = channel.selectedSortId,
                                            onSortSelected = { viewModel.selectSort(it) }
                                        )
                                    }
                                    Divider(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }
                    if (state.isLoading) {
                        item(key = "channel_tab_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (errorMessage != null) {
                        item(key = "channel_tab_error") {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        val items = channel?.videos.orEmpty()
                        val isPlaylistsTab = channel?.selectedTabId == ChannelTabIds.PLAYLISTS
                        if (items.isEmpty()) {
                            item(key = "channel_empty") {
                                Text(
                                    text = stringResource(
                                        if (isPlaylistsTab) {
                                            R.string.channel_playlists_empty
                                        } else {
                                            R.string.channel_videos_empty
                                        }
                                    ),
                                    modifier = Modifier.padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            items(items, key = { it.stableListKey }) { item ->
                                if (item.isPlaylist || isPlaylistsTab) {
                                    ChannelPlaylistRow(
                                        playlist = item,
                                        onClick = { viewModel.openItem(item, onVideoClick) }
                                    )
                                } else {
                                    ChannelVideoRow(
                                        video = item,
                                        onClick = { viewModel.openItem(item, onVideoClick) }
                                    )
                                }
                            }
                            if (state.isLoadingMore) {
                                item(key = "channel_loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ChannelLoadingPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .offset(y = (-36).dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(14.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp)
            )
        }
    }
}

@Composable
private fun ChannelErrorState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp)
        )
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
    val surface = MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to surface
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-40).dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ChannelAvatar(
                name = channel.name,
                size = 88.dp,
                imageUrl = channel.avatarUrl,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(3.dp, surface, CircleShape)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
                }
                YouTubeSubscribePill(
                    subscribed = channel.isSubscribed,
                    label = stringResource(
                        if (channel.isSubscribed) R.string.subscribed else R.string.subscribe
                    ),
                    onClick = onSubscribe,
                    fullWidth = false
                )
            }
            descriptionText?.let { text ->
                Row(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showExpand) {
                        Text(
                            text = stringResource(R.string.channel_description_more),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable { descriptionExpanded = true }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun channelStatsText(channel: ChannelDetails): String =
    channel.subscriberCount?.trim().orEmpty()

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
        edgePadding = 16.dp,
        divider = {},
        indicator = { positions ->
            if (positions.isNotEmpty()) {
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selectedIndex]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
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
    ChannelTabIds.VIDEOS -> stringResource(R.string.channel_tab_videos)
    ChannelTabIds.SHORTS -> stringResource(R.string.channel_tab_shorts)
    ChannelTabIds.PLAYLISTS -> stringResource(R.string.channel_tab_playlists)
    ChannelTabIds.LIVE -> stringResource(R.string.channel_tab_live)
    ChannelTabIds.HOME -> stringResource(R.string.channel_tab_home)
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
