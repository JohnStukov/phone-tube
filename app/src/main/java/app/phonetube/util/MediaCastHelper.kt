package app.phonetube.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import app.phonetube.R
import app.phonetube.cast.PhoneTubeCastController

object MediaCastHelper {
    fun openCastPicker(context: Context, castController: PhoneTubeCastController? = null) {
        val activity = context.findActivity() ?: run {
            showUnavailable(context)
            return
        }
        if (castController != null) {
            val opened = runCatching {
                castController.initialize()
                if (castController.isAvailable()) {
                    castController.openCastPicker(activity)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (opened) return
        }
        val launched = tryLaunchMediaOutputPanel(activity) ||
            tryLaunchCastSettings(activity) ||
            tryLaunchBluetoothSettings(activity)
        if (!launched) {
            showUnavailable(context)
        }
    }

    private fun tryLaunchMediaOutputPanel(activity: android.app.Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            activity.startActivity(
                Intent("android.settings.panel.action.MEDIA_OUTPUT").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
    }

    private fun tryLaunchCastSettings(activity: android.app.Activity): Boolean {
        return runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_CAST_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
    }

    private fun tryLaunchBluetoothSettings(activity: android.app.Activity): Boolean =
        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)

    private fun showUnavailable(context: Context) {
        Toast.makeText(context, R.string.cast_unavailable, Toast.LENGTH_SHORT).show()
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
