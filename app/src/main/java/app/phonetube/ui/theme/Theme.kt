package app.phonetube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PhoneTubePrimary,
    onPrimary = Color(0xFFFFFFFF),
    secondary = PhoneTubePrimaryDark,
    background = YouTubeBackgroundDark,
    surface = YouTubeSurfaceDark,
    surfaceVariant = YouTubeSurfaceVariantDark,
    onBackground = YouTubeOnSurfaceDark,
    onSurface = YouTubeOnSurfaceDark,
    onSurfaceVariant = YouTubeOnSurfaceVariantDark,
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF0F0F0F)
)

private val LightColorScheme = lightColorScheme(
    primary = PhoneTubePrimary,
    onPrimary = Color(0xFFFFFFFF),
    secondary = PhoneTubePrimaryDark,
    background = YouTubeBackgroundLight,
    surface = YouTubeSurfaceLight,
    surfaceVariant = YouTubeSurfaceVariantLight,
    onBackground = YouTubeOnSurfaceLight,
    onSurface = YouTubeOnSurfaceLight,
    onSurfaceVariant = YouTubeOnSurfaceVariantLight,
    inverseSurface = Color(0xFF0F0F0F),
    inverseOnSurface = Color(0xFFFFFFFF)
)

@Composable
fun PhoneTubeTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
