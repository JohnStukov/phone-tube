package app.phonetube.ui



import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.hilt.navigation.compose.hiltViewModel

import androidx.navigation.NavHostController

import androidx.navigation.NavType

import androidx.navigation.compose.NavHost

import androidx.navigation.compose.composable

import androidx.navigation.compose.navigation

import androidx.navigation.compose.currentBackStackEntryAsState

import androidx.navigation.compose.rememberNavController

import androidx.navigation.navArgument

import app.phonetube.PhoneTubeApp
import app.phonetube.R
import app.phonetube.core.media.network.ConnectivityMonitor

import app.phonetube.navigation.BottomTab

import app.phonetube.navigation.ChannelRoute
import app.phonetube.navigation.Routes

import app.phonetube.navigation.TopBarActions

import app.phonetube.ui.notifications.NotificationsScreen

import app.phonetube.ui.search.SearchScreen

import app.phonetube.util.MediaCastHelper

import app.phonetube.ui.auth.SignInScreen

import app.phonetube.ui.components.KeepScreenOnEffect
import app.phonetube.ui.components.YouTubeBottomBar

import app.phonetube.ui.home.HomeScreen

import app.phonetube.ui.library.LibraryScreen

import app.phonetube.ui.player.MiniPlayerOverlay

import app.phonetube.ui.player.PlaybackHostViewModel

import app.phonetube.ui.player.PlayerDisplayMode

import app.phonetube.ui.player.PlayerScreen

import app.phonetube.ui.settings.SettingsScreen

import app.phonetube.ui.channel.ChannelScreen
import app.phonetube.ui.shorts.ShortsScreen

import app.phonetube.ui.subscriptions.SubscriptionsScreen



@Composable

