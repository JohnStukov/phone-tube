package app.phonetube.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.phonetube.R
import app.phonetube.core.media.MediaErrors

@StringRes
fun mediaErrorStringRes(errorCode: String?): Int? = when (errorCode) {
    MediaErrors.NETWORK -> R.string.error_network
    MediaErrors.GENERIC -> R.string.error_generic
    MediaErrors.SIGN_IN -> R.string.sign_in_required_action
    MediaErrors.PLAYLIST_UNAVAILABLE -> R.string.error_playlist_unavailable
    else -> null
}

fun resolveMediaErrorMessage(context: Context, error: String?): String? {
    if (error.isNullOrBlank()) return null
    val resId = mediaErrorStringRes(error)
    return if (resId != null) context.getString(resId) else error
}

@Composable
fun resolveMediaErrorMessage(error: String?): String? {
    if (error.isNullOrBlank()) return null
    val resId = mediaErrorStringRes(error)
    return if (resId != null) stringResource(resId) else error
}
