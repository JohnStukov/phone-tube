package app.phonetube.ui.home



import android.app.Application

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope

import app.phonetube.core.media.HomeFeedKind
import app.phonetube.core.media.MediaErrors
import app.phonetube.core.media.VideoItem
import app.phonetube.core.media.VideoItemMapper
import app.phonetube.core.media.YouTubeRepository

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch



data class HomeUiState(

    val videos: List<VideoItem> = emptyList(),

    val isLoading: Boolean = false,

    val isRefreshing: Boolean = false,

    val isLoadingMore: Boolean = false,

    val canLoadMore: Boolean = false,

    val error: String? = null,

    val feed: HomeFeed = HomeFeed.ALL

)



enum class HomeFeed {
    ALL,
    TRENDING,
    NEWS,
    MUSIC,
    GAMING,
    SPORTS,
    LIVE,
    MOVIES
}



class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = YouTubeRepository(application)

    private val _state = MutableStateFlow(HomeUiState(isLoading = true))

    val state: StateFlow<HomeUiState> = _state.asStateFlow()



    private var nextPageKey: String? = null

    private var groupType: Int = -1



    init {

        load(HomeFeed.ALL)

    }



    fun load(feed: HomeFeed) {

        viewModelScope.launch {

            nextPageKey = null

            groupType = -1

            _state.value = _state.value.copy(

                isLoading = true,

                isLoadingMore = false,

                canLoadMore = false,

                error = null,

                feed = feed

            )

            fetchFirstPage(feed, showLoading = true)

        }

    }



    fun refresh() {

        if (_state.value.isRefreshing) return

        val feed = _state.value.feed

        viewModelScope.launch {

            nextPageKey = null

            groupType = -1

            _state.value = _state.value.copy(

                isRefreshing = true,

                isLoadingMore = false,

                canLoadMore = false,

                error = null

            )

            fetchFirstPage(feed, showLoading = false)

        }

    }



    fun loadMore() {

        val key = nextPageKey ?: return

        if (groupType < 0) return

        val current = _state.value

        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.canLoadMore) {

            return

        }

        viewModelScope.launch {

            _state.value = current.copy(isLoadingMore = true, error = null)

            try {

                val page = repository.loadHomeFeedMore(key, groupType)

                nextPageKey = page.nextPageKey

                groupType = page.groupType

                val merged = VideoItemMapper.merge(current.videos, page.videos)

                _state.value = current.copy(

                    videos = merged,

                    isLoadingMore = false,

                    canLoadMore = page.canLoadMore

                )

            } catch (e: Exception) {

                _state.value = current.copy(

                    isLoadingMore = false,

                    error = MediaErrors.codeFor(e)
                )

            }

        }

    }



    private suspend fun fetchFirstPage(feed: HomeFeed, showLoading: Boolean) {

        try {

            val page = repository.loadHomeFeedPage(feed.toKind())

            nextPageKey = page.nextPageKey

            groupType = page.groupType

            _state.value = HomeUiState(

                videos = page.videos,

                isLoading = false,

                isRefreshing = false,

                isLoadingMore = false,

                canLoadMore = page.canLoadMore,

                feed = feed

            )

        } catch (e: Exception) {

            _state.value = _state.value.copy(

                isLoading = if (showLoading) false else _state.value.isLoading,

                isRefreshing = false,

                isLoadingMore = false,

                error = MediaErrors.codeFor(e)
            )

        }

    }



    private fun mergeVideos(existing: List<VideoItem>, more: List<VideoItem>): List<VideoItem> {

        if (more.isEmpty()) return existing

        val seen = existing.map { it.videoId }.toMutableSet()

        val result = ArrayList<VideoItem>(existing.size + more.size)

        result.addAll(existing)

        for (video in more) {

            if (seen.add(video.videoId)) {

                result.add(video)

            }

        }

        return result

    }



    private fun HomeFeed.toKind(): HomeFeedKind = when (this) {
        HomeFeed.ALL -> HomeFeedKind.ALL
        HomeFeed.TRENDING -> HomeFeedKind.TRENDING
        HomeFeed.NEWS -> HomeFeedKind.NEWS
        HomeFeed.MUSIC -> HomeFeedKind.MUSIC
        HomeFeed.GAMING -> HomeFeedKind.GAMING
        HomeFeed.SPORTS -> HomeFeedKind.SPORTS
        HomeFeed.LIVE -> HomeFeedKind.LIVE
        HomeFeed.MOVIES -> HomeFeedKind.MOVIES
    }

}

