package app.phonetube.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.phonetube.core.playback.SeekSegment

private val YouTubeProgressRed = Color(0xFFFF0000)
private val SponsorBlockGreen = Color(0xFF4CAF50)

@Composable
fun SponsorBlockProgressSlider(
    value: Float,
    durationMs: Long,
    sponsorSegments: List<SeekSegment>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val trackHeight = size.height
            drawRect(
                color = Color.White.copy(alpha = 0.35f),
                size = Size(size.width, trackHeight)
            )
            if (durationMs > 0L) {
                sponsorSegments.forEach { segment ->
                    val startFrac = (segment.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    val endFrac = (segment.endMs.toFloat() / durationMs).coerceIn(startFrac, 1f)
                    val width = size.width * (endFrac - startFrac)
                    if (width > 0f) {
                        drawRect(
                            color = SponsorBlockGreen,
                            topLeft = Offset(size.width * startFrac, 0f),
                            size = Size(width, trackHeight)
                        )
                    }
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = YouTubeProgressRed,
                activeTrackColor = YouTubeProgressRed,
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}
