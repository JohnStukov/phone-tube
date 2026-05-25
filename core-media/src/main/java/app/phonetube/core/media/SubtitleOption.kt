package app.phonetube.core.media

data class SubtitleOption(
    val id: String,
    val label: String,
    val baseUrl: String?,
    val languageCode: String? = null,
    val mimeType: String? = null
) {
    val isOff: Boolean get() = baseUrl.isNullOrBlank()
}
