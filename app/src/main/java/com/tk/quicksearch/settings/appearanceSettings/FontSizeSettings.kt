package com.tk.quicksearch.settings.appearanceSettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.core.BackgroundSource
import com.tk.quicksearch.search.core.HomeTextColor
import com.tk.quicksearch.settings.shared.SettingsCard
import com.tk.quicksearch.settings.shared.SettingsToggleRow
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.util.hapticToggle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FontSizeCard(
        fontScaleMultiplier: Float,
        onFontScaleMultiplierChange: (Float) -> Unit,
        useSystemFont: Boolean,
        onUseSystemFontChange: (Boolean) -> Unit,
        homeTextColorOverride: HomeTextColor?,
        onHomeTextColorChange: (HomeTextColor) -> Unit,
        backgroundSource: BackgroundSource,
        customImageUri: String?,
        modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    SettingsCard(modifier = modifier.fillMaxWidth()) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsToggleRow(
                    title = stringResource(R.string.settings_use_system_font_title),
                    subtitle = stringResource(R.string.settings_use_system_font_desc),
                    checked = useSystemFont,
                    onCheckedChange = onUseSystemFontChange,
                    leadingIcon = Icons.Rounded.TextFields,
                    horizontalPadding = 4.dp,
                    showDivider = false,
            )

            HorizontalDivider(
                    color = AppColors.SettingsDivider,
                    modifier = Modifier.padding(start = 4.dp, top = 1.dp, end = 4.dp, bottom = 4.dp),
            )

            HomeTextColorOptions(
                    selectedColor = homeTextColorOverride,
                    onColorSelected = onHomeTextColorChange,
                    backgroundSource = backgroundSource,
                    customImageUri = customImageUri,
            )

            HorizontalDivider(
                    color = AppColors.SettingsDivider,
                    modifier = Modifier.padding(start = 4.dp, top = 1.dp, end = 4.dp, bottom = 4.dp),
            )

            FontSizeSlider(
                    fontScaleMultiplier = fontScaleMultiplier,
                    onFontScaleMultiplierChange = onFontScaleMultiplierChange,
            )
        }
    }
}

internal val FONT_SIZE_STEPS =
        listOf(
                FontSizeStep(0.90f, R.string.settings_font_size_extra_small, R.string.settings_font_size_tick_xs),
                FontSizeStep(0.95f, R.string.settings_font_size_small, R.string.settings_font_size_tick_s),
                FontSizeStep(1.00f, R.string.settings_font_size_medium, R.string.settings_font_size_tick_m),
                FontSizeStep(1.05f, R.string.settings_font_size_big, R.string.settings_font_size_tick_l),
                FontSizeStep(1.10f, R.string.settings_font_size_extra_big, R.string.settings_font_size_tick_xl),
        )

internal data class FontSizeStep(val scale: Float, val labelRes: Int, val tickRes: Int)

@Composable
private fun FontSizeSlider(
        fontScaleMultiplier: Float,
        onFontScaleMultiplierChange: (Float) -> Unit,
) {
    val view = LocalView.current
    val committedIndex =
            FONT_SIZE_STEPS.indices.minByOrNull { abs(FONT_SIZE_STEPS[it].scale - fontScaleMultiplier) } ?: 2
    var sliderValue by remember(committedIndex) { mutableFloatStateOf(committedIndex.toFloat()) }
    val activeIndex = sliderValue.roundToInt().coerceIn(0, FONT_SIZE_STEPS.lastIndex)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                    text = stringResource(R.string.settings_font_size_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                    text = stringResource(FONT_SIZE_STEPS[activeIndex].labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                    text = "A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        sliderValue = value
                        val index = value.roundToInt().coerceIn(0, FONT_SIZE_STEPS.lastIndex)
                        if (index != committedIndex) {
                            hapticToggle(view)()
                            onFontScaleMultiplierChange(FONT_SIZE_STEPS[index].scale)
                        }
                    },
                    valueRange = 0f..FONT_SIZE_STEPS.lastIndex.toFloat(),
                    steps = FONT_SIZE_STEPS.size - 2,
                    modifier = Modifier.weight(1f),
            )
            Text(
                    text = "A",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FONT_SIZE_STEPS.forEachIndexed { index, step ->
                Text(
                        text = stringResource(step.tickRes),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                                if (index == activeIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                )
            }
        }
    }
}
