package app.phonetube.core.media

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.CommentsService
import com.liskovsoft.mediaserviceinterfaces.ContentService
import com.liskovsoft.mediaserviceinterfaces.MediaItemService
import com.liskovsoft.mediaserviceinterfaces.NotificationsService
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItem
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val feedLoader = BrowseFeedLoader(appContext)
    private val subscriptionsPager = MediaGroupPagedLoader(appContext, MediaGroupFeedKind.SUBSCRIPTIONS)
    private val shortsPager = MediaGroupPagedLoader(appContext, MediaGroupFeedKind.SHORTS)

    private val contentService: ContentService
        get() = YouTubeServiceManager.instance().contentService

    private val mediaItemService: MediaItemService
        get() = YouTubeServiceManager.instance().mediaItemService

    private val commentsService: CommentsService
        get() = YouTubeServiceManager.instance().commentsService

    private val notificationsService: NotificationsService
        get() = YouTubeServiceManager.instance().notificationsService

    suspend fun searchVideos(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        PhoneTubeMediaInit.init(appContext)
        val groups = contentService.getSearchObserve(query).blockingFirst()
        flattenGroups(groups)
    }

    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        PhoneTubeMediaInit.init(appContext)
        contentService.getSearchTagsObserve(query).blockingFirst() ?: emptyList()
    }

    suspend fun loadNotifications(): List<VideoItem> = withContext(Dispatchers.IO) {
        requireSignedIn()
        PhoneTubeMediaInit.init(appContext)
        val group = notificationsService.getNotificationItemsObserve().blockingFirst()
        flattenGroup(group)
    }

    suspend fun loadHomeFeedPage(kind: HomeFeedKind): FeedPage = withContext(Dispatchers.IO) {
        feedLoader.loadFirstPage(kind)
    }

    suspend fun loadHomeFeedMore(nextPageKey: String, groupType: Int): FeedPage =
        withContext(Dispatchers.IO) {
            feedLoader.loadContinuation(nextPageKey, groupType)
        }

    suspend fun loadHomeVideos(): List<VideoItem> =
        loadHomeFeedPage(HomeFeedKind.ALL).videos

    suspend fun loadTrendingVideos(): List<VideoItem> =
        loadHomeFeedPage(HomeFeedKind.TRENDING).videos

    suspend fun loadMusicVideos(): List<VideoItem> =
        loadHomeFeedPage(HomeFeedKind.MUSIC).videos

    suspend fun loadGamingVideos(): List<VideoItem> =
        loadHomeFeedPage(HomeFeedKind.GAMING).videos

    suspend fun loadLiveVideos(): List<VideoItem> =
        loadHomeFeedPage(HomeFeedKind.LIVE).videos

    suspend fun loadSubscriptionsFeed(): FeedPage = withContext(Dispatchers.IO) {
        requireSignedIn()
        subscriptionsPager.loadFirstPage()
    }

    suspend fun loadSubscriptionsFeedMore(): FeedPage = withContext(Dispatchers.IO) {
        requireSignedIn()
        subscriptionsPager.loadMore()
    }

    suspend fun loadShortsFeed(): FeedPage = withContext(Dispatchers.IO) {
        shortsPager.loadFirstPage()
    }

    suspend fun loadShortsFeedMore(): FeedPage = withContext(Dispatchers.IO) {
        shortsPager.loadMore()
    }

    suspend fun loadSubscriptionVideos(): List<VideoItem> =
        loadSubscriptionsFeed().videos

    suspend fun loadShortsVideos(): List<VideoItem> =
        loadShortsFeed().videos

    suspend fun loadHistoryVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!AuthRepository.get(appContext).isSignedIn()) {
            throw NotSignedInException()
        }
        PhoneTubeMediaInit.init(appContext)
        val group = contentService.getHistoryObserve().blockingFirst()
        flattenGroup(group)
    }

    suspend fun getFormatInfo(videoId: String): MediaItemFormatInfo = withContext(Dispatchers.IO) {
        PhoneTubeMediaInit.init(appContext)
        mediaItemService.getFormatInfoObserve(videoId).blockingFirst()
    }

    suspend fun getQualityOptions(videoId: String): List<StreamQualityOption> = withContext(Dispatchers.IO) {
        val formatInfo = getFormatInfo(videoId)
        QualityOptionsHelper.qualityOptions(formatInfo)
    }

    suspend fun getSubtitleOptions(videoId: String): List<SubtitleOption> = withContext(Dispatchers.IO) {
        val formatInfo = getFormatInfo(videoId)
        SubtitleOptionsHelper.subtitleOptions(formatInfo)
    }

    suspend fun getVideoMetadata(videoId: String): VideoMetadata = withContext(Dispatchers.IO) {
        PhoneTubeMediaInit.init(appContext)
        val meta = mediaItemService.getMetadataObserve(videoId).blockingFirst()
        enrichChannelId(mapMetadata(meta, videoId), videoId)
    }

    suspend fun resolveChannelIdForVideo(videoId: String): String? = withContext(Dispatchers.IO) {
        PhoneTubeMediaInit.init(appContext)
        val meta = mediaItemService.getMetadataObserve(videoId).blockingFirst()
        val mapped = enrichChannelId(mapMetadata(meta, videoId), videoId)
        mapped.channelId?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun enrichChannelId(metadata: VideoMetadata, videoId: String): VideoMetadata {
        val existing = metadata.channelId?.trim()?.takeIf { it.isNotEmpty() }
        return try {
            val format = getFormatInfo(videoId)
            val channelId = existing ?: format.channelId?.trim()?.takeIf { it.isNotEmpty() }
            val isLive = metadata.isLive || format.isLive || format.isLiveContent
            metadata.copy(
                channelId = channelId,
                isLive = isLive
            )
        } catch (_: Exception) {
            if (existing != null) metadata.copy(channelId = existing) else metadata
        }
    }

    suspend fun loadChannel(channelId: String, fallbackName: String? = null): ChannelDetails =
        withContext(Dispatchers.IO) {
            PhoneTubeMediaInit.init(appContext)
            val canonicalId = channelId.trim()
            val groups = loadChannelGroups(canonicalId)
            val videos = flattenGroups(groups)
            val headerItem = groups.firstOrNull()?.mediaItems?.firstOrNull()
            val name = fallbackName?.takeIf { it.isNotBlank() }
                ?: headerItem?.author?.takeIf { it.isNotBlank() }
                ?: groups.firstOrNull()?.title?.takeIf { it.isNotBlank() }
                ?: canonicalId
            ChannelDetails(
                channelId = canonicalId,
                name = name,
                avatarUrl = ImageUrlHelper.normalize(
                    headerItem?.cardImageUrl ?: headerItem?.backgroundImageUrl
                ),
                subscriberCount = headerItem?.secondTitle?.toString(),
                description = null,
                videos = videos,
                isSubscribed = resolveIsSubscribed(canonicalId)
            )
        }

    suspend fun setLike(videoId: String) = withContext(Dispatchers.IO) {
        requireSignedIn()
        PhoneTubeMediaInit.init(appContext)
        mediaItemService.setLike(videoItem(videoId))
    }

    suspend fun removeLike(videoId: String) = withContext(Dispatchers.IO) {
        requireSignedIn()
        PhoneTubeMediaInit.init(appContext)
        mediaItemService.removeLike(videoItem(videoId))
    }

    suspend fun setDislike(videoId: String) = withContext(Dispatchers.IO) {
        requireSignedIn()
        PhoneTubeMediaInit.init(appContext)
        mediaItemService.setDislike(videoItem(videoId))
    }

    suspend fun removeDislike(videoId: String) = withContext(Dispatchers.IO) {
        requireSignedIn()
        PhoneTubeMediaInit.init(appContext)
        mediaItemService.removeDislike(videoItem(videoId))
    }

    suspend fun subscribe(channelId: String) = withContext(Dispatchers.IO) {
        requireSignedIn()
        PhoneTubeMediaInit.init(appContext)
        mediaItemService.subscribe(channelId)
    }

    suspend fun unsubscribe(channelId: String) = withContext(Dispatchers.IO) {
        requireSignedIn()
        PhoneTubeMediaInit.init(appContext)
        mediaItemService.unsubscribe(channelId)
    }

    suspend fun getDownloadUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        PhoneTubeMediaInit.init(appContext)
        val info = getFormatInfo(videoId)
        val urls = info.createUrlList()
        urls?.firstOrNull { it.startsWith("http") }
    }

    fun shareUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    suspend fun loadComments(commentsKey: String): List<VideoComment> = withContext(Dispatchers.IO) {
        if (commentsKey.isBlank()) return@withContext emptyList()
        PhoneTubeMediaInit.init(appContext)
        val group = commentsService.getCommentsObserve(commentsKey).blockingFirst() ?: return@withContext emptyList()
        group.comments
            ?.mapNotNull { mapComment(it) }
            .orEmpty()
    }

    suspend fun canPostComment(commentsKey: String): Boolean = withContext(Dispatchers.IO) {
        if (commentsKey.isBlank() || !AuthRepository.get(appContext).isSignedIn()) return@withContext false
        PhoneTubeMediaInit.init(appContext)
        val group = commentsService.getCommentsObserve(commentsKey).blockingFirst() ?: return@withContext false
        !group.createCommentParams.isNullOrBlank()
    }

    suspend fun postComment(commentsKey: String, text: String) = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw CommentPostException("comment_empty")
        }
        requireSignedIn()
        PhoneTubeMediaInit.init(appContext)
        val group = commentsService.getCommentsObserve(commentsKey).blockingFirst()
            ?: throw CommentPostException("comments_not_ready")
        val params = group.createCommentParams
        if (params.isNullOrBlank()) {
            throw CommentPostException("comments_post_unavailable")
        }
        commentsService.createCommentObserve(params, trimmed).blockingFirst()
    }

    private fun mapMetadata(meta: MediaItemMetadata, videoId: String): VideoMetadata {
        val related = flattenGroups(meta.suggestions)
        return VideoMetadata(
            videoId = meta.videoId?.takeIf { it.isNotBlank() } ?: videoId,
            title = meta.title.orEmpty(),
            channelId = meta.channelId,
            author = meta.author,
            viewCount = meta.viewCount,
            subscriberCount = meta.subscriberCount,
            authorImageUrl = ImageUrlHelper.normalize(meta.authorImageUrl),
            publishedDate = meta.publishedDate,
            likeCount = meta.likeCount,
            description = meta.description,
            commentsKey = meta.commentsKey,
            isSubscribed = resolveIsSubscribed(meta.channelId),
            isLive = meta.isLive,
            likeStatus = meta.likeStatus,
            relatedVideos = related
        )
    }

    private fun videoItem(videoId: String): MediaItem {
        val item = YouTubeMediaItem()
        item.setVideoId(videoId)
        return item
    }

    private fun requireSignedIn() {
        if (!AuthRepository.get(appContext).isSignedIn()) {
            throw NotSignedInException()
        }
    }

    private fun loadChannelGroups(channelId: String): List<MediaGroup> {
        return try {
            if (!AuthRepository.get(appContext).isSignedIn()) {
                return emptyList()
            }
            contentService.getChannelObserve(channelId).blockingFirst()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveIsSubscribed(channelId: String?): Boolean {
        if (channelId.isNullOrBlank()) return false
        if (!AuthRepository.get(appContext).isSignedIn()) return false
        return isChannelInSubscriptions(channelId)
    }

    private fun isChannelInSubscriptions(channelId: String): Boolean {
        return try {
            val group = contentService.getSubscriptionsObserve().blockingFirst() ?: return false
            group.mediaItems?.any { item ->
                channelIdsMatch(item.channelId, channelId)
            } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun channelIdsMatch(a: String?, b: String): Boolean {
        if (a.isNullOrBlank()) return false
        return a == b || a.endsWith(b) || b.endsWith(a)
    }

    private fun mapComment(item: CommentItem): VideoComment? {
        if (item.isEmpty) return null
        val message = item.message?.trim().orEmpty()
        if (message.isEmpty()) return null
        return VideoComment(
            id = item.id,
            message = message,
            authorName = item.authorName,
            authorPhotoUrl = ImageUrlHelper.normalize(item.authorPhoto),
            publishedDate = item.publishedDate,
            likeCount = item.likeCount
        )
    }

    suspend fun getSponsorSegments(videoId: String, categories: Set<String>): List<SponsorSegment> =
        withContext(Dispatchers.IO) {
            PhoneTubeMediaInit.init(appContext)
            if (categories.isEmpty()) {
                emptyList()
            } else {
                mediaItemService.getSponsorSegmentsObserve(videoId, categories).blockingFirst()
            }
        }

    fun getSponsorSegmentsObserve(videoId: String, categories: Set<String>): Observable<List<SponsorSegment>> {
        PhoneTubeMediaInit.init(appContext)
        return mediaItemService.getSponsorSegmentsObserve(videoId, categories)
    }

    private fun flattenGroup(group: MediaGroup?): List<VideoItem> =
        VideoItemMapper.fromGroups(group?.let { listOf(it) })

    private fun flattenGroups(groups: List<MediaGroup>?): List<VideoItem> =
        VideoItemMapper.fromGroups(groups)
}
