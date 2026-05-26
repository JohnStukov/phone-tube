package app.phonetube.core.media

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object MediaErrors {
    const val NETWORK = "error_network"
    const val GENERIC = "error_generic"
    const val SIGN_IN = "sign_in_required_action"
    const val PLAYLIST_UNAVAILABLE = "error_playlist_unavailable"

    fun codeFor(e: Throwable): String = when (e) {
        is NotSignedInException -> SIGN_IN
        is UnknownHostException,
        is SocketTimeoutException,
        is IOException -> NETWORK
        else -> GENERIC
    }
}
