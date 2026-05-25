package app.phonetube

import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.phonetube.ui.PhoneTubeNavHost
import app.phonetube.ui.splash.PhoneTubeSplashScreen
import app.phonetube.ui.theme.PhoneTubeTheme

class MainActivity : ComponentActivity() {

    private val keepSystemSplash = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSystemSplash.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyTaskDescription()

        setContent {
            var showAnimatedSplash by remember { mutableStateOf(true) }

            PhoneTubeTheme {
                Crossfade(
                    targetState = showAnimatedSplash,
                    animationSpec = tween(durationMillis = 450),
                    label = "splashCrossfade"
                ) { isSplash ->
                    if (isSplash) {
                        PhoneTubeSplashScreen(
                            onFinished = {
                                showAnimatedSplash = false
                                keepSystemSplash.value = false
                                applyTaskDescription()
                            }
                        )
                    } else {
                        PhoneTubeNavHost()
                    }
                }
            }
        }
    }

    private fun applyTaskDescription() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_launcher_full) ?: return
        val size = (resources.displayMetrics.density * 48).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        val color = ContextCompat.getColor(this, R.color.phonetube_primary)
        @Suppress("DEPRECATION")
        setTaskDescription(
            ActivityManager.TaskDescription(
                getString(R.string.app_name),
                bitmap,
                color
            )
        )
    }
}
