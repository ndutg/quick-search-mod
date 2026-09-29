package com.tk.quicksearch.settings.appearanceSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.core.BackgroundSource
import com.tk.quicksearch.search.core.HomeTextColor
import com.tk.quicksearch.shared.ui.theme.LocalAppIsDarkTheme
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.util.WallpaperUtils

@Composable
fun HomeTextColorOptions(
        selectedColor: HomeTextColor?,
        onColorSelected: (HomeTextColor) -> Unit,
        backgroundSource: BackgroundSource,
        customImageUri: String?,
        modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val defaultColor = if (LocalAppIsDarkTheme.current) HomeTextColor.WHITE else HomeTextColor.BLACK
    val wallpaperDefaultColor by produceState(
            initialValue = defaultColor,
            backgroundSource,
            customImageUri,
    ) {
        value =
                WallpaperUtils.getBackgroundAppearance(context, backgroundSource, customImageUri)
                        ?.isDark
                        ?.let { isDark -> if (isDark) HomeTextColor.WHITE else HomeTextColor.BLACK }
                        ?: defaultColor
    }
    val effectiveSelectedColor = selectedColor ?: wallpaperDefaultColor
    Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                        text = stringResource(R.string.settings_home_text_color_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                        text = stringResource(R.string.settings_home_text_color_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    HomeTextColorChip(
                            label = stringResource(R.string.widget_text_icon_color_white),
                            color = HomeTextColor.WHITE,
                            selected = effectiveSelectedColor == HomeTextColor.WHITE,
                            onClick = onColorSelected,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    HomeTextColorChip(
                            label = stringResource(R.string.widget_text_icon_color_black),
                            color = HomeTextColor.BLACK,
                            selected = effectiveSelectedColor == HomeTextColor.BLACK,
                            onClick = onColorSelected,
                    )
                }
            }
    }
}

@Composable
private fun HomeTextColorChip(
        label: String,
        color: HomeTextColor,
        selected: Boolean,
        onClick: (HomeTextColor) -> Unit,
        modifier: Modifier = Modifier,
) {
    val dotBorder = MaterialTheme.colorScheme.outline
    AssistChip(
            onClick = {
                if (!selected) onClick(color)
            },
            label = {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                            modifier =
                                    Modifier.size(14.dp)
                                            .clip(CircleShape)
                                            .background(
                                                    if (color == HomeTextColor.WHITE) Color.White else Color.Black,
                                            )
                                            .border(1.dp, dotBorder, CircleShape),
                    )
                    Text(
                            text = label,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            shape = RoundedCornerShape(999.dp),
            border =
                    androidx.compose.foundation.BorderStroke(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else AppColors.SettingsDivider,
                    ),
            colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.Transparent,
                    labelColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            ),
            modifier = modifier.fillMaxWidth(),
    )
}
