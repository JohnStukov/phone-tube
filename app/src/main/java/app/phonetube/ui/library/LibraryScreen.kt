package app.phonetube.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.phonetube.R
import app.phonetube.core.media.AccountInfo
import app.phonetube.core.media.AuthState
import app.phonetube.ui.auth.AuthViewModel
import app.phonetube.ui.components.AppVersionLabel
import app.phonetube.ui.components.ChannelAvatar
import app.phonetube.navigation.TopBarActions
import app.phonetube.ui.components.YouTubeTopBar
import app.phonetube.ui.feed.VideoFeedList

@Composable
fun LibraryScreen(
    onVideoClick: (videoId: String, isLive: Boolean) -> Unit,
    onSignIn: () -> Unit,
    onOpenSettings: () -> Unit,
    topBarActions: TopBarActions = TopBarActions(),
    libraryViewModel: LibraryViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val state by libraryViewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        YouTubeTopBar(
            onSearchClick = topBarActions.onSearchClick,
            onCastClick = topBarActions.onCastClick,
            onNotificationsClick = topBarActions.onNotificationsClick,
            onAccountClick = { }
        )

        LibraryHeader(
            auth = state.auth,
            onSignIn = onSignIn,
            onSignOut = { authViewModel.signOut() },
            onSelectAccount = { authViewModel.selectAccount(it) },
            onOpenSettings = onOpenSettings
        )

        Divider()

        Text(
            text = stringResource(R.string.library_history),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (!state.auth.isSignedIn) {
                Text(
                    text = stringResource(R.string.library_sign_in_for_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                VideoFeedList(
                    videos = state.historyVideos,
                    isLoading = state.isLoading,
                    error = state.error,
                    emptyMessage = stringResource(R.string.library_history_empty),
                    onVideoClick = onVideoClick,
                    modifier = Modifier.fillMaxSize()
                )
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
