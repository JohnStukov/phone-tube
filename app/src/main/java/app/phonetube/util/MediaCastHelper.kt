package app.phonetube.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import app.phonetube.R

object MediaCastHelper {
    fun openCastPicker(context: Context) {
        val activity = context.findActivity() ?: run {
            Toast.makeText(context, R.string.cast_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.startActivity(
                    Intent("android.settings.panel.action.MEDIA_OUTPUT")
                )
            } else {
                activity.startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
            }
        } catch (_: Exception) {
            Toast.makeText(context, R.string.cast_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
