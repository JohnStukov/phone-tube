package app.phonetube.core.media

data class StreamQualityOption(
    val label: String,
    val streamUrl: String?,
    val audioStreamUrl: String? = null
)
