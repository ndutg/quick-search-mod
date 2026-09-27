package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tk.quicksearch.R
import com.tk.quicksearch.shared.ui.theme.DesignTokens

internal val GlanceDismissButtonSize = 28.dp

/** A counter that bumps every time the screen resumes, for glances that re-read their source then. */
@Composable
internal fun rememberResumeRefreshKey(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return refreshKey
}

/** Colors for the trailing value pill of a [GlanceStatusRow]. */
internal class GlancePillColors(
    val container: Color,
    val content: Color,
)

@Composable
internal fun glanceNeutralPillColors() =
    GlancePillColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    )

/**
 * The common At a Glance row layout, matching [LowBatteryRow]: a 24dp icon, a title with an
 * optional subtitle and extra content below it, an optional value pill, and an optional [trailing]
 * action or dismiss button outside the row's tap target. [pillWidthText] is the widest value the
 * pill can show; the pill always reserves its width, so a changing value doesn't shift the row.
 */
@Composable
internal fun GlanceStatusRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    pillText: String? = null,
    pillColors: GlancePillColors = glanceNeutralPillColors(),
    pillWidthText: String? = null,
    onDismiss: (() -> Unit)? = null,
    belowText: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(
                start = 7.dp,
                end = if (onDismiss == null) 7.dp else 0.dp,
                top = DesignTokens.SpacingMedium,
                bottom = DesignTokens.SpacingMedium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                belowText?.invoke()
            }
            if (pillText != null) {
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(pillColors.container)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pillWidthText != null) {
                        Text(
                            text = pillWidthText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.alpha(0f).clearAndSetSemantics {},
                        )
                    }
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = pillColors.content,
                        maxLines = 1,
                    )
                }
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = DesignTokens.SpacingSmall)) { trailing() }
        }
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(GlanceDismissButtonSize),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
