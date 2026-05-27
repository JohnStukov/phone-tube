package app.phonetube

import android.app.Application
import androidx.multidex.MultiDex
import app.phonetube.core.media.PhoneTubeMediaInit
import app.phonetube.core.media.network.ConnectivityMonitor
import app.phonetube.core.media.pending.PendingActionsSyncer
import app.phonetube.cast.PhoneTubeCastController
import app.phonetube.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhoneTubeApp : Application() {

    @Inject
    lateinit var castController: PhoneTubeCastController

    override fun onCreate() {
        super.onCreate()
        MultiDex.install(this)
        PhoneTubeMediaInit.init(this)
        ConnectivityMonitor.get(this).start()
        PendingActionsSyncer.get(this).start()
        castController.initialize()
        AppLogger.d("app", "PhoneTube initialized")
    }
}
