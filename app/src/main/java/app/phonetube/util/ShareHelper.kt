package app.phonetube.util

import android.content.Context
import android.content.Intent
import app.phonetube.R

object ShareHelper {
    fun shareVideo(context: Context, videoId: String) {
        val url = "https://www.youtube.com/shorts/$videoId"
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            },
            context.getString(R.string.share)
        )
        context.startActivity(intent)
    }
}
