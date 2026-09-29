package com.tk.quicksearch.search.appSettings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import com.tk.quicksearch.shared.util.hapticToggle

/** Selected/unselected colors shared by every chip on app-setting result rows (matches the AI model chips). */
internal object AppSettingChipStyle {
    @Composable
    fun containerColor(selected: Boolean): Color =
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent

    @Composable
    fun border(selected: Boolean): BorderStroke =
        BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            },
        )

    @Composable
    fun labelColor(selected: Boolean): Color =
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Single-choice chip for app-setting rows. [showCheck] adds a leading checkmark when selected; turn
 * it off where the chip sits in a fixed trailing slot and must not change width. [compact] draws a
 * slimmer chip that centers its content, so several can share one row via weights.
 */
@Composable
internal fun AppSettingChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = true,
    compact: Boolean = false,
) {
    val view = LocalView.current
    val onChipClick = {
        hapticToggle(view)()
        onClick()
    }
    if (compact) {
        Row(
            modifier =
                modifier
                    .clip(DesignTokens.ShapeFull)
                    .background(AppSettingChipStyle.containerColor(selected))
                    .border(AppSettingChipStyle.border(selected), DesignTokens.ShapeFull)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onChipClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            if (selected && showCheck) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AppSettingChipStyle.labelColor(selected),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    AssistChip(
        onClick = onChipClick,
        label = { Text(label) },
        modifier = modifier,
        shape = DesignTokens.ShapeFull,
        leadingIcon =
            if (selected && showCheck) {
                {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                }
            } else {
                null
            },
        border = AppSettingChipStyle.border(selected),
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = AppSettingChipStyle.containerColor(selected),
                labelColor = AppSettingChipStyle.labelColor(selected),
            ),
    )
}
