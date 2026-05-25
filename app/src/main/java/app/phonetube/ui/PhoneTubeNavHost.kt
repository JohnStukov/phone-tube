package app.phonetube.ui



import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Scaffold

import androidx.compose.runtime.Composable

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavGraph.Companion.findStartDestination

import androidx.navigation.NavType

import androidx.navigation.compose.NavHost

import androidx.navigation.compose.composable

import androidx.navigation.compose.currentBackStackEntryAsState

import androidx.navigation.compose.rememberNavController

import androidx.navigation.navArgument

import app.phonetube.R

import app.phonetube.navigation.BottomTab

import app.phonetube.navigation.ChannelRoute
import app.phonetube.navigation.Routes

import app.phonetube.navigation.TopBarActions

import app.phonetube.ui.notifications.NotificationsScreen

import app.phonetube.ui.search.SearchScreen

import app.phonetube.util.MediaCastHelper

import app.phonetube.ui.auth.SignInScreen

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

    playbackHost: PlaybackHostViewModel = viewModel()

) {

    val navController = rememberNavController()

    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in Routes.bottomNavRoutes

    val selectedTab = BottomTab.fromRoute(currentRoute) ?: BottomTab.HOME

    val playbackSession by playbackHost.session.collectAsState()

    val isOnPlayerRoute = currentRoute?.startsWith("player/") == true



    val openSignIn: () -> Unit = {

        navController.navigate(Routes.SIGN_IN)

    }

    val openAccount: () -> Unit = {

        navController.navigate(Routes.LIBRARY) {

            popUpTo(navController.graph.findStartDestination().id) {

                saveState = true

            }

            launchSingleTop = true

            restoreState = true

        }

    }



    val topBarActions = TopBarActions(

        onSearchClick = { navController.navigate(Routes.SEARCH) },

        onCastClick = { MediaCastHelper.openCastPicker(context) },

        onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) }

    )



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

            bottomBar = {

                if (showBottomBar) {

                    YouTubeBottomBar(

                        selectedTab = selectedTab,

                        onTabSelected = { tab ->

                            navController.navigate(tab.route) {

                                popUpTo(navController.graph.findStartDestination().id) {

                                    saveState = true

                                }

                                launchSingleTop = true

                                restoreState = true

                            }

                        }

                    )

                }

            }

        ) { padding ->

            NavHost(

                navController = navController,

                startDestination = Routes.HOME,

                modifier = Modifier

                    .fillMaxSize()

                    .padding(padding)

            ) {

                composable(Routes.HOME) {

                    HomeScreen(

                        onVideoClick = { videoId, isLive ->

                            navController.navigate(Routes.player(videoId, isLive))

                        },

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

                composable(
                    route = Routes.CHANNEL,
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType }
                    )
                ) { entry ->
                    val channelId = ChannelRoute.decodeChannelId(
                        entry.arguments?.getString("channelId")
                    )
                    val channelName: String? = null
                    ChannelScreen(
                        channelId = channelId,
                        channelName = channelName,
                        onBack = { navController.popBackStack() },
                        onVideoClick = { videoId, isLive ->
                            navController.navigate(Routes.player(videoId, isLive))
                        },
                        onSignIn = openSignIn
                    )
                }

                composable(Routes.SUBSCRIPTIONS) {

                    SubscriptionsScreen(

                        onVideoClick = { videoId, isLive ->

                            navController.navigate(Routes.player(videoId, isLive))

                        },

                        onSignIn = openSignIn,

                        onOpenAccount = openAccount,

                        topBarActions = topBarActions

                    )

                }

                composable(Routes.SEARCH) {

                    SearchScreen(

                        onBack = { navController.popBackStack() },

                        onVideoClick = { videoId, isLive ->

                            navController.navigate(Routes.player(videoId, isLive))

                        }

                    )

                }

                composable(Routes.NOTIFICATIONS) {

                    NotificationsScreen(

                        onBack = { navController.popBackStack() },

                        onVideoClick = { videoId, isLive ->

                            navController.navigate(Routes.player(videoId, isLive))

                        },

                        onSignIn = openSignIn

                    )

                }

                composable(Routes.LIBRARY) {

                    LibraryScreen(

                        onVideoClick = { videoId, isLive ->

                            navController.navigate(Routes.player(videoId, isLive))

                        },

                        onSignIn = openSignIn,

                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },

                        topBarActions = topBarActions

                    )

                }

                composable(Routes.SIGN_IN) {

                    SignInScreen(

                        onBack = { navController.popBackStack() },

                        onSignedIn = {

                            navController.popBackStack()

                            navController.navigate(Routes.SUBSCRIPTIONS) {

                                popUpTo(navController.graph.findStartDestination().id) {

                                    saveState = true

                                }

                                launchSingleTop = true

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

                        onRelatedVideoClick = { id, live ->

                            navController.navigate(Routes.player(id, live))

                        },

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

