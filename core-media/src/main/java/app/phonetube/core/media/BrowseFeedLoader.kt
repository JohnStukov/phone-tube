package app.phonetube.core.media

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.youtubeapi.browse.v2.BrowseService2

internal class BrowseFeedLoader(context: Context) {
    private val appContext = context.applicationContext
    private val browse = BrowseService2()

    fun loadFirstPage(feed: HomeFeedKind): FeedPage {
        PhoneTubeMediaInit.init(appContext)
        return when (feed) {
            HomeFeedKind.ALL -> pageFromHome(browse.getHome())
            HomeFeedKind.TRENDING -> loadTrending()
            HomeFeedKind.MUSIC -> pageFromHome(browse.getMusic())
            HomeFeedKind.GAMING -> pageFromHome(browse.getGaming())
            HomeFeedKind.LIVE -> pageFromHome(browse.getLive())
        }
    }

    fun loadContinuation(nextPageKey: String, groupType: Int): FeedPage {
        PhoneTubeMediaInit.init(appContext)
        val pair = browse.continueSectionList(nextPageKey, groupType) ?: return FeedPage.EMPTY
        return pageFromGroups(pair.first, pair.second, groupType)
    }

    private fun loadTrending(): FeedPage {
        val tv = browse.getTrendingTv()
        if (tv != null && !tv.first.isNullOrEmpty()) {
            val expanded = expandChipSections(tv.first)
            val videos = VideoItemMapper.fromGroups(expanded)
            if (videos.isNotEmpty()) {
                return FeedPage(
                    videos = videos,
                    nextPageKey = tv.second,
                    groupType = MediaGroup.TYPE_TRENDING
                )
            }
        }
        val expanded = expandChipSections(browse.getTrending())
        return pageFromGroups(expanded, null, MediaGroup.TYPE_TRENDING)
    }

    private fun pageFromHome(pair: Pair<List<MediaGroup?>?, String?>?): FeedPage {
        if (pair == null) return FeedPage.EMPTY
        val expanded = expandChipSections(pair.first)
        return pageFromGroups(expanded, pair.second, MediaGroup.TYPE_HOME)
    }

    private fun expandChipSections(groups: List<MediaGroup?>?): List<MediaGroup> {
        val result = ArrayList<MediaGroup>()
        for (group in groups.orEmpty()) {
            if (group == null) continue
            if (group.isEmpty) {
                browse.continueEmptyGroup(group)?.forEach { section ->
                    if (section != null) result.add(section)
                }
            } else {
                result.add(group)
            }
        }
        return result
    }

    private fun pageFromGroups(
        groups: List<MediaGroup?>?,
        nextPageKey: String?,
        groupType: Int
    ): FeedPage {
        val items = VideoItemMapper.fromGroups(groups)
        return FeedPage(
            videos = items,
            nextPageKey = nextPageKey,
            groupType = groupType
        )
    }
}

enum class HomeFeedKind {
    ALL,
    TRENDING,
    MUSIC,
    GAMING,
    LIVE;

    companion object {
        fun from(feed: String): HomeFeedKind = when (feed) {
            "TRENDING" -> TRENDING
            "MUSIC" -> MUSIC
            "GAMING" -> GAMING
            "LIVE" -> LIVE
            else -> ALL
        }
    }
}
