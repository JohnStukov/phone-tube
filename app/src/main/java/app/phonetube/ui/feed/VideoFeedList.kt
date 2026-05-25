package app.phonetube.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.phonetube.core.media.VideoItem
import app.phonetube.ui.components.YouTubeVideoCard
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun VideoFeedList(
    videos: List<VideoItem>,
    isLoading: Boolean,
    error: String?,
    emptyMessage: String,
    onVideoClick: (videoId: String, isLive: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLoadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {}
) {
    when {
        isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
        videos.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        else -> {
            val listState = rememberLazyListState()
            LaunchedEffect(listState, videos.size, canLoadMore) {
                if (!canLoadMore) return@LaunchedEffect
                snapshotFlow {
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    total > 0 && lastVisible >= total - 4
                }
                    .distinctUntilChanged()
                    .collect { nearEnd ->
                        if (nearEnd) onLoadMore()
                    }
            }
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(
                    items = videos,
                    key = { index, video -> "${video.videoId}_$index" }
                ) { _, video ->
                    YouTubeVideoCard(
                        video = video,
                        onClick = { onVideoClick(video.videoId, video.isLive) }
                    )
                }
                if (isLoadingMore) {
                    item(key = "loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
