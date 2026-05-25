package app.phonetube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class FeedChipStyle(
    val containerColor: Color,
    val labelColor: Color
)

fun feedChipStyle(selected: Boolean, darkTheme: Boolean): FeedChipStyle {
    return if (darkTheme) {
        if (selected) {
            FeedChipStyle(
                containerColor = Color(0xFFFFFFFF),
                labelColor = Color(0xFF000000)
            )
        } else {
            FeedChipStyle(
                containerColor = YouTubeSurfaceVariantDark,
                labelColor = YouTubeOnSurfaceDark
            )
        }
    } else {
        if (selected) {
            FeedChipStyle(
                containerColor = Color(0xFF0F0F0F),
                labelColor = Color(0xFFFFFFFF)
            )
        } else {
            FeedChipStyle(
                containerColor = YouTubeSurfaceVariantLight,
                labelColor = YouTubeOnSurfaceLight
            )
        }
    }
}

@Composable
fun feedChipStyle(selected: Boolean): FeedChipStyle =
    feedChipStyle(selected, isSystemInDarkTheme())
