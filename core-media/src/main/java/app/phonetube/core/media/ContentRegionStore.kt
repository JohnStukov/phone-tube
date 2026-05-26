package app.phonetube.core.media

import android.content.Context
import com.liskovsoft.googlecommon.common.locale.LocaleManager
import com.liskovsoft.sharedutils.locale.LocaleUpdater
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContentRegionStore private constructor(context: Context) {
    private val prefs = GlobalPreferences.instance(context.applicationContext)
    private val _regionChanges = MutableStateFlow(0)
    val regionChanges: StateFlow<Int> = _regionChanges.asStateFlow()

    fun isAuto(): Boolean = prefs.getPreferredCountry().isNullOrEmpty()

    fun getCountryCode(): String? = prefs.getPreferredCountry()?.takeIf { it.isNotEmpty() }

    fun setAuto() {
        prefs.setPreferredCountry("")
        applyApiRegion()
    }

    fun setCountry(isoCode: String) {
        prefs.setPreferredCountry(isoCode.uppercase(Locale.US))
        applyApiRegion()
    }

    private fun applyApiRegion() {
        LocaleUpdater.clearCache()
        LocaleManager.unhold()
        _regionChanges.value += 1
    }

    companion object {
        val SUPPORTED_COUNTRY_CODES: List<String> = listOf(
            "US", "GB", "CA", "MX", "AR", "CL", "CO", "PE", "BR",
            "ES", "DE", "FR", "IT", "PT", "NL", "PL", "RU", "UA",
            "JP", "KR", "IN", "AU", "NZ", "ZA", "AE", "SA", "TR"
        )

        @Volatile
        private var instance: ContentRegionStore? = null

        fun get(context: Context): ContentRegionStore {
            return instance ?: synchronized(this) {
                instance ?: ContentRegionStore(context.applicationContext).also { instance = it }
            }
        }

        fun displayCountryName(code: String, uiLocale: Locale = Locale.getDefault()): String {
            return Locale("", code).getDisplayCountry(uiLocale)
        }
    }
}
