package app.phonetube.ui.library

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.phonetube.R
import app.phonetube.core.media.AccountInfo
import app.phonetube.core.media.AuthState
import app.phonetube.navigation.TopBarActions
import app.phonetube.ui.auth.AuthViewModel
import app.phonetube.ui.channel.ChannelPlaylistRow
import app.phonetube.ui.components.AppVersionLabel
import app.phonetube.ui.components.ChannelAvatar
import app.phonetube.ui.components.PullRefreshBox
import app.phonetube.ui.components.YouTubeTopBar
import app.phonetube.ui.components.YouTubeVideoCard
import app.phonetube.util.resolveMediaErrorMessage

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LibraryScreen(
    onVideoClick: (videoId: String, isLive: Boolean) -> Unit,
    onSignIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit = {},
    topBarActions: TopBarActions = TopBarActions(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val state by libraryViewModel.state.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        YouTubeTopBar(
            onSearchClick = topBarActions.onSearchClick,
            onCastClick = topBarActions.onCastClick,
            onNotificationsClick = topBarActions.onNotificationsClick,
            onAccountClick = onOpenAccount,
            accountName = state.auth.selectedAccount?.name,
            accountImageUrl = state.auth.selectedAccount?.avatarUrl
        )

        LibraryHeader(
            auth = state.auth,
            onSignIn = onSignIn,
            onSignOut = { authViewModel.signOut() },
            onSelectAccount = { authViewModel.selectAccount(it) },
            onOpenSettings = onOpenSettings
        )

        Divider()

        if (state.auth.isSignedIn && state.showingCachedData) {
            Text(
                text = stringResource(R.string.offline_cached_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (!state.auth.isSignedIn) {
                Text(
                    text = stringResource(R.string.library_sign_in_for_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                PullRefreshBox(
                    refreshing = state.isRefreshing,
                    onRefresh = { libraryViewModel.refresh() }
                ) {
                    when {
                        state.isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        else -> {
                            val errorMessage = resolveMediaErrorMessage(state.error)
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item(key = "history_title") {
                                    Text(
                                        text = stringResource(R.string.library_history),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                if (state.historyVideos.isEmpty()) {
                                    item(key = "history_empty") {
                                        Text(
                                            text = stringResource(R.string.library_history_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    items(state.historyVideos, key = { it.stableListKey }) { video ->
                                        YouTubeVideoCard(
                                            video = video,
                                            onClick = {
                                                libraryViewModel.openItem(
                                                    video,
                                                    onOpenVideo = onVideoClick,
                                                    onPlaylistUnavailable = {
                                                        Toast.makeText(
                                                            context,
                                                            R.string.error_playlist_unavailable,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                                item(key = "watch_later_title") {
                                    Text(
                                        text = stringResource(R.string.library_watch_later),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                if (state.watchLaterVideos.isEmpty()) {
                                    item(key = "watch_later_empty") {
                                        Text(
                                            text = stringResource(R.string.library_watch_later_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    items(state.watchLaterVideos, key = { it.stableListKey }) { video ->
                                        YouTubeVideoCard(
                                            video = video,
                                            onClick = {
                                                libraryViewModel.openItem(
                                                    video,
                                                    onOpenVideo = onVideoClick,
                                                    onPlaylistUnavailable = {
                                                        Toast.makeText(
                                                            context,
                                                            R.string.error_playlist_unavailable,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                                item(key = "liked_title") {
                                    Text(
                                        text = stringResource(R.string.library_liked),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                if (state.likedVideos.isEmpty()) {
                                    item(key = "liked_empty") {
                                        Text(
                                            text = stringResource(R.string.library_liked_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    items(state.likedVideos, key = { it.stableListKey }) { video ->
                                        YouTubeVideoCard(
                                            video = video,
                                            onClick = {
                                                libraryViewModel.openItem(
                                                    video,
                                                    onOpenVideo = onVideoClick,
                                                    onPlaylistUnavailable = {
                                                        Toast.makeText(
                                                            context,
                                                            R.string.error_playlist_unavailable,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                                item(key = "playlists_title") {
                                    Text(
                                        text = stringResource(R.string.library_playlists),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        )
                                    )
                                }
                                if (state.playlistVideos.isEmpty()) {
                                    item(key = "playlists_empty") {
                                        Text(
                                            text = stringResource(R.string.library_playlists_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    items(state.playlistVideos, key = { it.stableListKey }) { playlist ->
                                        ChannelPlaylistRow(
                                            playlist = playlist,
                                            onClick = {
                                                libraryViewModel.openItem(
                                                    playlist,
                                                    onOpenVideo = onVideoClick,
                                                    onPlaylistUnavailable = {
                                                        Toast.makeText(
                                                            context,
                                                            R.string.error_playlist_unavailable,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                                if (errorMessage != null) {
                                    item(key = "library_error") {
                                        Text(
                                            text = errorMessage,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AppVersionLabel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LibraryHeader(
    auth: AuthState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSelectAccount: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (auth.isSignedIn) {
            val account = auth.selectedAccount
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(
                    name = account?.name,
                    imageUrl = account?.avatarUrl,
                    size = 48.dp
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = account?.name.orEmpty().ifBlank { stringResource(R.string.account) },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    account?.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clickable(onClick = onOpenSettings)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (auth.accounts.size > 1) {
                Text(
                    text = stringResource(R.string.account_switch),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                auth.accounts.forEach { item ->
                    AccountRow(item = item, onSelect = { onSelectAccount(item.id) })
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.sign_out))
            }
        } else {
            Text(
                text = stringResource(R.string.tab_library_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.sign_in_button))
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.settings))
            }
        }
    }
}

@Composable
private fun AccountRow(item: AccountInfo, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = item.isSelected, onClick = onSelect)
        ChannelAvatar(
            name = item.name,
            imageUrl = item.avatarUrl,
            size = 32.dp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Column {
            Text(text = item.name.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            item.email?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
