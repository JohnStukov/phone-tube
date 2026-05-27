package app.phonetube.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.phonetube.R
import app.phonetube.core.media.AudioLanguageOptionsHelper
import app.phonetube.core.media.AudioTrackOption
import app.phonetube.core.media.SubtitleOption
import app.phonetube.core.media.SubtitleOptionsHelper
import app.phonetube.core.media.StreamQualityOption
import app.phonetube.core.playback.CaptionSize
import app.phonetube.core.playback.PlaybackSpeedHelper

private enum class SettingsPage {
    Main, Quality, Audio, Subtitles, Speed, CaptionSize
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    qualityOptions: List<StreamQualityOption>,
    selectedQualityLabel: String,
    audioTrackOptions: List<AudioTrackOption>,
    selectedAudioTrackId: String?,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleId: String,
    playbackSpeed: Float,
    captionSize: CaptionSize,
    autoplayEnabled: Boolean,
    onQualitySelected: (StreamQualityOption) -> Unit,
    onAudioTrackSelected: (AudioTrackOption) -> Unit,
    onSubtitleSelected: (SubtitleOption) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onCaptionSizeSelected: (CaptionSize) -> Unit,
    onAutoplayChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.Main) }
    val selectedSubtitleLabel = subtitleOptions
        .firstOrNull { it.id == selectedSubtitleId }
        ?.let { localizeSubtitleLabel(it) }
        ?: stringResource(R.string.subtitles_off)
    val selectedAudioLabel = audioTrackOptions
        .firstOrNull { it.id == selectedAudioTrackId }
        ?.let { audioTrackLabel(it) }
        ?: stringResource(R.string.audio_track_auto)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (page) {
            SettingsPage.Main -> SettingsMainPage(
                selectedQualityLabel = selectedQualityLabel,
                selectedAudioLabel = selectedAudioLabel,
                selectedSubtitleLabel = selectedSubtitleLabel,
                playbackSpeedLabel = PlaybackSpeedHelper.formatSpeedLabel(playbackSpeed),
                captionSizeLabel = captionSizeLabel(captionSize),
                autoplayEnabled = autoplayEnabled,
                hasQuality = qualityOptions.size > 1,
                hasAudioTracks = audioTrackOptions.size > 1,
                hasSubtitles = subtitleOptions.size > 1,
                onNavigate = { page = it },
                onAutoplayChanged = onAutoplayChanged
            )
            SettingsPage.Quality -> SettingsSubPage(
                title = stringResource(R.string.quality),
                onBack = { page = SettingsPage.Main }
            ) {
                qualityOptions.forEach { option ->
                    SettingsRow(
                        label = option.label,
                        selected = option.label == selectedQualityLabel,
                        onClick = {
                            onQualitySelected(option)
                            page = SettingsPage.Main
                        }
                    )
                }
            }
            SettingsPage.Audio -> SettingsSubPage(
                title = stringResource(R.string.audio_track),
                onBack = { page = SettingsPage.Main }
            ) {
                if (audioTrackOptions.size <= 1) {
                    Text(
                        text = stringResource(R.string.audio_track_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                } else {
                    audioTrackOptions.forEach { option ->
                        SettingsRow(
                            label = audioTrackLabel(option),
                            selected = option.id == selectedAudioTrackId,
                            onClick = {
                                onAudioTrackSelected(option)
                                page = SettingsPage.Main
                            }
                        )
                    }
                }
            }
            SettingsPage.Subtitles -> SettingsSubPage(
                title = stringResource(R.string.subtitles),
                onBack = { page = SettingsPage.Main }
            ) {
                if (subtitleOptions.size <= 1) {
                    Text(
                        text = stringResource(R.string.subtitles_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                } else {
                    subtitleOptions.forEach { option ->
                        SettingsRow(
                            label = localizeSubtitleLabel(option),
                            selected = option.id == selectedSubtitleId,
                            onClick = {
                                onSubtitleSelected(option)
                                page = SettingsPage.Main
                            }
                        )
                    }
                }
            }
            SettingsPage.Speed -> SettingsSubPage(
                title = stringResource(R.string.playback_speed),
                onBack = { page = SettingsPage.Main }
            ) {
                PlaybackSpeedHelper.speedOptions().forEach { speed ->
                    SettingsRow(
                        label = PlaybackSpeedHelper.formatSpeedLabel(speed),
                        selected = speed == playbackSpeed,
                        onClick = {
                            onSpeedSelected(speed)
                            page = SettingsPage.Main
                        }
                    )
                }
            }
            SettingsPage.CaptionSize -> SettingsSubPage(
                title = stringResource(R.string.caption_size),
                onBack = { page = SettingsPage.Main }
            ) {
                CaptionSize.values().forEach { size ->
                    SettingsRow(
                        label = captionSizeLabel(size),
                        selected = size == captionSize,
                        onClick = {
                            onCaptionSizeSelected(size)
                            page = SettingsPage.Main
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMainPage(
    selectedQualityLabel: String,
    selectedAudioLabel: String,
    selectedSubtitleLabel: String,
    playbackSpeedLabel: String,
    captionSizeLabel: String,
    autoplayEnabled: Boolean,
    hasQuality: Boolean,
    hasAudioTracks: Boolean,
    hasSubtitles: Boolean,
    onNavigate: (SettingsPage) -> Unit,
    onAutoplayChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.player_settings),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (hasQuality) {
            SettingsNavRow(
                title = stringResource(R.string.quality),
                value = selectedQualityLabel,
                onClick = { onNavigate(SettingsPage.Quality) }
            )
        }
        if (hasAudioTracks) {
            SettingsNavRow(
                title = stringResource(R.string.audio_track),
                value = selectedAudioLabel,
                onClick = { onNavigate(SettingsPage.Audio) }
            )
        }
        if (hasSubtitles) {
            SettingsNavRow(
                title = stringResource(R.string.subtitles),
                value = selectedSubtitleLabel,
                onClick = { onNavigate(SettingsPage.Subtitles) }
            )
        }
        SettingsNavRow(
            title = stringResource(R.string.playback_speed),
            value = playbackSpeedLabel,
            onClick = { onNavigate(SettingsPage.Speed) }
        )
        SettingsNavRow(
            title = stringResource(R.string.caption_size),
            value = captionSizeLabel,
            onClick = { onNavigate(SettingsPage.CaptionSize) }
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.autoplay),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(checked = autoplayEnabled, onCheckedChange = onAutoplayChanged)
        }
    }
}

@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        content()
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun localizeSubtitleLabel(option: SubtitleOption): String {
    return if (option.id == SubtitleOptionsHelper.OFF_ID) {
        stringResource(R.string.subtitles_off)
    } else {
        option.label
    }
}

@Composable
private fun audioTrackLabel(option: AudioTrackOption): String {
    return if (option.id == AudioLanguageOptionsHelper.PREFERRED_ORIGINAL) {
        stringResource(R.string.audio_track_original)
    } else {
        option.label
    }
}

@Composable
private fun captionSizeLabel(size: CaptionSize): String {
    return when (size) {
        CaptionSize.SMALL -> stringResource(R.string.caption_size_small)
        CaptionSize.MEDIUM -> stringResource(R.string.caption_size_medium)
        CaptionSize.LARGE -> stringResource(R.string.caption_size_large)
    }
}
