package app.phonetube.navigation

data class TopBarActions(
    val onSearchClick: () -> Unit = {},
    val onCastClick: () -> Unit = {},
    val onNotificationsClick: () -> Unit = {}
)
