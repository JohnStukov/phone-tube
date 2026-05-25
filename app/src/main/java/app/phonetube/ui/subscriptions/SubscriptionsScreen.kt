package app.phonetube.ui.subscriptions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.phonetube.R
import app.phonetube.core.media.AuthRepository
import app.phonetube.navigation.TopBarActions
import app.phonetube.ui.components.YouTubeTopBar
import app.phonetube.ui.feed.VideoFeedList

@Composable
fun SubscriptionsScreen(
    onVideoClick: (videoId: String, isLive: Boolean) -> Unit,
    onSignIn: () -> Unit,
    onOpenAccount: () -> Unit,
    topBarActions: TopBarActions = TopBarActions(),
    viewModel: SubscriptionsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val authState by AuthRepository.get(LocalContext.current).state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        YouTubeTopBar(
            onSearchClick = topBarActions.onSearchClick,
            onCastClick = topBarActions.onCastClick,
            onNotificationsClick = topBarActions.onNotificationsClick,
            onAccountClick = onOpenAccount,
            accountName = authState.selectedAccount?.name,
            accountImageUrl = authState.selectedAccount?.avatarUrl
        )

        Box(modifier = Modifier.weight(1f)) {
            if (state.needsSignIn) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.tab_subscriptions_message),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Button(onClick = onSignIn) {
                        Text(stringResource(R.string.sign_in_button))
                    }
                }
            } else {
                VideoFeedList(
                    videos = state.videos,
                    isLoading = state.isLoading,
                    error = state.error,
                    emptyMessage = stringResource(R.string.subscriptions_empty),
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
