package app.phonetube.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.phonetube.R
import app.phonetube.navigation.BottomTab

@Composable
fun YouTubeBottomBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == BottomTab.HOME,
            onClick = { onTabSelected(BottomTab.HOME) },
            icon = {
                Icon(
                    imageVector = navIcon(
                        selected = selectedTab == BottomTab.HOME,
                        selectedIcon = Icons.Filled.Home,
                        unselectedIcon = Icons.Outlined.Home
                    ),
                    contentDescription = stringResource(R.string.nav_home)
                )
            },
            label = { Text(stringResource(R.string.nav_home)) },
            colors = navItemColors(selectedTab == BottomTab.HOME)
        )
        NavigationBarItem(
            selected = selectedTab == BottomTab.SHORTS,
            onClick = { onTabSelected(BottomTab.SHORTS) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_shorts),
                    contentDescription = stringResource(R.string.nav_shorts),
                    tint = if (selectedTab == BottomTab.SHORTS) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            label = { Text(stringResource(R.string.nav_shorts)) },
            colors = navItemColors(selectedTab == BottomTab.SHORTS)
        )
        NavigationBarItem(
            selected = selectedTab == BottomTab.SUBSCRIPTIONS,
            onClick = { onTabSelected(BottomTab.SUBSCRIPTIONS) },
            icon = {
                Icon(
                    imageVector = navIcon(
                        selected = selectedTab == BottomTab.SUBSCRIPTIONS,
                        selectedIcon = Icons.Filled.Subscriptions,
                        unselectedIcon = Icons.Outlined.Subscriptions
                    ),
                    contentDescription = stringResource(R.string.nav_subscriptions)
                )
            },
            label = { Text(stringResource(R.string.nav_subscriptions)) },
            colors = navItemColors(selectedTab == BottomTab.SUBSCRIPTIONS)
        )
        NavigationBarItem(
            selected = selectedTab == BottomTab.LIBRARY,
            onClick = { onTabSelected(BottomTab.LIBRARY) },
            icon = {
                Icon(
                    imageVector = navIcon(
                        selected = selectedTab == BottomTab.LIBRARY,
                        selectedIcon = Icons.Filled.VideoLibrary,
                        unselectedIcon = Icons.Outlined.VideoLibrary
                    ),
                    contentDescription = stringResource(R.string.nav_library)
                )
            },
            label = { Text(stringResource(R.string.nav_library)) },
            colors = navItemColors(selectedTab == BottomTab.LIBRARY)
        )
    }
}

private fun navIcon(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector
): ImageVector = if (selected) selectedIcon else unselectedIcon

@Composable
private fun navItemColors(isSelected: Boolean) = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSurface,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
)
