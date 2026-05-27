package app.phonetube.core.media.pending

import android.content.Context
import app.phonetube.core.media.YouTubeRepository
import app.phonetube.core.media.network.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PendingActionsSyncer private constructor(
    context: Context,
    private val connectivity: ConnectivityMonitor,
    private val pendingActions: PendingActionsRepository,
    private val repository: YouTubeRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            connectivity.isOnline.collect { online ->
                    if (online) {
                        pendingActions.drain(repository)
                    }
                }
        }
    }

    companion object {
        @Volatile
        private var instance: PendingActionsSyncer? = null

        fun get(context: Context): PendingActionsSyncer {
            val app = context.applicationContext
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: PendingActionsSyncer(
                    app,
                    ConnectivityMonitor.get(app),
                    PendingActionsRepository.get(app),
                    YouTubeRepository(app)
                ).also { instance = it }
            }
        }
    }
}
