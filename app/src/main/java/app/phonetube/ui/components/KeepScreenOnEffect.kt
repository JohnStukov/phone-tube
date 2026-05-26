package app.phonetube.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val previous = view.keepScreenOn
        view.keepScreenOn = enabled
        onDispose {
            view.keepScreenOn = previous
        }
    }
}
