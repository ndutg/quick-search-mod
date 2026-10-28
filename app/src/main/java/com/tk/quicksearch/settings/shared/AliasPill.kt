package com.tk.quicksearch.settings.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.shared.ui.icons.MaterialSymbols
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens

private val ALIAS_ICON_SIZE = 14.dp
private val ALIAS_CLEAR_ICON_SIZE = 16.dp

@Composable
fun AliasPill(
    text: AnnotatedString,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    textColor: Color = Color.Unspecified,
    showBackground: Boolean = true,
    leadingIcon: ImageVector? = null,
    iconTextSpacing: Dp = DesignTokens.SpacingXSmall,
    onClearClick: (() -> Unit)? = null,
) {
    val resolvedTextColor =
        if (textColor == Color.Unspecified) {
            AppColors.DialogText
        } else {
            textColor
        }
    val shouldUseSurfaceClick = onClick != null && onClearClick == null
    Surface(
        modifier =
            modifier
                .then(
                    if (shouldUseSurfaceClick) {
                        Modifier
                            .clip(DesignTokens.ShapeFull)
                            .clickable(onClick = requireNotNull(onClick))
                    } else {
                        Modifier
                    },
                ),
        shape = DesignTokens.ShapeFull,
        border =
            if (showBackground) {
                BorderStroke(1.dp, AppColors.SettingsDivider)
            } else {
                null
            },
        color = Color.Transparent,
        tonalElevation = DesignTokens.ElevationLevel0,
    ) {
        val contentPadding =
            if (showBackground) {
                PaddingValues(
                    horizontal = DesignTokens.SpacingSmall,
                    vertical = DesignTokens.SpacingXXSmall,
                )
            } else {
                PaddingValues(
                    start = 0.dp,
                    top = DesignTokens.SpacingXXSmall,
                    end = DesignTokens.SpacingSmall,
                    bottom = DesignTokens.SpacingXXSmall,
                )
            }

        Row(
            modifier =
                Modifier
                    .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingXSmall),
        ) {
            Row(
                modifier =
                    if (onClick != null && !shouldUseSurfaceClick) {
                        Modifier
                            .clip(DesignTokens.ShapeFull)
                            .clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(iconTextSpacing),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = resolvedTextColor,
                        modifier = Modifier.size(ALIAS_ICON_SIZE),
                    )
                }

                Text(
                    text = text,
                    style = textStyle,
                    color = resolvedTextColor,
                )
            }

            if (onClearClick != null) {
                Icon(
                    imageVector = MaterialSymbols.Close,
                    contentDescription = null,
                    tint = resolvedTextColor,
                    modifier =
                        Modifier
                            .padding(start = DesignTokens.SpacingSmall)
                            .size(ALIAS_CLEAR_ICON_SIZE)
                            .clip(DesignTokens.ShapeFull)
                            .clickable(onClick = onClearClick),
                )
            }
        }
    }
}
