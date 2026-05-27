package app.phonetube.core.media.pending

import android.content.Context
import app.phonetube.core.media.YouTubeRepository
import app.phonetube.core.media.cache.PhoneTubeCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PendingActionsRepository private constructor(
    private val dao: PendingActionDao
) {
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    suspend fun enqueue(type: PendingActionType, payload: JSONObject) =
        enqueuePayload(type, payload.toString())

    suspend fun enqueuePayload(type: PendingActionType, payloadJson: String) = withContext(Dispatchers.IO) {
        dao.insert(
            PendingActionEntity(
                type = type.name,
                payloadJson = payloadJson,
                createdAtMs = System.currentTimeMillis()
            )
        )
        refreshCount()
    }

    suspend fun drain(repository: YouTubeRepository) = withContext(Dispatchers.IO) {
        val actions = dao.all()
        for (action in actions) {
            runCatching {
                processAction(repository, action)
            }.onSuccess {
                dao.delete(action.id)
            }
        }
        refreshCount()
    }

    private suspend fun refreshCount() {
        _pendingCount.value = dao.count()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
        refreshCount()
    }

    private suspend fun processAction(repository: YouTubeRepository, action: PendingActionEntity) {
        val payload = JSONObject(action.payloadJson)
        when (PendingActionType.valueOf(action.type)) {
            PendingActionType.LIKE -> repository.setLike(payload.getString("videoId"))
            PendingActionType.REMOVE_LIKE -> repository.removeLike(payload.getString("videoId"))
            PendingActionType.DISLIKE -> repository.setDislike(payload.getString("videoId"))
            PendingActionType.REMOVE_DISLIKE -> repository.removeDislike(payload.getString("videoId"))
            PendingActionType.SUBSCRIBE -> repository.subscribe(payload.getString("channelId"))
            PendingActionType.UNSUBSCRIBE -> repository.unsubscribe(payload.getString("channelId"))
            PendingActionType.DISMISS_NOTIFICATION -> repository.dismissNotification(payload.getString("videoId"))
        }
    }

    companion object {
        fun forTest(dao: PendingActionDao): PendingActionsRepository = PendingActionsRepository(dao)

        @Volatile
        private var instance: PendingActionsRepository? = null

        fun get(context: Context): PendingActionsRepository {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: PendingActionsRepository(
                    PhoneTubeCacheDatabase.get(context.applicationContext).pendingActionDao()
                ).also { instance = it }
            }
        }
    }
}
