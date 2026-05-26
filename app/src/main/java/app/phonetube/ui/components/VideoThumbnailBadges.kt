package app.phonetube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.phonetube.R
import app.phonetube.ui.theme.YouTubeBadgeBackground
import app.phonetube.ui.theme.YouTubeLiveRed

@Composable
fun VideoDurationBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(YouTubeBadgeBackground)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White
    )
}

@Composable
fun VideoLiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.live_badge),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(YouTubeLiveRed)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White
    )
}
