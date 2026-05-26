package app.phonetube.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.phonetube.R
import app.phonetube.ui.home.HomeFeed

@Composable
fun YouTubeChipRow(
    selected: HomeFeed,
    onSelect: (HomeFeed) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = listOf(
        HomeFeed.ALL to R.string.feed_home,
        HomeFeed.TRENDING to R.string.feed_trending,
        HomeFeed.NEWS to R.string.chip_news,
        HomeFeed.MUSIC to R.string.chip_music,
        HomeFeed.GAMING to R.string.chip_gaming,
        HomeFeed.SPORTS to R.string.chip_sports,
        HomeFeed.LIVE to R.string.chip_live,
        HomeFeed.MOVIES to R.string.chip_movies
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (feed, labelRes) ->
            YouTubeFilterChip(
                label = stringResource(labelRes),
                selected = selected == feed,
                onClick = { onSelect(feed) }
            )
        }
    }
}
