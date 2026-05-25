package app.phonetube.navigation

import android.net.Uri

object ChannelRoute {
    fun channel(channelId: String, @Suppress("UNUSED_PARAMETER") channelName: String? = null): String {
        val id = channelId.trim()
        require(id.isNotEmpty()) { "channelId required" }
        return "channel/${Uri.encode(id)}"
    }

    fun decodeChannelId(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return Uri.decode(encoded).trim()
    }

    @Deprecated("Channel name is loaded on ChannelScreen; kept for call-site compat")
    fun decodeChannelName(encoded: String?): String? = null
}
