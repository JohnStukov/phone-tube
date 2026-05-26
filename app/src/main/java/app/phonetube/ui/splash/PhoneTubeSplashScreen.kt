package app.phonetube.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phonetube.R
import app.phonetube.ui.theme.PhoneTubePrimary
import app.phonetube.ui.components.AppVersionLabel
import app.phonetube.ui.theme.PhoneTubePrimaryDark
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 2_200L

@Composable
fun PhoneTubeSplashScreen(onFinished: () -> Unit) {
    var animationStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animationStarted = true
        delay(SPLASH_DURATION_MS)
        onFinished()
    }

    val logoScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "logoScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        PhoneTubePrimaryDark.copy(alpha = 0.35f),
                        Color(0xFF0F0F0F),
                        Color(0xFF050505)
                    ),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(pulse * 1.4f)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PhoneTubePrimary,
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(contentAlpha)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_phonetube_mark),
                contentDescription = stringResource(R.string.brand_phonetube),
                modifier = Modifier
                    .size(104.dp)
                    .scale(logoScale * pulse),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.brand_phonetube),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppVersionLabel(color = Color.White.copy(alpha = 0.72f))
        }
    }
}
