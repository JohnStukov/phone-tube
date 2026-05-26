package app.phonetube.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.phonetube.R
import app.phonetube.util.AppVersion

@Composable
fun AppVersionLabel(
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = stringResource(R.string.app_version, AppVersion.name),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = textAlign,
        modifier = modifier
    )
}
