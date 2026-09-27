package com.tk.quicksearch.widgets.noteWidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.widgets.utils.TextIconColorOverride
import com.tk.quicksearch.widgets.utils.WidgetConfigConstants
import com.tk.quicksearch.widgets.utils.WidgetPreferences
import com.tk.quicksearch.widgets.utils.WidgetTheme
import com.tk.quicksearch.widgets.widgetConfigScreen.components.WidgetColorPickerDialog

private const val SEGMENT_COUNT = 4

/**
 * Background choice for the Note widget: follow the system theme, white, black, or a custom color
 * from the picker. A custom color is stored in [WidgetPreferences.backgroundColor]; null means one
 * of the themes. Picking a theme makes the text automatic again so it reads on the new background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteWidgetBackgroundSection(
    state: WidgetPreferences,
    onStateChange: (WidgetPreferences) -> Unit,
) {
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    val customColor = state.backgroundColor?.let(::Color)
    val selectTheme = { theme: WidgetTheme ->
        onStateChange(
            state.copy(theme = theme, backgroundColor = null, textIconColorOverride = TextIconColorOverride.THEME),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(WidgetConfigConstants.COLOR_SECTION_SPACING)) {
        Text(
            text = stringResource(R.string.settings_app_theme_title),
            style = MaterialTheme.typography.titleSmall,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = customColor == null && state.theme == WidgetTheme.SYSTEM,
                onClick = { selectTheme(WidgetTheme.SYSTEM) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = SEGMENT_COUNT),
                icon = {},
            ) { Text(stringResource(R.string.common_theme_system), maxLines = 1) }
            SegmentedButton(
                selected = customColor == null && state.theme == WidgetTheme.LIGHT,
                onClick = { selectTheme(WidgetTheme.LIGHT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = SEGMENT_COUNT),
                icon = {},
            ) { Text(stringResource(R.string.common_theme_light), maxLines = 1) }
            SegmentedButton(
                selected = customColor == null && state.theme == WidgetTheme.DARK,
                onClick = { selectTheme(WidgetTheme.DARK) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = SEGMENT_COUNT),
                icon = {},
            ) { Text(stringResource(R.string.common_theme_dark), maxLines = 1) }
            // An icon rather than the "Custom" label, which does not fit a quarter of the row in
            // every language.
            SegmentedButton(
                selected = customColor != null,
                onClick = { showColorPicker = true },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = SEGMENT_COUNT),
                icon = {},
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = stringResource(R.string.common_custom),
                        modifier = Modifier.size(18.dp),
                    )
                    if (customColor != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(customColor)
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = CircleShape,
                                    ),
                        )
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        WidgetColorPickerDialog(
            initialColor = customColor ?: MaterialTheme.colorScheme.primary,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                onStateChange(state.copy(backgroundColor = color.toArgb()))
                showColorPicker = false
            },
        )
    }
}

/**
 * Text color for the Note widget: white or black. Until one is picked the text is automatic, and
 * the option matching the color it currently resolves to is shown as selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteWidgetTextColorSection(
    state: WidgetPreferences,
    onStateChange: (WidgetPreferences) -> Unit,
) {
    val isWhite =
        NoteWidgetColors.forWidget(state, isSystemDark = isSystemInDarkTheme()).title == AppColors.WidgetTextLight

    Column(verticalArrangement = Arrangement.spacedBy(WidgetConfigConstants.COLOR_SECTION_SPACING)) {
        Text(
            text = stringResource(R.string.widget_text_color),
            style = MaterialTheme.typography.titleSmall,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isWhite,
                onClick = { onStateChange(state.copy(textIconColorOverride = TextIconColorOverride.WHITE)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
            ) { Text(stringResource(R.string.widget_text_icon_color_white)) }
            SegmentedButton(
                selected = !isWhite,
                onClick = { onStateChange(state.copy(textIconColorOverride = TextIconColorOverride.BLACK)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
            ) { Text(stringResource(R.string.widget_text_icon_color_black)) }
        }
    }
}
