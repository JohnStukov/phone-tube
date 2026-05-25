package app.phonetube.navigation

object Routes {
    const val HOME = "home"
    const val SHORTS = "shorts"
    const val SUBSCRIPTIONS = "subscriptions"
    const val LIBRARY = "library"
    const val PLAYER = "player/{videoId}/{isLive}"
    const val SETTINGS = "settings"
    const val SIGN_IN = "sign_in"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val CHANNEL = "channel/{channelId}"

    val bottomNavRoutes = setOf(HOME, SHORTS, SUBSCRIPTIONS, LIBRARY)

    fun player(videoId: String, isLive: Boolean) = "player/$videoId/$isLive"

    fun channel(channelId: String, channelName: String? = null): String =
        ChannelRoute.channel(channelId, channelName)
}
