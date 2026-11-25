package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.SplashCreamText
import com.example.ui.theme.SplashEmblemSurface
import com.example.ui.theme.SplashForestDeep
import com.example.ui.theme.SplashForestMid
import com.example.ui.theme.SplashForestTop
import com.example.ui.theme.SplashMintGlow
import com.example.ui.theme.SplashMintSubtitle
import com.example.ui.theme.SplashTerracottaPulse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded cold-start continuity splash overlay seamlessly continuing from `bg_splash_screen.xml`.
 * Features ambient radial glow, triple-ring botanical freshness pulse + counter-rotating aura arc,
 * spring-bounced emblem, staggered typography reveal, freshness progress sweep, and touch + TV D-pad dismiss.
 */
@Composable
fun BrandedSplashOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconScale = remember { Animatable(0.76f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(24f) }
    val badgeAlpha = remember { Animatable(0f) }
    val progressSweep = remember { Animatable(0.14f) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse_rings")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )
    val glowBreath by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_breath"
    )
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        launch {
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            progressSweep.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }
        delay(90L)
        launch {
            titleAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(0f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
        delay(120L)
        launch {
            badgeAlpha.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SplashForestTop,
                        SplashForestMid,
                        SplashForestDeep
                    )
                )
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.Spacebar)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
            .testTag("splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(224.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val ambientRadius = size.minDimension * 0.45f * glowBreath
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SplashMintGlow.copy(alpha = 0.24f),
                                SplashMintGlow.copy(alpha = 0.07f),
                                Color.Transparent
                            ),
                            radius = ambientRadius
                        ),
                        radius = ambientRadius
                    )

                    val baseRadius = size.minDimension * 0.31f
                    val maxRadius = size.minDimension * 0.49f

                    val ring1Radius = baseRadius + (maxRadius - baseRadius) * pulseProgress
                    val ring1Alpha = (1f - pulseProgress) * 0.44f

                    val ring2Progress = (pulseProgress + 0.33f) % 1f
                    val ring2Radius = baseRadius + (maxRadius - baseRadius) * ring2Progress
                    val ring2Alpha = (1f - ring2Progress) * 0.36f

                    val ring3Progress = (pulseProgress + 0.66f) % 1f
                    val ring3Radius = baseRadius + (maxRadius - baseRadius) * ring3Progress
                    val ring3Alpha = (1f - ring3Progress) * 0.28f

                    drawCircle(
                        color = SplashMintGlow.copy(alpha = ring1Alpha),
                        radius = ring1Radius,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = SplashTerracottaPulse.copy(alpha = ring2Alpha),
                        radius = ring2Radius,
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                    drawCircle(
                        color = SplashMintSubtitle.copy(alpha = ring3Alpha),
                        radius = ring3Radius,
                        style = Stroke(width = 1.6.dp.toPx())
                    )

                    // Orbiting botanical freshness arc
                    val arcPadding = size.minDimension * 0.14f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                SplashMintGlow.copy(alpha = 0.55f),
                                SplashTerracottaPulse.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        ),
                        startAngle = orbitAngle,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(arcPadding, arcPadding),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - arcPadding * 2,
                            size.height - arcPadding * 2
                        ),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(36.dp),
                    shadowElevation = 20.dp,
                    color = SplashEmblemSurface,
                    border = BorderStroke(1.8.dp, SplashMintGlow.copy(alpha = 0.42f)),
                    modifier = Modifier
                        .size(136.dp)
                        .scale(iconScale.value)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_pantrypulse_icon),
                        contentDescription = stringResource(id = R.string.cd_pantrypulse_emblem),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(36.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .graphicsLayer { translationY = titleOffsetY.value }
            ) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = SplashCreamText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(id = R.string.splash_tagline),
                    style = MaterialTheme.typography.titleSmall,
                    color = SplashMintSubtitle,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))

                // Subtle botanical freshness progress bar
                Box(
                    modifier = Modifier
                        .width(136.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SplashCreamText.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressSweep.value)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(SplashMintGlow, SplashTerracottaPulse)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = SplashCreamText.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, SplashMintGlow.copy(alpha = 0.25f)),
                    modifier = Modifier.alpha(badgeAlpha.value)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = SplashMintGlow,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.splash_tap_to_continue),
                            style = MaterialTheme.typography.labelSmall,
                            color = SplashMintSubtitle,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
