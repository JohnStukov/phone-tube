package app.phonetube.core.playback

import android.content.Context

enum class CaptionSize(val fraction: Float) {
    SMALL(0.04f),
    MEDIUM(0.0533f),
    LARGE(0.07f)
}

class PlayerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var captionSize: CaptionSize
        get() = CaptionSize.values().getOrElse(prefs.getInt(KEY_CAPTION_SIZE, 1)) { CaptionSize.MEDIUM }
        set(value) = prefs.edit().putInt(KEY_CAPTION_SIZE, value.ordinal).apply()

    var preferredSubtitleId: String?
        get() = prefs.getString(KEY_SUBTITLE_ID, null)
        set(value) = prefs.edit().putString(KEY_SUBTITLE_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "phone_player_prefs"
        private const val KEY_CAPTION_SIZE = "caption_size"
        private const val KEY_SUBTITLE_ID = "preferred_subtitle_id"
    }
}
