package app.phonetube.core.playback

import android.content.Context
import java.util.Locale

enum class CaptionSize(val fraction: Float) {
    SMALL(0.04f),
    MEDIUM(0.0533f),
    LARGE(0.062f)
}

class PlayerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var captionSize: CaptionSize
        get() = CaptionSize.values().getOrElse(prefs.getInt(KEY_CAPTION_SIZE, 1)) { CaptionSize.MEDIUM }
        set(value) = prefs.edit().putInt(KEY_CAPTION_SIZE, value.ordinal).apply()

    var preferredSubtitleId: String?
        get() = prefs.getString(KEY_SUBTITLE_ID, null)
        set(value) = prefs.edit().putString(KEY_SUBTITLE_ID, value).apply()

    var audioLanguageMode: AudioLanguageMode
        get() = AudioLanguageMode.values().getOrElse(
            prefs.getInt(KEY_AUDIO_LANG_MODE, AudioLanguageMode.SYSTEM.ordinal)
        ) { AudioLanguageMode.SYSTEM }
        set(value) = prefs.edit().putInt(KEY_AUDIO_LANG_MODE, value.ordinal).apply()

    var audioLanguageCode: String?
        get() = prefs.getString(KEY_AUDIO_LANG_CODE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_AUDIO_LANG_CODE, value).apply()

    fun resolvePreferredAudioLanguage(): String? = when (audioLanguageMode) {
        AudioLanguageMode.SYSTEM -> Locale.getDefault().language.takeIf { it.isNotBlank() }
        AudioLanguageMode.MANUAL -> audioLanguageCode
    }

    companion object {
        private const val PREFS_NAME = "phone_player_prefs"
        private const val KEY_CAPTION_SIZE = "caption_size"
        private const val KEY_SUBTITLE_ID = "preferred_subtitle_id"
        private const val KEY_AUDIO_LANG_MODE = "audio_language_mode"
        private const val KEY_AUDIO_LANG_CODE = "audio_language_code"
    }
}
