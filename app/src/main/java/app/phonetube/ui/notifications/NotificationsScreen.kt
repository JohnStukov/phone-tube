package app.phonetube.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.phonetube.R
import app.phonetube.ui.components.PullRefreshBox
import app.phonetube.ui.feed.VideoFeedList
import app.phonetube.util.resolveMediaErrorMessage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onVideoClick: (videoId: String, isLive: Boolean) -> Unit,
    onSignIn: () -> Unit,
    viewModel: NotificationsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.notifications)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.needsSignIn -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.notifications_sign_in),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onSignIn, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.sign_in_button))
                    }
                }
                else -> PullRefreshBox(
                    refreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() }
                ) {
                    VideoFeedList(
                        videos = state.videos,
                        isLoading = state.isLoading && !state.isRefreshing,
                        error = resolveMediaErrorMessage(state.error),
                        emptyMessage = stringResource(R.string.notifications_empty),
                        onVideoClick = onVideoClick,
                        modifier = Modifier.fillMaxSize(),
                        isLoadingMore = state.isLoadingMore,
                        canLoadMore = state.canLoadMore,
                        onLoadMore = { viewModel.loadMore() }
                    )
                }
            }
        }
    }
}
