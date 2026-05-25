package app.phonetube.core.media

data class VideoComment(
    val id: String?,
    val message: String,
    val authorName: String?,
    val authorPhotoUrl: String?,
    val publishedDate: String?,
    val likeCount: String?
)
