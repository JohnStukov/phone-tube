package app.phonetube.core.media

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.youtubeapi.browse.v2.BrowseService2

internal class ChannelPageLoader(
    private val browseService2: BrowseService2 = BrowseService2()
) {
    private var loadedChannelId: String? = null
    private var sortGroups: List<MediaGroup?> = emptyList()
    private var tabGroupsById: Map<String, MediaGroup?> = emptyMap()
    private val sortReloadKeyById = mutableMapOf<String, String>()

    fun loadHeader(channelId: String): BrowseService2.ChannelHeaderMetadata? =
        browseService2.getChannelHeaderMetadata(channelId)

    fun ensureStructure(channelId: String) {
        val id = channelId.trim()
        if (loadedChannelId == id) return
        loadedChannelId = id
        sortGroups = browseService2.getChannelSortOptions(id).orEmpty()
        tabGroupsById = buildTabMap(browseService2.getChannelTabGroups(id).orEmpty())
        sortReloadKeyById.clear()
        sortGroups.forEachIndexed { index, group ->
            val key = group?.nextPageKey?.trim().orEmpty()
            val label = group?.title?.trim().orEmpty()
            if (key.isNotBlank() && label.isNotBlank()) {
                sortReloadKeyById["sort_$index"] = key
            }
        }
    }

    fun tabs(): List<ChannelTab> = TAB_ORDER.mapNotNull { id ->
        tabGroupsById[id]?.let { ChannelTab(id = id, title = id) }
    }

    fun sortOptions(): List<ChannelSortOption> =
        sortReloadKeyById.mapNotNull { (id, _) ->
            val index = id.removePrefix("sort_").toIntOrNull() ?: return@mapNotNull null
            val label = sortGroups.getOrNull(index)?.title?.trim().orEmpty()
            if (label.isBlank()) null else ChannelSortOption(id = id, label = label)
        }

    fun loadVideos(channelId: String, tabId: String, sortId: String?): List<VideoItem> {
        ensureStructure(channelId)
        val mediaGroup = when (tabId) {
            ChannelTabIds.VIDEOS -> loadVideosTab(channelId, sortId)
            else -> browseService2.getChannelTabVideos(tabGroupsById[tabId])
        }
        return VideoItemMapper.fromGroups(mediaGroup?.let { listOf(it) })
    }

    private fun loadVideosTab(channelId: String, sortId: String?): MediaGroup? {
        val sortGroup = sortId
            ?.removePrefix("sort_")
            ?.toIntOrNull()
            ?.let { sortGroups.getOrNull(it) }
        return browseService2.getChannelVideosForSort(channelId, sortGroup)
    }

    private fun buildTabMap(groups: List<MediaGroup?>): Map<String, MediaGroup?> {
        val result = LinkedHashMap<String, MediaGroup?>()
        for (group in groups) {
            val kind = classifyTab(group?.title) ?: continue
            if (!result.containsKey(kind)) {
                result[kind] = group
            }
        }
        if (!result.containsKey(ChannelTabIds.VIDEOS)) {
            groups.firstOrNull { it?.title?.contains("video", ignoreCase = true) == true }?.let {
                result[ChannelTabIds.VIDEOS] = it
            }
        }
        return result
    }

    private fun classifyTab(title: String?): String? {
        val raw = title?.trim().orEmpty()
        if (raw.isBlank() || raw.length > 32) return null
        val lower = raw.lowercase()
        return when {
            lower == "videos" || lower == "vídeos" -> ChannelTabIds.VIDEOS
            lower == "shorts" -> ChannelTabIds.SHORTS
            lower.contains("playlist") -> ChannelTabIds.PLAYLISTS
            lower == "live" || lower.contains("en directo") || lower == "directos" -> ChannelTabIds.LIVE
            lower == "home" || lower == "inicio" -> ChannelTabIds.HOME
            else -> null
        }
    }

    companion object {
        private val TAB_ORDER = listOf(
            ChannelTabIds.VIDEOS,
            ChannelTabIds.SHORTS,
            ChannelTabIds.PLAYLISTS,
            ChannelTabIds.LIVE,
            ChannelTabIds.HOME
        )
    }
}
