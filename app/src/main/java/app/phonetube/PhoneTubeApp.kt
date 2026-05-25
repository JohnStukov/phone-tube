package app.phonetube

import android.app.Application
import androidx.multidex.MultiDex
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.PhoneTubeMediaInit

class PhoneTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MultiDex.install(this)
        PhoneTubeMediaInit.init(this)
        AuthRepository.get(this)
    }
}
