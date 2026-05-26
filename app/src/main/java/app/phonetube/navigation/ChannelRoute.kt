package app.phonetube.navigation

import android.net.Uri

object ChannelRoute {
    fun channel(channelId: String, channelName: String? = null): String {
        val id = channelId.trim()
        require(id.isNotEmpty()) { "channelId required" }
        val encodedId = Uri.encode(id)
        val name = channelName?.trim()?.takeIf { it.isNotEmpty() } ?: return "channel/$encodedId"
        return "channel/$encodedId?channelName=${Uri.encode(name)}"
    }

    fun decodeChannelId(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return Uri.decode(encoded).trim()
    }

    fun decodeChannelName(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return Uri.decode(encoded).trim().takeIf { it.isNotEmpty() }
    }
}
