package app.phonetube.navigation

enum class BottomTab(val route: String) {
    HOME(Routes.HOME),
    SHORTS(Routes.SHORTS),
    SUBSCRIPTIONS(Routes.SUBSCRIPTIONS),
    LIBRARY(Routes.LIBRARY);

    companion object {
        fun fromRoute(route: String?): BottomTab? =
            values().firstOrNull { it.route == route }
    }
}
