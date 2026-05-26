package app.phonetube.core.media

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.NotificationsService
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager

internal class NotificationsPagedLoader(context: Context) {
    private val appContext = context.applicationContext
    private val notificationsService: NotificationsService
        get() = YouTubeServiceManager.instance().notificationsService
    private var anchor: MediaGroup? = null

    fun reset() {
        anchor = null
    }

    fun loadFirstPage(): FeedPage {
        PhoneTubeMediaInit.init(appContext)
        reset()
        val group = notificationsService.notificationItems ?: return FeedPage.EMPTY
        anchor = group
        return pageFromGroup(group)
    }

    fun loadMore(): FeedPage {
        PhoneTubeMediaInit.init(appContext)
        val current = anchor ?: return FeedPage.EMPTY
        val key = current.nextPageKey?.trim().orEmpty()
        if (key.isBlank()) {
            return FeedPage.EMPTY.copy(groupType = current.type)
        }
        val next = notificationsService.getNotificationItemsContinuation(key) ?: return FeedPage.EMPTY
        anchor = next
        return pageFromGroup(next)
    }

    private fun pageFromGroup(group: MediaGroup): FeedPage =
        FeedPage(
            videos = VideoItemMapper.fromGroups(listOf(group)),
            nextPageKey = group.nextPageKey,
            groupType = group.type
        )
}
