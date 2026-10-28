package com.tk.quicksearch.settings.appearanceSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.core.SearchBarStyle
import com.tk.quicksearch.search.searchScreen.components.backgroundColor
import com.tk.quicksearch.search.searchScreen.components.borderColor
import com.tk.quicksearch.search.searchScreen.components.borderWidth
import com.tk.quicksearch.search.searchScreen.components.cornerRadius
import com.tk.quicksearch.settings.shared.SettingsCard
import com.tk.quicksearch.shared.ui.icons.MaterialSymbols
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import com.tk.quicksearch.shared.util.hapticToggle
import com.tk.quicksearch.widgets.widgetConfigScreen.components.WidgetColorPickerDialog
import java.util.Locale
import kotlin.math.roundToInt

private const val BorderWidthStepDp = 0.5f
private const val BorderWidthSteps =
    ((SearchBarStyle.MAX_BORDER_WIDTH_DP - SearchBarStyle.MIN_BORDER_WIDTH_DP) / BorderWidthStepDp).toInt() - 1
private const val TransparencySteps = 9
private const val CornerRadiusStepDp = 2f
private const val CornerRadiusSteps =
    ((SearchBarStyle.MAX_CORNER_RADIUS_DP - SearchBarStyle.MIN_CORNER_RADIUS_DP) / CornerRadiusStepDp).toInt() - 1
private val SwatchSize = 16.dp
private val SliderValueWidth = 64.dp
private val PreviewHeight = 56.dp

private enum class SearchBarColorTarget { BORDER, BACKGROUND }

@Composable
fun SearchBarStyleCard(
    searchBarStyle: SearchBarStyle,
    onSetSearchBarStyle: (SearchBarStyle) -> Unit,
    showWallpaperBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    // Sliders drive the preview locally and persist once the drag settles.
    var borderWidthDp by remember(searchBarStyle.borderWidthDp) {
        mutableFloatStateOf(searchBarStyle.borderWidthDp)
    }
    var transparency by remember(searchBarStyle.backgroundTransparency) {
        mutableFloatStateOf(searchBarStyle.backgroundTransparency)
    }
    var cornerRadiusDp by remember(searchBarStyle.cornerRadiusDp) {
        mutableFloatStateOf(searchBarStyle.cornerRadiusDp)
    }
    var colorPickerTarget by rememberSaveable { mutableStateOf<SearchBarColorTarget?>(null) }
    val previewStyle =
        searchBarStyle.copy(
            borderWidthDp = borderWidthDp,
            backgroundTransparency = transparency,
            cornerRadiusDp = cornerRadiusDp,
        )

    SettingsCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = DesignTokens.CardHorizontalPadding,
                        vertical = DesignTokens.SpacingLarge,
                    ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
        ) {
            Text(
                text = stringResource(R.string.settings_search_bar_style_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = DesignTokens.SectionTitleBottomPadding),
            )

            SearchBarStylePreview(
                style = previewStyle,
                showWallpaperBackground = showWallpaperBackground,
                modifier = Modifier.padding(bottom = DesignTokens.SpacingMedium),
            )

            ColorChoiceRow(
                label = stringResource(R.string.settings_search_bar_background_color_label),
                defaultLabel = stringResource(R.string.settings_app_theme_title),
                customArgb = searchBarStyle.backgroundColorArgb,
                onSelectDefault = {
                    hapticToggle(view)()
                    onSetSearchBarStyle(searchBarStyle.copy(backgroundColorArgb = null))
                },
                onSelectCustom = {
                    hapticToggle(view)()
                    colorPickerTarget = SearchBarColorTarget.BACKGROUND
                },
            )

            ColorChoiceRow(
                label = stringResource(R.string.widget_border_color),
                defaultLabel = stringResource(R.string.settings_search_bar_color_accent),
                customArgb = searchBarStyle.borderColorArgb,
                onSelectDefault = {
                    hapticToggle(view)()
                    onSetSearchBarStyle(searchBarStyle.copy(borderColorArgb = null))
                },
                onSelectCustom = {
                    hapticToggle(view)()
                    colorPickerTarget = SearchBarColorTarget.BORDER
                },
            )

            SliderSetting(
                label = stringResource(R.string.widget_slider_border),
                value = borderWidthDp,
                valueRange = SearchBarStyle.MIN_BORDER_WIDTH_DP..SearchBarStyle.MAX_BORDER_WIDTH_DP,
                steps = BorderWidthSteps,
                valueText = String.format(Locale.getDefault(), "%.1f dp", borderWidthDp),
                onValueChange = { value ->
                    val snapped = (value / BorderWidthStepDp).roundToInt() * BorderWidthStepDp
                    if (snapped != borderWidthDp) {
                        hapticToggle(view)()
                        borderWidthDp = snapped
                    }
                },
                onValueChangeFinished = {
                    onSetSearchBarStyle(searchBarStyle.copy(borderWidthDp = borderWidthDp))
                },
            )

            SliderSetting(
                label = stringResource(R.string.widget_slider_radius),
                value = cornerRadiusDp,
                valueRange = SearchBarStyle.MIN_CORNER_RADIUS_DP..SearchBarStyle.MAX_CORNER_RADIUS_DP,
                steps = CornerRadiusSteps,
                valueText = String.format(Locale.getDefault(), "%.0f dp", cornerRadiusDp),
                onValueChange = { value ->
                    val snapped = (value / CornerRadiusStepDp).roundToInt() * CornerRadiusStepDp
                    if (snapped != cornerRadiusDp) {
                        hapticToggle(view)()
                        cornerRadiusDp = snapped
                    }
                },
                onValueChangeFinished = {
                    onSetSearchBarStyle(searchBarStyle.copy(cornerRadiusDp = cornerRadiusDp))
                },
            )

            SliderSetting(
                label = stringResource(R.string.settings_wallpaper_transparency_label),
                value = transparency,
                valueRange = 0f..1f,
                steps = TransparencySteps,
                valueText = "${(transparency * 100).roundToInt()}%",
                onValueChange = { value ->
                    val snapped = (value * (TransparencySteps + 1)).roundToInt() / (TransparencySteps + 1).toFloat()
                    if (snapped != transparency) {
                        hapticToggle(view)()
                        transparency = snapped
                    }
                },
                onValueChangeFinished = {
                    onSetSearchBarStyle(searchBarStyle.copy(backgroundTransparency = transparency))
                },
            )

            TextButton(
                onClick = { onSetSearchBarStyle(SearchBarStyle()) },
                enabled = !previewStyle.isDefault,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_launcher_icon_reset_default))
            }
        }
    }

    colorPickerTarget?.let { target ->
        val currentArgb =
            when (target) {
                SearchBarColorTarget.BORDER -> searchBarStyle.borderColorArgb
                SearchBarColorTarget.BACKGROUND -> searchBarStyle.backgroundColorArgb
            }
        val fallbackColor =
            when (target) {
                SearchBarColorTarget.BORDER -> AppColors.Accent
                SearchBarColorTarget.BACKGROUND ->
                    AppColors.getSearchBarBackground(showWallpaperBackground).copy(alpha = 1f)
            }
        WidgetColorPickerDialog(
            initialColor = currentArgb?.let { Color(it) } ?: fallbackColor,
            onDismiss = { colorPickerTarget = null },
            onConfirm = { color ->
                val argb = color.toArgb()
                onSetSearchBarStyle(
                    when (target) {
                        SearchBarColorTarget.BORDER -> searchBarStyle.copy(borderColorArgb = argb)
                        SearchBarColorTarget.BACKGROUND -> searchBarStyle.copy(backgroundColorArgb = argb)
                    },
                )
                colorPickerTarget = null
            },
            title =
                stringResource(
                    when (target) {
                        SearchBarColorTarget.BORDER -> R.string.widget_border_color
                        SearchBarColorTarget.BACKGROUND -> R.string.settings_search_bar_background_color_label
                    },
                ),
        )
    }
}

