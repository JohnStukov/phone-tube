package app.phonetube.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.phonetube.R
import app.phonetube.core.playback.SponsorBlockPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SponsorBlockPrefs(context) }
    var sbEnabled by remember { mutableStateOf(prefs.isEnabled) }
    var altServer by remember { mutableStateOf(prefs.useAltServer) }
    val categories = remember { SponsorBlockPrefs.ALL_CATEGORIES }
    var categoryStates by remember {
        mutableStateOf(categories.associateWith { prefs.isCategoryEnabled(it) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_sponsorblock),
                style = MaterialTheme.typography.titleMedium
            )
            SettingSwitch(
                title = stringResource(R.string.settings_sb_enabled),
                checked = sbEnabled,
                onCheckedChange = {
                    sbEnabled = it
                    prefs.isEnabled = it
                }
            )
            SettingSwitch(
                title = stringResource(R.string.settings_sb_alt_server),
                checked = altServer,
                onCheckedChange = {
                    altServer = it
                    prefs.useAltServer = it
                }
            )

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                text = stringResource(R.string.settings_sb_categories),
                style = MaterialTheme.typography.titleSmall
            )

            categories.forEach { category ->
                val label = categoryLabel(category)
                val checked = categoryStates[category] == true
                SettingSwitch(
                    title = label,
                    checked = checked,
                    onCheckedChange = { enabled ->
                        prefs.setCategoryEnabled(category, enabled)
                        categoryStates = categoryStates.toMutableMap().apply { put(category, enabled) }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun categoryLabel(category: String): String {
    return when (category) {
        "sponsor" -> stringResource(R.string.sb_category_sponsor)
        "intro" -> stringResource(R.string.sb_category_intro)
        "outro" -> stringResource(R.string.sb_category_outro)
        "selfpromo" -> stringResource(R.string.sb_category_selfpromo)
        "interaction" -> stringResource(R.string.sb_category_interaction)
        "music_offtopic" -> stringResource(R.string.sb_category_music_offtopic)
        "preview" -> stringResource(R.string.sb_category_preview)
        "poi_highlight" -> stringResource(R.string.sb_category_highlight)
        "filler" -> stringResource(R.string.sb_category_filler)
        else -> category
    }
}
