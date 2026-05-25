package app.phonetube.core.media

import android.content.Context
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.youtubeapi.service.YouTubeSignInService

object PhoneTubeMediaInit {
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            GlobalPreferences.instance(context.applicationContext)
            YouTubeSignInService.instance().ensureAccountsRestored()
            YouTubeServiceManager.instance().refreshCacheIfNeeded()
            initialized = true
        }
    }
}