@Composable
private fun SearchBarStylePreview(
    style: SearchBarStyle,
    showWallpaperBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(style.cornerRadius())
    val iconAndTextColor = AppColors.getSearchBarTextAndIconColor(showWallpaperBackground)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .then(
                    if (style.borderWidthDp > 0f) {
                        Modifier.border(style.borderWidth(), style.borderColor(AppColors.Accent), shape)
                    } else {
                        Modifier
                    },
                ).clip(shape)
                .background(style.backgroundColor(AppColors.getSearchBarBackground(showWallpaperBackground))),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DesignTokens.SpacingLarge),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MaterialSymbols.Search,
                contentDescription = null,
                tint = AppColors.getSearchBarSecondaryIconTint(showWallpaperBackground),
            )
            Text(
                text = stringResource(R.string.common_search),
                style = MaterialTheme.typography.titleMedium,
                color = iconAndTextColor.copy(alpha = DesignTokens.SearchFieldPlaceholderAlpha),
            )
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // No spacing: the value is centered in the gap between the slider end and the card edge.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(SliderValueWidth),
            )
        }
    }
}

@Composable
private fun ColorChoiceRow(
    label: String,
    defaultLabel: String,
    customArgb: Int?,
    onSelectDefault: () -> Unit,
    onSelectCustom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingXSmall)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorChoiceChip(
                label = defaultLabel,
                selected = customArgb == null,
                onClick = onSelectDefault,
                modifier = Modifier.weight(1f),
            )
            ColorChoiceChip(
                label = stringResource(R.string.common_custom),
                selected = customArgb != null,
                onClick = onSelectCustom,
                swatchColor = customArgb?.let { Color(it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColorChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swatchColor: Color? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        leadingIcon =
            swatchColor?.let { color ->
                {
                    Box(
                        modifier =
                            Modifier
                                .size(SwatchSize)
                                .clip(CircleShape)
                                .background(color)
                                .border(DesignTokens.BorderWidth, AppColors.SettingsDivider, CircleShape),
                    )
                }
            },
        shape = DesignTokens.ShapeFull,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}
