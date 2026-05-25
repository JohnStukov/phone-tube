package app.phonetube.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.phonetube.R
import app.phonetube.core.media.AuthRepository
import app.phonetube.navigation.TopBarActions
import app.phonetube.ui.components.YouTubeChipRow
import app.phonetube.ui.components.YouTubeTopBar
import app.phonetube.ui.feed.VideoFeedList

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onVideoClick: (videoId: String, isLive: Boolean) -> Unit,
    onOpenAccount: () -> Unit,
    topBarActions: TopBarActions = TopBarActions(),
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val authState by AuthRepository.get(
        androidx.compose.ui.platform.LocalContext.current
    ).state.collectAsState()

    LaunchedEffect(authState.isSignedIn, authState.selectedAccount?.id) {
        viewModel.load(state.feed)
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        YouTubeTopBar(
            onSearchClick = topBarActions.onSearchClick,
            onCastClick = topBarActions.onCastClick,
            onNotificationsClick = topBarActions.onNotificationsClick,
            onAccountClick = onOpenAccount,
            accountName = authState.selectedAccount?.name,
            accountImageUrl = authState.selectedAccount?.avatarUrl
        )
        YouTubeChipRow(
            selected = state.feed,
            onSelect = { viewModel.load(it) }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .pullRefresh(pullRefreshState)
        ) {
            VideoFeedList(
                videos = state.videos,
                isLoading = state.isLoading && !state.isRefreshing,
                error = state.error,
                emptyMessage = stringResource(R.string.home_empty),
                onVideoClick = onVideoClick,
                modifier = Modifier.fillMaxSize(),
                isLoadingMore = state.isLoadingMore,
                canLoadMore = state.canLoadMore,
                onLoadMore = { viewModel.loadMore() }
            )
            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
