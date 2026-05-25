package app.phonetube.core.media

data class AccountInfo(
    val id: Int,
    val name: String?,
    val email: String?,
    val avatarUrl: String?,
    val isSelected: Boolean
)
