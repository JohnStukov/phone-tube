package app.phonetube.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders
import com.liskovsoft.sharedutils.okhttp.OkHttpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response

object ThumbnailLoader {
    private const val REFERER = "https://www.youtube.com/"
    private val memoryCache = LruCache<String, Bitmap>(80)

    private val imageHeaders = mapOf(
        "User-Agent" to DefaultHeaders.USER_AGENT_WEB,
        "Referer" to REFERER,
        "Accept" to "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
        "Accept-Encoding" to "identity"
    )

    suspend fun load(
        url: String?,
        videoId: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        for (candidate in buildCandidates(url, videoId)) {
            memoryCache.get(candidate)?.let { return@withContext it }
            val bitmap = downloadBitmap(candidate) ?: continue
            memoryCache.put(candidate, bitmap)
            return@withContext bitmap
        }
        null
    }

    private fun buildCandidates(url: String?, videoId: String?): List<String> {
        val list = linkedSetOf<String>()
        ImageUrlHelper.normalize(url)?.let { list.add(it) }
        if (!videoId.isNullOrBlank()) {
            ImageUrlHelper.videoThumbnail(videoId, "hqdefault")?.let { list.add(it) }
            ImageUrlHelper.videoThumbnail(videoId, "mqdefault")?.let { list.add(it) }
            ImageUrlHelper.videoThumbnail(videoId, "sddefault")?.let { list.add(it) }
        }
        return list.toList()
    }

    private fun downloadBitmap(url: String): Bitmap? {
        var response: Response? = null
        return try {
            response = OkHttpManager.instance().doGetRequest(url, imageHeaders)
            if (response == null || !response.isSuccessful) return null
            val body = response.body() ?: return null
            val bytes = body.bytes()
            if (bytes.isEmpty()) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            response?.close()
        }
    }
}
