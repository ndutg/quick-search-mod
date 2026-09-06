package com.tk.quicksearch.search.apps.speedBump

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tk.quicksearch.R
import com.tk.quicksearch.search.apps.rememberAppIcon
import com.tk.quicksearch.search.core.AppIconShape
import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import kotlinx.coroutines.delay
import kotlin.math.sin

private const val BreathCycleMillis = 3_400
private const val TickMillis = 16L
private val IconSize = 88.dp
private val RingSize = 168.dp

/** How far the gradient edge is pulled toward the theme's foreground colour. */
private const val VignetteStrength = 0.10f

/**
 * Calming interstitial shown before a bumped app opens.
 *
 * The ring fills over [SpeedBump.DELAY_MILLIS] while the icon breathes; when it completes,
 * [onOpen] launches the app. "Don't Open" (and system back) abandon the launch via [onCancel].
 */
@Composable
fun SpeedBumpOverlay(
    appInfo: AppInfo,
    iconPackPackage: String?,
    appIconShape: AppIconShape,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    val currentOnOpen by rememberUpdatedState(onOpen)
    // The host drops the pending launch on configuration change, so this only needs to
    // survive recomposition.
    val startedAtMillis by remember(appInfo.packageName) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(appInfo.packageName) {
        while (true) {
            val elapsed = System.currentTimeMillis() - startedAtMillis
            progress = (elapsed.toFloat() / SpeedBump.DELAY_MILLIS).coerceIn(0f, 1f)
            if (progress >= 1f) break
            delay(TickMillis)
        }
        currentOnOpen()
    }

    val breath = rememberInfiniteTransition(label = "speedBumpBreath")
    val breathPhase by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = BreathCycleMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "speedBumpBreathPhase",
    )
    // Sine keeps the inhale and exhale symmetric, with no visible seam when the cycle wraps.
    val breathAmount = (sin(breathPhase * 2f * Math.PI.toFloat()) + 1f) / 2f

    val iconResult =
        rememberAppIcon(
            packageName = appInfo.packageName,
            iconPackPackage = iconPackPackage,
            userHandleId = appInfo.userHandleId,
            forceCircularMask = appIconShape == AppIconShape.CIRCLE,
        )

    Dialog(
        onDismissRequest = onCancel,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Opaque and derived from the theme's surface, so the vignette follows the
                    // selected light/dark theme instead of always fading to black.
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    lerp(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.onSurface,
                                        VignetteStrength,
                                    ),
                                ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingXXLarge),
                modifier = Modifier.padding(DesignTokens.SpacingHuge),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    BreathingRing(
                        progress = progress,
                        breathAmount = breathAmount,
                        ringColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                    iconResult.bitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = appInfo.appName,
                            modifier =
                                Modifier
                                    .size(IconSize)
                                    .scale(0.94f + 0.06f * breathAmount)
                                    .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
                ) {
                    Text(
                        text = stringResource(R.string.speed_bump_overlay_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.speed_bump_overlay_prompt, appInfo.appName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // Small but prominent: a filled pill rather than a bare text button, so the
                // way out reads as a real action without competing with the breathing icon.
                Button(
                    onClick = onCancel,
                    shape = DesignTokens.ShapeFull,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    contentPadding =
                        PaddingValues(
                            horizontal = DesignTokens.SpacingXXLarge,
                            vertical = DesignTokens.SpacingMedium,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.speed_bump_dont_open),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BreathingRing(
    progress: Float,
    breathAmount: Float,
    ringColor: Color,
    trackColor: Color,
) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(RingSize).scale(0.9f + 0.1f * breathAmount),
    ) {
        val stroke = Stroke(width = 4.dp.toPx())
        val inset = stroke.width / 2f
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = ringColor.copy(alpha = 0.35f + 0.35f * breathAmount),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
    }
}
