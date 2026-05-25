package app.phonetube.core.playback

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment

class SponsorBlockPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var useAltServer: Boolean
        get() = prefs.getBoolean(KEY_ALT_SERVER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ALT_SERVER, value).apply()
            com.liskovsoft.sharedutils.prefs.GlobalPreferences.instance(appContext)
                .setContentBlockAltServerEnabled(value)
        }

    fun getEnabledCategories(): Set<String> {
        val stored = prefs.getStringSet(KEY_CATEGORIES, null)
        return stored ?: DEFAULT_CATEGORIES
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        val current = getEnabledCategories().toMutableSet()
        if (enabled) current.add(category) else current.remove(category)
        prefs.edit().putStringSet(KEY_CATEGORIES, current).apply()
    }

    fun isCategoryEnabled(category: String): Boolean = getEnabledCategories().contains(category)

    companion object {
        private const val PREFS_NAME = "phonetube_sponsorblock"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALT_SERVER = "alt_server"
        private const val KEY_CATEGORIES = "categories"

        val ALL_CATEGORIES = listOf(
            SponsorSegment.CATEGORY_SPONSOR,
            SponsorSegment.CATEGORY_INTRO,
            SponsorSegment.CATEGORY_OUTRO,
            SponsorSegment.CATEGORY_SELF_PROMO,
            SponsorSegment.CATEGORY_INTERACTION,
            SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC,
            SponsorSegment.CATEGORY_PREVIEW_RECAP,
            SponsorSegment.CATEGORY_POI_HIGHLIGHT,
            SponsorSegment.CATEGORY_FILLER
        )

        private val DEFAULT_CATEGORIES = ALL_CATEGORIES.toSet()
    }
}
