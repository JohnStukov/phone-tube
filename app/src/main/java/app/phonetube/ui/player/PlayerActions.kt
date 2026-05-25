package app.phonetube.ui.player

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.phonetube.R
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun CollectPlayerActions(actionEvents: SharedFlow<PlayerActionEvent>) {
    val context = LocalContext.current
    LaunchedEffect(actionEvents) {
        actionEvents.collect { event ->
            when (event) {
                is PlayerActionEvent.Share -> {
                    val intent = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, event.url)
                        },
                        context.getString(R.string.share)
                    )
                    context.startActivity(intent)
                }
                is PlayerActionEvent.Download -> {
                    enqueueDownload(context, event.url, event.title)
                    Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private fun enqueueDownload(context: Context, url: String, title: String) {
    val safeName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
    val fileName = if (safeName.endsWith(".mp4")) safeName else "$safeName.mp4"
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setDescription(context.getString(R.string.app_name))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

fun resolveActionMessage(context: Context, message: String?): String? {
    if (message.isNullOrBlank()) return null
    return when (message) {
        "sign_in_required_action" -> context.getString(R.string.sign_in_required_action)
        "download_unavailable" -> context.getString(R.string.download_unavailable)
        "comment_posted" -> context.getString(R.string.comment_posted)
        "comment_post_failed" -> context.getString(R.string.comment_post_failed)
        "comment_empty" -> context.getString(R.string.comment_empty)
        "comments_post_unavailable" -> context.getString(R.string.comments_post_unavailable)
        "comments_not_ready" -> context.getString(R.string.comments_not_ready)
        else -> message
    }
}