fun PhoneTubeNavHost(

    modifier: Modifier = Modifier,

    playbackHost: PlaybackHostViewModel = hiltViewModel()

) {

    val navController = rememberNavController()

    val context = LocalContext.current
    val castController = (context.applicationContext as PhoneTubeApp).castController
    val connectivity = remember { ConnectivityMonitor.get(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isOnline by connectivity.isOnline.collectAsState()
    var wasOnline by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(isOnline) {
        if (!isOnline) {
            snackbarHostState.showSnackbar(context.getString(R.string.network_offline))
        } else if (!wasOnline) {
            snackbarHostState.showSnackbar(context.getString(R.string.network_online))
        }
        wasOnline = isOnline
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = Routes.showsBottomBar(currentRoute)

    var lastBottomTab by rememberSaveable { mutableStateOf(BottomTab.HOME) }
    LaunchedEffect(currentRoute) {
        BottomTab.fromRoute(currentRoute)?.let { lastBottomTab = it }
    }
    val selectedTab = BottomTab.fromRoute(currentRoute) ?: lastBottomTab

    val playbackSession by playbackHost.session.collectAsState()
    val isOnPlayerRoute = currentRoute?.startsWith("player/") == true

    LaunchedEffect(currentRoute, playbackSession?.videoId, playbackSession?.mode) {
        if (!isOnPlayerRoute) {
            playbackHost.minimizeIfLeavingPlayerScreen()
        }
    }

    KeepScreenOnEffect(enabled = playbackSession != null || isOnPlayerRoute)



    val openSignIn: () -> Unit = {

        navController.navigate(Routes.SIGN_IN)

    }

    val openAccount: () -> Unit = {
        navController.navigateBottomTab(BottomTab.LIBRARY)
    }



    val topBarActions = TopBarActions(

        onSearchClick = { navController.navigate(Routes.SEARCH) },

        onCastClick = { MediaCastHelper.openCastPicker(context, castController) },

        onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) }

    )

    val openPlayer: (String, Boolean) -> Unit = { videoId, isLive ->
        navController.navigate(Routes.player(videoId, isLive))
    }

    val expandMiniPlayer: () -> Unit = {
        playbackSession?.let { session ->
            playbackHost.setFullMode()
            navController.navigate(Routes.player(session.videoId, session.isLive)) {
                launchSingleTop = true
            }
        }
    }



    Box(modifier = modifier.fillMaxSize()) {

        Scaffold(

            modifier = Modifier.fillMaxSize(),

            snackbarHost = { SnackbarHost(snackbarHostState) },

            bottomBar = {

                if (showBottomBar) {

                    YouTubeBottomBar(

                        selectedTab = selectedTab,

                        onTabSelected = { tab ->
                            navController.navigateBottomTab(tab)
                        }

                    )

                }

            }

        ) { padding ->

            NavHost(

                navController = navController,

                startDestination = Routes.BOTTOM_NAV,

                modifier = Modifier

                    .fillMaxSize()

                    .padding(padding)

            ) {

                navigation(
                    route = Routes.BOTTOM_NAV,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onVideoClick = openPlayer,
                            onOpenAccount = openAccount,
                            topBarActions = topBarActions
                        )
                    }

                    composable(Routes.SHORTS) {
                        ShortsScreen(
                            onChannelClick = { channelId, channelName ->
                                navController.navigate(Routes.channel(channelId, channelName)) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(Routes.SUBSCRIPTIONS) {
                        SubscriptionsScreen(
                            onVideoClick = openPlayer,
                            onSignIn = openSignIn,
                            onOpenAccount = openAccount,
                            topBarActions = topBarActions
                        )
                    }

                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            onVideoClick = openPlayer,
                            onSignIn = openSignIn,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onOpenAccount = openAccount,
                            topBarActions = topBarActions
                        )
                    }
                }

                composable(
                    route = Routes.CHANNEL,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType },
                        navArgument("channelName") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    val channelId = ChannelRoute.decodeChannelId(
                        entry.arguments?.getString("channelId")
                    )
                    val channelName = ChannelRoute.decodeChannelName(
                        entry.arguments?.getString("channelName")
                    )
                    ChannelScreen(
                        channelId = channelId,
                        channelName = channelName,
                        onBack = { navController.popBackStack() },
                        onVideoClick = openPlayer,
                        onSignIn = openSignIn
                    )
                }

                composable(Routes.SEARCH) {

                    SearchScreen(

                        onBack = { navController.popBackStack() },

                        onVideoClick = openPlayer

                    )

                }

                composable(Routes.NOTIFICATIONS) {

                    NotificationsScreen(

                        onBack = { navController.popBackStack() },

                        onVideoClick = openPlayer,

                        onSignIn = openSignIn

                    )

                }

                composable(Routes.SIGN_IN) {

                    SignInScreen(

                        onBack = { navController.popBackStack() },

                        onSignedIn = {
                            navController.navigate(Routes.SUBSCRIPTIONS) {
                                popUpTo(Routes.SIGN_IN) { inclusive = true }
                                popUpTo(Routes.BOTTOM_NAV) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }

                    )

                }

                composable(

                    route = Routes.PLAYER,

                    arguments = listOf(

                        navArgument("videoId") { type = NavType.StringType },

                        navArgument("isLive") { type = NavType.BoolType }

                    )

                ) { entry ->

                    val videoId = entry.arguments?.getString("videoId").orEmpty()

                    val isLive = entry.arguments?.getBoolean("isLive") ?: false

                    PlayerScreen(

                        videoId = videoId,

                        isLive = isLive,

                        playbackHost = playbackHost,

                        onBack = { navController.popBackStack() },

                        onRelatedVideoClick = openPlayer,

                        onChannelClick = { channelId, channelName ->
                            navController.navigate(Routes.channel(channelId, channelName)) {
                                launchSingleTop = true
                            }
                        },

                        onSearchClick = topBarActions.onSearchClick,

                        onCastClick = topBarActions.onCastClick

                    )

                }

                composable(Routes.SETTINGS) {

                    SettingsScreen(onBack = { navController.popBackStack() })

                }

            }

        }



        val miniSession = playbackSession

        if (miniSession != null && miniSession.mode == PlayerDisplayMode.MINI && !isOnPlayerRoute) {

            MiniPlayerOverlay(

                session = miniSession,

                controller = playbackHost.getOrCreateController(),

                showBottomBar = showBottomBar,

                onExpand = expandMiniPlayer,

                onDismiss = { playbackHost.stop() },

                onOffsetChange = { x, y, initialized ->

                    playbackHost.updateMiniOffset(x, y, initialized)

                }

            )

        }

    }

}

private fun NavHostController.navigateBottomTab(tab: BottomTab) {
    if (currentDestination?.route == tab.route) return
    navigate(tab.route) {
        popUpTo(Routes.BOTTOM_NAV) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

