package app.phonetube.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.phonetube.ui.theme.ContrastColors
import app.phonetube.ui.theme.feedChipStyle

@Composable
fun YouTubeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val style = feedChipStyle(selected, darkTheme)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = style.containerColor,
        contentColor = style.labelColor
    ) {
        CompositionLocalProvider(LocalContentColor provides style.labelColor) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun YouTubeSubscribePill(
    subscribed: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false
) {
    val bg = if (subscribed) ContrastColors.subscribedBg else ContrastColors.subscribeBg
    val fg = if (subscribed) ContrastColors.subscribedText else ContrastColors.subscribeText
    Surface(
        onClick = onClick,
        modifier = if (fullWidth) modifier.fillMaxWidth() else modifier,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        shadowElevation = 0.dp
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            textAlign = if (fullWidth) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun YouTubeActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val bg = if (selected) ContrastColors.actionSelectedBg else ContrastColors.actionBg
    val fg = if (selected) ContrastColors.actionSelectedText else ContrastColors.actionText
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
