package app.phonetube.ui.images

import android.content.Context
import coil.ImageLoader
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders
import okhttp3.OkHttpClient

object PhoneTubeImageLoader {
    private const val REFERER = "https://www.youtube.com/"

    fun create(context: Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", DefaultHeaders.USER_AGENT_WEB)
                        .header("Referer", REFERER)
                        .header("Accept", "image/*,*/*;q=0.8")
                        .header("Accept-Encoding", "identity")
                        .build()
                )
            }
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
