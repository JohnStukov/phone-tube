package app.phonetube.ui.components

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import app.phonetube.core.playback.PhonePlayerController
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView

fun createTouchTransparentPlayerView(
    context: Context,
    controller: PhonePlayerController,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
): PlayerView {
    return PlayerView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        useController = false
        this.resizeMode = resizeMode
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        setOnTouchListener { _, _ -> false }
        controller.attachPlayerView(this)
    }
}
