package app.phonetube.core.media

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.ContentService
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.youtubeapi.browse.v2.BrowseService2
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager

internal class MediaGroupPagedLoader(
    context: Context,
    private val kind: MediaGroupFeedKind
) {
    private val appContext = context.applicationContext
    private val contentService: ContentService =
        YouTubeServiceManager.instance().contentService
    private val browse = BrowseService2()
    private var anchor: MediaGroup? = null

    fun reset() {
        anchor = null
    }

    fun loadFirstPage(): FeedPage {
        PhoneTubeMediaInit.init(appContext)
        reset()
        return when (kind) {
            MediaGroupFeedKind.SUBSCRIPTIONS -> loadSubscriptionsFirst()
            MediaGroupFeedKind.SHORTS -> loadShortsFirst()
        }
    }

    fun loadMore(): FeedPage {
        PhoneTubeMediaInit.init(appContext)
        val current = anchor ?: return FeedPage.EMPTY
        if (current.nextPageKey.isNullOrBlank()) {
            return FeedPage.EMPTY.copy(groupType = current.type)
        }
        val next = contentService.continueGroup(current) ?: return FeedPage.EMPTY
        anchor = next
        return pageFromGroup(next)
    }

    private fun loadSubscriptionsFirst(): FeedPage {
        val group = contentService.subscriptions ?: return FeedPage.EMPTY
        anchor = group
        return pageFromGroup(group)
    }

    private fun loadShortsFirst(): FeedPage {
        val primary = browse.getShorts()
        if (primary != null && !primary.nextPageKey.isNullOrBlank()) {
            anchor = primary
            return pageFromGroup(primary)
        }
        val merged = ArrayList<VideoItem>()
        primary?.let { merged.addAll(videosFromGroup(it)) }
        val fallback = browse.getShorts2()
        if (fallback != null) {
            merged.addAll(0, videosFromGroup(fallback))
            anchor = fallback.takeIf { !it.nextPageKey.isNullOrBlank() } ?: primary
        } else {
            anchor = primary
        }
        val anchorGroup = anchor
        return FeedPage(
            videos = VideoItemMapper.dedupe(merged),
            nextPageKey = anchorGroup?.nextPageKey,
            groupType = MediaGroup.TYPE_SHORTS
        )
    }

    private fun pageFromGroup(group: MediaGroup): FeedPage =
        FeedPage(
            videos = videosFromGroup(group),
            nextPageKey = group.nextPageKey,
            groupType = group.type
        )

    private fun videosFromGroup(group: MediaGroup): List<VideoItem> =
        VideoItemMapper.fromGroups(listOf(group))
}

internal enum class MediaGroupFeedKind {
    SUBSCRIPTIONS,
    SHORTS
}
