package app.phonetube.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.phonetube.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeTopBar(
    onSearchClick: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    accountName: String? = null,
    accountImageUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhoneTubeLogo(modifier = Modifier.weight(1f))
            IconButton(onClick = onCastClick) {
                Icon(
                    Icons.Outlined.Cast,
                    contentDescription = stringResource(R.string.cast),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onNotificationsClick) {
                Icon(
                    Icons.Outlined.NotificationsNone,
                    contentDescription = stringResource(R.string.notifications),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onAccountClick) {
                ChannelAvatar(
                    name = accountName,
                    imageUrl = accountImageUrl,
                    size = 28.dp,
                    contentDescription = stringResource(R.string.account)
                )
            }
        }
    }
}

@Composable
fun YouTubePlayerTopBar(
    onBack: () -> Unit,
    onCastClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            PhoneTubeLogo(modifier = Modifier.weight(1f))
            IconButton(onClick = onCastClick) {
                Icon(
                    Icons.Outlined.Cast,
                    contentDescription = stringResource(R.string.cast),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
