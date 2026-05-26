package app.phonetube.core.playback

import android.content.Context

class PlaybackPositionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPositionMs(videoId: String): Long {
        if (videoId.isBlank()) return 0L
        return prefs.getLong(key(videoId), 0L).coerceAtLeast(0L)
    }

    fun savePositionMs(videoId: String, positionMs: Long, durationMs: Long) {
        if (videoId.isBlank() || positionMs < MIN_SAVE_MS) return
        if (durationMs > 0L && positionMs >= durationMs * NEAR_END_FRACTION) {
            clear(videoId)
            return
        }
        prefs.edit().putLong(key(videoId), positionMs).apply()
    }

    fun clear(videoId: String) {
        if (videoId.isBlank()) return
        prefs.edit().remove(key(videoId)).apply()
    }

    fun resolveStartMs(videoId: String, durationMs: Long, percentWatched: Int): Long {
        val local = getPositionMs(videoId)
        val fromAccount = percentToMs(percentWatched, durationMs)
        val candidate = maxOf(local, fromAccount)
        if (candidate < MIN_RESUME_MS) return 0L
        if (durationMs <= 0L) return candidate
        val maxStart = (durationMs - END_BUFFER_MS).coerceAtLeast(0L)
        if (candidate >= maxStart) return 0L
        return candidate.coerceAtMost(maxStart)
    }

    private fun percentToMs(percentWatched: Int, durationMs: Long): Long {
        if (percentWatched !in 1..94 || durationMs <= 0L) return 0L
        return durationMs * percentWatched / 100L
    }

    private fun key(videoId: String) = "pos_$videoId"

    companion object {
        private const val PREFS_NAME = "phone_playback_positions"
        private const val MIN_SAVE_MS = 3_000L
        private const val MIN_RESUME_MS = 3_000L
        private const val END_BUFFER_MS = 5_000L
        private const val NEAR_END_FRACTION = 0.95

        @Volatile
        private var instance: PlaybackPositionStore? = null

        fun get(context: Context): PlaybackPositionStore {
            return instance ?: synchronized(this) {
                instance ?: PlaybackPositionStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
